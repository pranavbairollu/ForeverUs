package com.example.foreverus;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import java.util.List;

public class BucketViewModel extends AndroidViewModel {

    private final BucketRepository repository;
    private final MutableLiveData<String> relationshipIdLiveData = new MutableLiveData<>();
    private final LiveData<List<BucketItem>> bucketItems;

    public BucketViewModel(@NonNull Application application) {
        super(application);
        repository = new BucketRepository(application);
        bucketItems = Transformations.switchMap(relationshipIdLiveData, id -> repository.getAllBucketItems(id));
    }

    public void loadBucketItems(String relationshipId) {
        if (relationshipId != null && !relationshipId.equals(relationshipIdLiveData.getValue())) {
            relationshipIdLiveData.setValue(relationshipId);
        }
    }

    public LiveData<List<BucketItem>> getBucketItems() {
        return bucketItems;
    }

    public void addBucketItem(String relationshipId, BucketItem item) {
        addBucketItem(relationshipId, item, null);
    }

    public void addBucketItem(String relationshipId, BucketItem item, android.net.Uri imageUri) {
        if (imageUri != null) {
            uploadImageToCloudinary(imageUri, new OnUploadCallback() {
                @Override
                public void onSuccess(String url) {
                    item.setImageUrl(url);
                    repository.addBucketItem(relationshipId, item);
                }

                @Override
                public void onError(String error) {
                    // Start with no image if upload fails, or handle error
                    // For now, proceed without image to avoid blocking
                    repository.addBucketItem(relationshipId, item);
                }
            });
        } else {
            repository.addBucketItem(relationshipId, item);
        }
    }

    public void updateBucketItem(String relationshipId, BucketItem item) {
        updateBucketItem(relationshipId, item, null);
    }

    public void updateBucketItem(String relationshipId, BucketItem item, android.net.Uri newImageUri) {
        if (newImageUri != null) {
            uploadImageToCloudinary(newImageUri, new OnUploadCallback() {
                @Override
                public void onSuccess(String url) {
                    item.setImageUrl(url);
                    repository.updateBucketItem(relationshipId, item);
                }

                @Override
                public void onError(String error) {
                    // Proceed without updating image
                    repository.updateBucketItem(relationshipId, item);
                }
            });
        } else {
            repository.updateBucketItem(relationshipId, item);
        }
    }

    public void updateItemPriorities(String relationshipId, List<BucketItem> items) {
        repository.updateItemPriorities(relationshipId, items);
    }

    public void deleteBucketItem(String relationshipId, BucketItem item) {
        repository.deleteBucketItem(relationshipId, item);
    }

    private void uploadImageToCloudinary(android.net.Uri uri, OnUploadCallback callback) {
        initCloudinary();
        com.cloudinary.android.MediaManager.get().upload(uri)
                .unsigned("foreverus_memories")
                .option("resource_type", "image")
                .callback(new com.cloudinary.android.callback.UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId, java.util.Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        callback.onSuccess(url);
                    }

                    @Override
                    public void onError(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                        callback.onError(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                    }
                })
                .dispatch();
    }

    private void initCloudinary() {
        try {
            com.cloudinary.android.MediaManager.get();
        } catch (IllegalStateException e) {
            java.util.Map<String, Object> config = new java.util.HashMap<>();
            config.put("cloud_name", "dsuwyan5m");
            config.put("secure", true);
            com.cloudinary.android.MediaManager.init(getApplication(), config);
        }
    }

    interface OnUploadCallback {
        void onSuccess(String url);

        void onError(String error);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.cleanup();
    }

    // Milestone Logic
    public void recordMilestone(String relationshipId, BucketItem item) {
        if (!item.isCompleted())
            return;

        Milestone milestone = new Milestone();
        milestone.setId(java.util.UUID.randomUUID().toString());
        milestone.setRelationshipId(relationshipId);
        milestone.setTitle(item.getTitle());
        milestone.setCategory(item.getType());
        milestone.setCompletedDate(new java.util.Date());
        milestone.setCost(item.getEstimatedCost());

        repository.addMilestone(milestone);
    }

    // Presence Logic
    public void setPresence(String relationshipId, boolean isOnline) {
        String userId = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            repository.setPresence(relationshipId, userId, isOnline);
        }
    }

    public LiveData<Boolean> getPartnerPresence(String relationshipId) {
        String userId = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            return repository.getPartnerPresence(relationshipId, userId);
        }
        return new MutableLiveData<>(false);
    }
}
