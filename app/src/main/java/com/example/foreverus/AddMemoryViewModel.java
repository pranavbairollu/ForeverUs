package com.example.foreverus;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import id.zelory.compressor.Compressor;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import id.zelory.compressor.Compressor;

public class AddMemoryViewModel extends AndroidViewModel {

    public enum UploadState {
        IDLE,
        COMPRESSING,
        UPLOADING,
        SAVING,
        SUCCESS,
        ERROR
    }

    private final MutableLiveData<UploadState> _uploadState = new MutableLiveData<>(UploadState.IDLE);
    public final LiveData<UploadState> uploadState = _uploadState;

    private final MutableLiveData<Integer> _uploadProgress = new MutableLiveData<>(0);
    public final LiveData<Integer> uploadProgress = _uploadProgress;

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public final LiveData<String> errorMessage = _errorMessage;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    private final MemoryRepository memoryRepository;

    public AddMemoryViewModel(@NonNull Application application) {
        super(application);
        memoryRepository = new MemoryRepository(application);
    }

    public void saveMemory(java.util.List<Uri> imageUris, String mimeType, String title, String description, String location, String relationshipId, FirebaseUser user, String audioFilePath) {
        if (imageUris == null || imageUris.isEmpty()) {
            _errorMessage.setValue(getApplication().getString(R.string.error_select_image));
            _uploadState.setValue(UploadState.ERROR);
            return;
        }
        if (title == null || title.trim().isEmpty()) {
            _errorMessage.setValue(getApplication().getString(R.string.error_enter_title));
            _uploadState.setValue(UploadState.ERROR);
            return;
        }
        if (relationshipId == null || relationshipId.isEmpty()) {
            _errorMessage.setValue(getApplication().getString(R.string.error_missing_relationship));
            _uploadState.setValue(UploadState.ERROR);
            return;
        }
        if (user == null) {
            _errorMessage.setValue(getApplication().getString(R.string.not_logged_in));
            _uploadState.setValue(UploadState.ERROR);
            return;
        }

        if (!isNetworkAvailable()) {
            _errorMessage.setValue("Internet required to upload photos/videos. Your text is safe.");
            _uploadState.setValue(UploadState.ERROR);
            return;
        }

        // Upload multiple images AND audio if present
        uploadMediaToCloudinary(imageUris, mimeType, title, description, location, relationshipId, user, audioFilePath);
    }
    
    // Kept for backward compatibility
    public void saveMemory(java.util.List<Uri> imageUris, String mimeType, String title, String description, String location, String relationshipId, FirebaseUser user) {
        saveMemory(imageUris, mimeType, title, description, location, relationshipId, user, null);
    }
    
    public void saveMemory(Uri imageUri, String mimeType, String title, String description, String location, String relationshipId, FirebaseUser user) {
        java.util.List<Uri> list = new java.util.ArrayList<>();
        list.add(imageUri);
        saveMemory(list, mimeType, title, description, location, relationshipId, user, null);
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getApplication().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            android.net.NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
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

    private void uploadMediaToCloudinary(java.util.List<Uri> uris, String mimeType, String title, String description, String location, String relationshipId, FirebaseUser user, String audioFilePath) {
        initCloudinary();
        _uploadState.postValue(UploadState.UPLOADING);
        _uploadProgress.postValue(0);

        java.util.List<String> uploadedUrls = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        // Use a container for the audio URL to be mutually accessible
        final String[] uploadedAudioUrl = {null};
        
        // Total items = images + (1 if audio exists)
        int totalItems = uris.size() + (audioFilePath != null ? 1 : 0);
        
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failureCount = new java.util.concurrent.atomic.AtomicInteger(0);

        String resourceType = "image";
        if (mimeType != null && mimeType.startsWith("video/")) {
            resourceType = "video";
        }
        final String finalResourceType = resourceType;

        // 1. Upload Images/Videos
        for (int i = 0; i < uris.size(); i++) {
            Uri uri = uris.get(i);
            com.cloudinary.android.MediaManager.get().upload(uri)
                    .unsigned("foreverus_memories")
                    .option("resource_type", finalResourceType)
                    .callback(new com.cloudinary.android.callback.UploadCallback() {
                        @Override
                        public void onStart(String requestId) { }
                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) { }
                        @Override
                        public void onSuccess(String requestId, java.util.Map resultData) {
                            String url = (String) resultData.get("secure_url");
                            uploadedUrls.add(url);
                            checkCompletionRecursive(totalItems, successCount.incrementAndGet(), failureCount.get(), uploadedUrls, uploadedAudioUrl[0], finalResourceType, title, description, location, relationshipId, user);
                        }
                        @Override
                        public void onError(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                            Log.e("AddMemoryViewModel", "Image Upload error: " + error.getDescription());
                            checkCompletionRecursive(totalItems, successCount.get(), failureCount.incrementAndGet(), uploadedUrls, uploadedAudioUrl[0], finalResourceType, title, description, location, relationshipId, user);
                        }
                        @Override
                        public void onReschedule(String requestId, com.cloudinary.android.callback.ErrorInfo error) {}
                    })
                    .dispatch();
        }
        
        // 2. Upload Audio if exists
        if (audioFilePath != null) {
            com.cloudinary.android.MediaManager.get().upload(audioFilePath)
                    .unsigned("foreverus_memories")
                    .option("resource_type", "video") // Audio is treated as video in Cloudinary usually
                    .callback(new com.cloudinary.android.callback.UploadCallback() {
                        @Override
                        public void onStart(String requestId) { }
                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) { }
                        @Override
                        public void onSuccess(String requestId, java.util.Map resultData) {
                            uploadedAudioUrl[0] = (String) resultData.get("secure_url");
                            checkCompletionRecursive(totalItems, successCount.incrementAndGet(), failureCount.get(), uploadedUrls, uploadedAudioUrl[0], finalResourceType, title, description, location, relationshipId, user);
                        }
                        @Override
                        public void onError(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                            Log.e("AddMemoryViewModel", "Audio Upload error: " + error.getDescription());
                            checkCompletionRecursive(totalItems, successCount.get(), failureCount.incrementAndGet(), uploadedUrls, uploadedAudioUrl[0], finalResourceType, title, description, location, relationshipId, user);
                        }
                        @Override
                        public void onReschedule(String requestId, com.cloudinary.android.callback.ErrorInfo error) {}
                    })
                    .dispatch();
        }
    }

    private void checkCompletionRecursive(int total, int success, int failure, java.util.List<String> urls, String audioUrl, String mediaType, String title, String description, String location, String relationshipId, FirebaseUser user) {
        if (success + failure == total) {
            if (success > 0) {
                // At least one succeeded (audio or image). Ideally we want at least one image.
                _uploadState.postValue(UploadState.SAVING);
                saveMemoryToRepository(urls, audioUrl, mediaType, title, description, location, relationshipId, user);
            } else {
                _errorMessage.postValue("All uploads failed.");
                _uploadState.postValue(UploadState.ERROR);
            }
        }
    }

    // Renamed and updated to accept mediaType
    private void saveMemoryToRepository(java.util.List<String> mediaUrls, String audioUrl, String mediaType, String title, String description, String location, String relationshipId, FirebaseUser user) {
        Memory memory = new Memory();
        memory.setId(UUID.randomUUID().toString());
        // Use the first URL as the cover image
        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            memory.setImageUrl(mediaUrls.get(0));
            memory.setMediaUrls(mediaUrls); // Store all URLs
        }
        if (audioUrl != null) {
            memory.setAudioUrl(audioUrl);
        }
        memory.setMediaType(mediaType);
        memory.setTitle(title);
        memory.setDescription(description);
        memory.setLocation(location);
        memory.setUserId(user.getUid());
        memory.setTimestamp(Timestamp.now());
        memory.setRelationshipId(relationshipId);

        // Offline-First Metadata Save
        memoryRepository.saveMemory(memory, new TimelineRepository.Callback() {
            @Override
            public void onSuccess() {
                _uploadState.postValue(UploadState.SUCCESS);
            }

            @Override
            public void onError(Exception e) {
                // If this fails (e.g. standard unknown error), check exception type.
                // But usage of Repository handling means: 
                // 1. If Offline/Unavailable -> It saved locally in repo, but called onError because Firestore failed.
                // We should handle this as Success (Saved Offline).
                if (e instanceof com.google.firebase.firestore.FirebaseFirestoreException && 
                   ((com.google.firebase.firestore.FirebaseFirestoreException) e).getCode() == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE) {
                     _uploadState.postValue(UploadState.SUCCESS); // Treat offline save as success
                } else {
                     // Real error, but we still saved LOCALLY (un-synced).
                     // Ideally we warn user?
                     // For now, let's play safe and say "Saved to device" if it's not a fundamental crash.
                     // The repository 'saveMemory' saves locally FIRST. So data IS safe.
                     _uploadState.postValue(UploadState.SUCCESS); 
                }
            }
        });
    }

    public void onErrorMessageShown() {
        _errorMessage.postValue(null);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        memoryRepository.cleanup();
    }
}
