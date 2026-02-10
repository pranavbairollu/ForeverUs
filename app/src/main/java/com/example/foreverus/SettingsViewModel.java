package com.example.foreverus;

import static com.example.foreverus.FirestoreConstants.*;

import android.app.Application;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SettingsViewModel extends AndroidViewModel {

    private static final String TAG = "SettingsViewModel";
    private final FirebaseFirestore db;
    private final FirebaseStorage storage;
    private final MutableLiveData<String> userAvatarUrl = new MutableLiveData<>();
    private final MutableLiveData<String> partnerAvatarUrl = new MutableLiveData<>();
    private final MutableLiveData<String> daysTogether = new MutableLiveData<>();

    private String currentUserId;

    private ListenerRegistration relationshipListener;
    private ListenerRegistration userListener;
    private ListenerRegistration partnerListener;

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();
    }

    public LiveData<String> getUserAvatarUrl() {
        return userAvatarUrl;
    }

    public LiveData<String> getPartnerAvatarUrl() {
        return partnerAvatarUrl;
    }

    public LiveData<String> getDaysTogether() {
        return daysTogether;
    }

    // Check if we already loaded the nickname to avoid redundant fetches
    private final MutableLiveData<String> partnerNickname = new MutableLiveData<>();

    public LiveData<String> getPartnerNickname() {
        return partnerNickname;
    }

    public void loadProfileData(String relationshipId) {
        if (currentUserId == null || relationshipId == null)
            return;

        // Remove existing listener if any
        if (relationshipListener != null)
            relationshipListener.remove();

        // 1. Load Relationship (for Days Together & Partner ID)
        relationshipListener = db.collection(COLLECTION_RELATIONSHIPS).document(relationshipId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists())
                        return;

                    // Days Together
                    long createdAtMillis = 0;
                    Object createdAtObj = snapshot.get(FIELD_CREATED_AT);

                    if (createdAtObj instanceof com.google.firebase.Timestamp) {
                        createdAtMillis = ((com.google.firebase.Timestamp) createdAtObj).toDate().getTime();
                    } else if (createdAtObj instanceof Number) {
                        createdAtMillis = ((Number) createdAtObj).longValue();
                    }

                    if (createdAtMillis > 0) {
                        long diff = System.currentTimeMillis() - createdAtMillis;
                        long days = TimeUnit.MILLISECONDS.toDays(Math.max(0, diff));
                        if (days == 0) {
                            daysTogether.postValue(getApplication().getString(R.string.together_starting_today));
                        } else {
                            daysTogether.postValue(getApplication().getString(R.string.together_for_days_format, days));
                        }
                    }

                    // Identify Partner
                    List<String> members = (List<String>) snapshot.get(FIELD_MEMBERS);
                    if (members != null) {
                        for (String memberId : members) {
                            if (!memberId.equals(currentUserId)) {
                                loadPartnerProfile(memberId);
                            } else {
                                loadMyProfile(memberId);
                            }
                        }
                    }
                });
    }

    public void loadNickname(String relationshipId) {
        if (relationshipId == null)
            return;
        // Don't fetch if we already have a value (Rotation handling)
        if (partnerNickname.getValue() != null)
            return;

        db.collection(COLLECTION_RELATIONSHIPS).document(relationshipId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && snapshot.contains(FIELD_COUPLE_NICKNAME)) {
                        partnerNickname.postValue(snapshot.getString(FIELD_COUPLE_NICKNAME));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading nickname", e));
    }

    public void updateNickname(String relationshipId, String nickname, androidx.core.util.Consumer<Boolean> onResult) {
        if (relationshipId == null) {
            onResult.accept(false);
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_COUPLE_NICKNAME, nickname);

        db.collection(COLLECTION_RELATIONSHIPS).document(relationshipId)
                .update(data)
                .addOnSuccessListener(aVoid -> {
                    partnerNickname.postValue(nickname); // Update local state immediately
                    onResult.accept(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update nickname", e);
                    onResult.accept(false);
                });
    }

    private void loadMyProfile(String userId) {
        if (userListener != null)
            userListener.remove();
        userListener = db.collection(COLLECTION_USERS).document(userId).addSnapshotListener((snap, e) -> {
            if (snap != null && snap.contains(FIELD_AVATAR_URL)) {
                userAvatarUrl.postValue(snap.getString(FIELD_AVATAR_URL));
            }
        });
    }

    private void loadPartnerProfile(String userId) {
        if (partnerListener != null)
            partnerListener.remove();
        partnerListener = db.collection(COLLECTION_USERS).document(userId).addSnapshotListener((snap, e) -> {
            if (snap != null && snap.contains(FIELD_AVATAR_URL)) {
                partnerAvatarUrl.postValue(snap.getString(FIELD_AVATAR_URL));
            }
        });
    }

    public enum UploadState {
        IDLE,
        UPLOADING,
        SUCCESS,
        ERROR
    }

    private final MutableLiveData<UploadState> uploadState = new MutableLiveData<>(UploadState.IDLE);

    public LiveData<UploadState> getUploadState() {
        return uploadState;
    }

    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void uploadAvatar(Uri imageUri) {
        if (currentUserId == null || imageUri == null)
            return;

        uploadState.setValue(UploadState.UPLOADING);

        // Use Cloudinary MediaManager (initialized in Application class)
        com.cloudinary.android.MediaManager.get().upload(imageUri)
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
                        updateAvatarUrl(url);
                    }

                    @Override
                    public void onError(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                        Log.e(TAG, "Avatar upload failed: " + error.getDescription());
                        errorMessage.postValue("Upload failed: " + error.getDescription());
                        uploadState.postValue(UploadState.ERROR);
                    }

                    @Override
                    public void onReschedule(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                    }
                })
                .dispatch();
    }

    private void updateAvatarUrl(String url) {
        db.collection(COLLECTION_USERS).document(currentUserId)
                .update(FIELD_AVATAR_URL, url)
                .addOnSuccessListener(aVoid -> uploadState.postValue(UploadState.SUCCESS))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update avatar URL", e);
                    errorMessage.postValue("Failed to update profile: " + e.getMessage());
                    uploadState.postValue(UploadState.ERROR);
                });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (relationshipListener != null)
            relationshipListener.remove();
        if (userListener != null)
            userListener.remove();
        if (partnerListener != null)
            partnerListener.remove();
    }
}
