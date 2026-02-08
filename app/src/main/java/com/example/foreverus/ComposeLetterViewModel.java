package com.example.foreverus;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.Timestamp;

import java.io.File;
import java.util.Date;
import java.util.Map;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import java.util.UUID;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import org.json.JSONException;
import org.json.JSONObject;

public class ComposeLetterViewModel extends AndroidViewModel {

    // UI State
    public enum Status {
        IDLE, RECORDING, PLAYING, LOADING, SUCCESS, ERROR
    }

    public static class ViewState {
        public Status status;
        public String message;
        public String draftTitle;
        public String draftContent;

        public ViewState(Status s, String m) {
            status = s;
            message = m;
        }
    }

    private final MutableLiveData<ViewState> _state = new MutableLiveData<>(new ViewState(Status.IDLE, null));

    public LiveData<ViewState> getState() {
        return _state;
    }

    // Data State
    private Uri selectedImageUri;
    private String audioFilePath;
    private Date openDate;
    private String relationshipId;
    private String currentUserId;
    private String theme = "classic"; // Default

    // Logic Objects
    private final LetterRepository letterRepository;
    // We don't hold MediaRecorder/Player here directly because they are tied to
    // Context/Activity often,
    // BUT for robustness, the ViewModel *controlling* the state is key.
    // Actually, MediaRecorder needs strict lifecycle management.
    // Providing paths and state flags is safer. Activity can handle the actual
    // MediaRecorder API to avoid Context leaks in VM.

    private boolean isRecording = false;
    private boolean isPlaying = false;

    public ComposeLetterViewModel(@NonNull Application application) {
        super(application);
        letterRepository = new LetterRepository(application);
    }

    public void init(String relationshipId, String currentUserId) {
        this.relationshipId = relationshipId;
        this.currentUserId = currentUserId;
        restoreDraft();
    }

    private static final String PREF_DRAFT = "draft_letter_";

    public void saveDraft(String title, String content) {
        if (relationshipId == null)
            return;

        // Don't save empty drafts if there was nothing there
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)
                && selectedImageUri == null && audioFilePath == null && openDate == null) {
            return;
        }

        SharedPreferences prefs = getApplication().getSharedPreferences("foreverus_drafts", Context.MODE_PRIVATE);
        JSONObject json = new JSONObject();
        try {
            json.put("title", title);
            json.put("content", content);
            if (openDate != null)
                json.put("openDate", openDate.getTime());
            if (selectedImageUri != null)
                json.put("imageUri", selectedImageUri.toString());
            if (audioFilePath != null)
                json.put("audioPath", audioFilePath);
            json.put("theme", theme);

            prefs.edit().putString(PREF_DRAFT + relationshipId, json.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void restoreDraft() {
        if (relationshipId == null)
            return;
        SharedPreferences prefs = getApplication().getSharedPreferences("foreverus_drafts", Context.MODE_PRIVATE);
        String jsonString = prefs.getString(PREF_DRAFT + relationshipId, null);

        if (jsonString != null) {
            try {
                JSONObject json = new JSONObject(jsonString);

                if (json.has("openDate"))
                    openDate = new Date(json.getLong("openDate"));

                if (json.has("imageUri")) {
                    String uriString = json.getString("imageUri");
                    Uri parsedUri = Uri.parse(uriString);
                    // VALIDATION COPIED FROM UPLOAD_CHECK
                    boolean fileExists = true;
                    if (parsedUri.getScheme() == null || "file".equals(parsedUri.getScheme())) {
                        String path = parsedUri.getPath();
                        if (path != null) {
                            File file = new File(path);
                            if (!file.exists()) {
                                fileExists = false;
                            }
                        }
                    }

                    if (fileExists) {
                        selectedImageUri = parsedUri;
                    } else {
                        // File lost (OS cleared cache or other issue)
                        selectedImageUri = null;
                        // Optional: Could set a flag to warn user "Image attachment lost"
                    }
                }

                if (json.has("audioPath")) {
                    String path = json.getString("audioPath");
                    File file = new File(path);
                    if (file.exists()) {
                        audioFilePath = path;
                    } else {
                        audioFilePath = null;
                    }
                }

                if (json.has("theme"))
                    theme = json.getString("theme");

                // For Title/Content, we need to pass them back to Activity.
                ViewState draftState = new ViewState(Status.IDLE, "Draft Restored");
                draftState.draftTitle = json.optString("title", "");
                draftState.draftContent = json.optString("content", "");
                _state.setValue(draftState); // Post immediately

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void clearDraft() {
        if (relationshipId == null)
            return;
        SharedPreferences prefs = getApplication().getSharedPreferences("foreverus_drafts", Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_DRAFT + relationshipId).apply();
    }

    // --- Getters/Setters for Activity Restoration ---
    public Uri getSelectedImageUri() {
        return selectedImageUri;
    }

    public void setSelectedImageUri(Uri uri) {
        this.selectedImageUri = uri;
    } // Trigger update? Activity pulls this.

    public String getAudioFilePath() {
        return audioFilePath;
    }

    public void setAudioFilePath(String path) {
        this.audioFilePath = path;
    }

    public Date getOpenDate() {
        return openDate;
    }

    public void setOpenDate(Date date) {
        this.openDate = date;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void setRecording(boolean recording) {
        this.isRecording = recording;
        if (recording)
            _state.postValue(new ViewState(Status.RECORDING, null));
        else
            _state.postValue(new ViewState(Status.IDLE, null));
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        if (playing)
            _state.postValue(new ViewState(Status.PLAYING, null));
        else
            _state.postValue(new ViewState(Status.IDLE, null));
    }

    // --- Image Handling ---

    public void setImageFromGallery(Uri sourceUri) {
        // We must copy this file to our internal cache so we have persistent access
        // (Drafts need to survive app kills, and Gallery URIs have transient
        // permissions)
        _state.setValue(new ViewState(Status.LOADING, "Processing Image..."));

        new Thread(() -> {
            try {
                java.io.InputStream is = getApplication().getContentResolver().openInputStream(sourceUri);
                if (is == null) {
                    _state.postValue(new ViewState(Status.ERROR, "Failed to open image"));
                    return;
                }

                // Create a file in INTERNAL FILES (not cache) to prevent OS deletion
                File draftsDir = new File(getApplication().getFilesDir(), "drafts");
                if (!draftsDir.exists())
                    draftsDir.mkdirs();

                File destFile = new File(draftsDir, "draft_img_" + System.currentTimeMillis() + ".jpg");

                java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.close();
                is.close();

                // Success
                Uri localUri = Uri.fromFile(destFile);
                this.selectedImageUri = localUri;

                // Update UI (IDLE state with message to trigger reload)
                ViewState vs = new ViewState(Status.IDLE, "Image Added");
                // We can abuse draftTitle/Content to pass data? No, that's dirty.
                // Best: Just set state IDLE. Activity's onActivityResult handles the preview
                // initially,
                // BUT we want to ensure the Activity uses THIS localUri if it reloads.
                // Actually, the Activity sets the preview from the *result* URI immediately.
                // So we just need to ensure *this* VM field is updated for Save/Upload.

                _state.postValue(vs);

            } catch (Exception e) {
                e.printStackTrace();
                _state.postValue(new ViewState(Status.ERROR, "Failed to cache image"));
            }
        }).start();
    }

    // --- Actions ---

    public void removeImage() {
        this.selectedImageUri = null;
        _state.postValue(new ViewState(Status.IDLE, "Image Removed"));
    }

    public void deleteAudio() {
        if (audioFilePath != null) {
            try {
                new File(audioFilePath).delete();
            } catch (Exception e) {
            }
            audioFilePath = null;
        }
    }

    public void sendLetter(String title, String content) {
        if (_state.getValue().status == Status.LOADING)
            return;

        _state.setValue(new ViewState(Status.LOADING, "Preparing..."));

        // Validation handled in Activity or here? Activity for Toasts usually, but here
        // is safer logic.
        // Assuming valid inputs passed.

        boolean hasImage = (selectedImageUri != null);
        boolean hasAudio = (audioFilePath != null);

        if (hasImage || hasAudio) {
            uploadNextMedia(hasImage, hasAudio, title, content);
        } else {
            finalizeSend(title, content, null, null);
        }
    }

    private static final int MAX_RETRIES = 3;

    private void uploadNextMedia(boolean hasImage, boolean hasAudio, String title, String content) {
        if (hasImage) {
            uploadMediaWithRetry(selectedImageUri, "image", 0, new MediaUploadCallback() {
                @Override
                public void onSuccess(String url) {
                    handleUploadStep(url, null, false, hasAudio, title, content);
                }

                @Override
                public void onFailure(String error) {
                    handleUploadFailure(title, content, "Image Upload Failed: " + error);
                }
            });
        } else if (hasAudio) {
            uploadMediaWithRetry(Uri.parse(audioFilePath), "video", 0, new MediaUploadCallback() { // video resource
                                                                                                   // type for audio in
                                                                                                   // Cloudinary
                @Override
                public void onSuccess(String url) {
                    handleUploadStep(null, url, false, false, title, content);
                }

                @Override
                public void onFailure(String error) {
                    handleUploadFailure(title, content, "Audio Upload Failed: " + error);
                }
            });
        }
    }

    private void handleUploadStep(String imageUrl, String audioUrl, boolean pendingImage, boolean pendingAudio,
            String title, String content) {
        // imageUrl is effectively final and can be used in the inner class

        if (pendingAudio) {
            _state.postValue(new ViewState(Status.LOADING, "Uploading Audio..."));
            uploadMediaWithRetry(Uri.parse(audioFilePath), "video", 0, new MediaUploadCallback() {
                @Override
                public void onSuccess(String url) {
                    finalizeSend(title, content, imageUrl, url);
                }

                @Override
                public void onFailure(String error) {
                    handleUploadFailure(title, content, "Audio Upload Failed: " + error);
                }
            });
        } else {
            finalizeSend(title, content, imageUrl, audioUrl);
        }
    }

    private void handleUploadFailure(String title, String content, String errorMsg) {
        saveDraft(title, content); // Force save
        _state.postValue(new ViewState(Status.ERROR, errorMsg + "\nDraft Saved."));
    }

    private interface MediaUploadCallback {
        void onSuccess(String url);

        void onFailure(String error);
    }

    private void uploadMediaWithRetry(Uri uri, String resourceType, int retryCount, MediaUploadCallback callback) {
        if (uri == null || (uri.toString().isEmpty())) {
            callback.onFailure("Invalid URI");
            return;
        }

        // Robust File Existence Check for Local Files
        if (uri.getScheme() == null || "file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                File file = new File(path);
                if (!file.exists() || file.length() == 0) {
                    callback.onFailure("File not found or empty: " + path);
                    return;
                }
            }
        }

        com.cloudinary.android.UploadRequest request;

        // Dispatch based on type to satisfy compiler & SDK quirks
        // Cloudinary Android SDK 1.x sometimes prefers String paths for local files
        if (uri.getScheme() == null) {
            request = MediaManager.get().upload(uri.getPath());
        } else {
            request = MediaManager.get().upload(uri);
        }

        request.unsigned("foreverus_memories") // Ensure this Preset exists in Cloudinary Settings!
                .option("resource_type", resourceType.equals("video") ? "video" : "image")
                .callback(new UploadCallback() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        if (url == null)
                            url = (String) resultData.get("url");
                        callback.onSuccess(url);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        String errorMsg = error.getDescription() + " (Code: " + error.getCode() + ")";

                        if (retryCount < MAX_RETRIES) {
                            _state.postValue(new ViewState(Status.LOADING,
                                    "Error: " + error.getDescription() + ". Retrying... (" + (retryCount + 1) + "/"
                                            + MAX_RETRIES + ")"));
                            new Handler(Looper.getMainLooper()).postDelayed(
                                    () -> uploadMediaWithRetry(uri, resourceType, retryCount + 1, callback), 2000);
                        } else {
                            callback.onFailure(errorMsg);
                        }
                    }

                    @Override
                    public void onStart(String requestId) {
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                    }
                }).dispatch();
    }

    private void finalizeSend(String title, String content, String mediaUrl, String audioUrl) {
        _state.postValue(new ViewState(Status.LOADING, "Sending Letter..."));

        Letter letter = new Letter();
        letter.setLetterId(UUID.randomUUID().toString());
        letter.setRelationshipId(relationshipId);
        letter.setFromId(currentUserId);
        letter.setToId("PARTNER_ID_PLACEHOLDER"); // Logic needs Partner ID.
        // Strategy: Activity should pass Partner ID or we fetch it here.
        // Fetching here is better for Robustness.

        letter.setTitle(title);
        letter.setMessage(content);
        letter.setTheme(theme);
        letter.setOpenDate(openDate);
        letter.setOpened(false);
        letter.setMediaUrl(mediaUrl);
        letter.setAudioUrl(audioUrl);
        letter.setTimestamp(Timestamp.now());

        // Using Repository to get Partner ID is tricky if Repo doesn't have that method
        // exposed simply.
        // Activity Logic for fetching Partner ID was: Fetch Firestore or Cache.
        // We should replicate that here using Firestore instance? Or pass it in.
        // Simplest Refactor: Pass PartnerID to sendLetter, handling the lookup in
        // Activity or here.
        // Let's handle it here for maximum safety (background).

        fetchPartnerAndSave(letter);
    }

    private void fetchPartnerAndSave(Letter letter) {
        // ... Logic to fetch from Firestore ...
        // For now, let's assume we use the Activity's logic but shifted here.
        // Actually, to avoid importing Firestore everywhere in VM, let's look at
        // LetterRepository.
        // Does it have 'getRelationship'?

        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore
                .getInstance();
        db.collection(FirestoreConstants.COLLECTION_RELATIONSHIPS).document(relationshipId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.exists()) {
                        java.util.List<String> members = (java.util.List<String>) snap
                                .get(FirestoreConstants.FIELD_MEMBERS);
                        if (members != null) {
                            boolean partnerFound = false;
                            for (String id : members) {
                                if (!id.equals(currentUserId)) {
                                    letter.setToId(id);
                                    partnerFound = true;

                                    // CACHE FOR OFFLINE RESILIENCE
                                    getApplication().getSharedPreferences("ForeverUsPrefs", Context.MODE_PRIVATE)
                                            .edit().putString("partnerId_" + relationshipId, id).apply();

                                    saveToRepo(letter);
                                    return;
                                }
                            }
                            if (!partnerFound) {
                                _state.postValue(new ViewState(Status.ERROR, "No partner found in relationship"));
                                return;
                            }
                        }
                    }
                    // Fail or fallback
                    _state.postValue(new ViewState(Status.ERROR, "Relationship not found"));
                })
                .addOnFailureListener(e -> {
                    // Fallback to cache? Need Context/SharedPreferences.
                    // VMs have Application context.
                    // Doing this "Bulletproof" means handling offline too.
                    // Check SharedPreferences for cache.
                    android.content.SharedPreferences prefs = getApplication().getSharedPreferences("ForeverUsPrefs",
                            android.content.Context.MODE_PRIVATE);
                    String cached = prefs.getString("partnerId_" + relationshipId, null);
                    if (cached != null) {
                        letter.setToId(cached);
                        saveToRepo(letter);
                    } else {
                        _state.postValue(new ViewState(Status.ERROR, "Start online to sync partner."));
                    }
                });
    }

    private void saveToRepo(Letter letter) {
        letterRepository.sendLetter(letter, new TimelineRepository.Callback() {
            @Override
            public void onSuccess() {
                clearDraft(); // CORRECT BEHAVIOR: Clear draft after successful send.
                scheduleUnlockNotification(letter);
                _state.postValue(new ViewState(Status.SUCCESS, "Letter Sent!"));
            }

            @Override
            public void onError(Exception e) {
                if (e instanceof com.google.firebase.firestore.FirebaseFirestoreException &&
                        ((com.google.firebase.firestore.FirebaseFirestoreException) e)
                                .getCode() == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE) {
                    clearDraft(); // Offline send counts as "Sent" for the UI. Worker handles it.
                    scheduleUnlockNotification(letter); // Even offline, schedule it locally!
                    _state.postValue(new ViewState(Status.SUCCESS, "Saved Offline."));
                } else {
                    _state.postValue(new ViewState(Status.SUCCESS, "Saved locally (Sync pending)."));
                }
            }
        });
    }

    private void scheduleUnlockNotification(Letter letter) {
        if (letter.getOpenDate() == null)
            return;

        String senderName = "Your Partner";
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && !TextUtils.isEmpty(user.getDisplayName())) {
            senderName = user.getDisplayName();
        }

        // Delegate to Scheduler
        NotificationScheduler.scheduleAlarm(getApplication(),
                letter.getLetterId(),
                letter.getOpenDate().getTime(),
                senderName,
                letter.getRelationshipId());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Clean up temp file if not sent?
        // Logic: If status is SUCCESS, file is uploaded/saved. If IDLE/ERROR, maybe
        // user gave up.
        // But we can't easily distinguish "Backgrounded" vs "Destroyed".
        // Better to NOT delete aggressively unless we are sure.
        // Or delete in onSuccess after upload? Activity cleans up "cache" dir
        // periodically?
    }
}
