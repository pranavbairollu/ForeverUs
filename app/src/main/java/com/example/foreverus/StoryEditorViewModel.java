
package com.example.foreverus;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class StoryEditorViewModel extends AndroidViewModel {

    private static final String LOCAL_STORY_VERSION_KEY = "localStoryVersion";
    private static final String IS_CONFLICT_RESOLVED_KEY = "isConflictResolved";

    private final SavedStateHandle savedStateHandle;
    private final MutableLiveData<Resource<Story>> story = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isSaving = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasUnsavedChanges = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isConflict = new MutableLiveData<>(false);
    private final MutableLiveData<String> partnerName = new MutableLiveData<>("Your Partner");
    private final MutableLiveData<Void> storyDeleted = new MutableLiveData<>();

    private FirebaseFirestore db;
    private DocumentReference storyRef;
    private ListenerRegistration storyListener;
    private final StoryRepository storyRepository;

    private String relationshipId;
    private String storyId;
    private String currentUserId;

    // Persistent Undo/Redo History for Rotation
    private final java.util.LinkedList<UndoRedoHelper.EditItem> undoHistory = new java.util.LinkedList<>();
    private final java.util.LinkedList<UndoRedoHelper.EditItem> redoHistory = new java.util.LinkedList<>();

    public StoryEditorViewModel(Application application, SavedStateHandle savedStateHandle) {
        super(application);
        this.savedStateHandle = savedStateHandle;
        this.storyRepository = new StoryRepository(application);
    }

    public void init(String relationshipId, String storyId) {
        if (this.storyId != null) {
            return; // Already initialized, prevent duplicate listeners on rotation
        }

        this.relationshipId = relationshipId;
        this.storyId = storyId;

        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        }

        if (this.relationshipId == null || this.relationshipId.isEmpty()) {
            story.setValue(Resource.error("Relationship ID is missing.", null));
            return;
        }

        if (this.storyId == null) {
            storyRef = db.collection(FirestoreConstants.COLLECTION_RELATIONSHIPS).document(this.relationshipId)
                    .collection(FirestoreConstants.COLLECTION_STORIES).document();
            this.storyId = storyRef.getId();
            setLocalStoryVersion(0L);
        } else {
            storyRef = db.collection(FirestoreConstants.COLLECTION_RELATIONSHIPS).document(this.relationshipId)
                    .collection(FirestoreConstants.COLLECTION_STORIES).document(this.storyId);
        }

        fetchPartnerName();
        loadStory();
    }

    private void fetchPartnerName() {
        db.collection(FirestoreConstants.COLLECTION_RELATIONSHIPS).document(relationshipId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        @SuppressWarnings("unchecked")
                        List<String> members = (List<String>) documentSnapshot.get(FirestoreConstants.FIELD_MEMBERS);
                        if (members != null) {
                            String partnerId = null;
                            for (String id : members) {
                                if (!id.equals(currentUserId)) {
                                    partnerId = id;
                                    break;
                                }
                            }
                            if (partnerId != null) {
                                db.collection(FirestoreConstants.COLLECTION_USERS).document(partnerId)
                                        .get()
                                        .addOnSuccessListener(userDocument -> {
                                            if (userDocument.exists()
                                                    && userDocument.contains(FirestoreConstants.FIELD_NAME)) {
                                                partnerName.setValue(
                                                        userDocument.getString(FirestoreConstants.FIELD_NAME));
                                            }
                                        });
                            }
                        }
                    }
                });
    }

    public void loadStory() {
        story.setValue(Resource.loading(null));
        storyListener = storyRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                story.setValue(Resource.error("Error loading story: " + e.getMessage(), null));
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                Long serverVersionLong = snapshot.getLong(FirestoreConstants.FIELD_VERSION);
                long serverVersion = serverVersionLong != null ? serverVersionLong : 0L;

                if (Boolean.TRUE.equals(isSaving.getValue())) {
                    return;
                }

                if (serverVersion > getLocalStoryVersion() && Boolean.TRUE.equals(hasUnsavedChanges.getValue())
                        && !isConflictResolved()) {
                    isConflict.setValue(true);
                } else {
                    Story serverStory = snapshot.toObject(Story.class);
                    if (serverStory != null) {
                        serverStory.storyId = snapshot.getId();
                        setLocalStoryVersion(serverVersion);
                        hasUnsavedChanges.setValue(false);
                        isConflict.setValue(false);
                        setConflictResolved(false);
                        story.setValue(Resource.success(serverStory));
                    } else {
                        story.setValue(Resource.error("Failed to parse story data.", null));
                    }
                }

            } else {
                Story newOrEmptyStory = new Story();
                if (this.storyId != null) {
                    newOrEmptyStory.storyId = this.storyId;
                }
                story.setValue(Resource.success(newOrEmptyStory));
                setLocalStoryVersion(0L);
            }
        });
    }

    public void onTextChanged() {
        if (story.getValue() != null && story.getValue().status == Resource.Status.SUCCESS) {
            hasUnsavedChanges.setValue(true);
        }
    }

    public void resolveConflictByReloading() {
        setConflictResolved(true);
        isConflict.setValue(false);
        loadStory();
    }

    public void resolveConflictByOverwriting(String title, String content) {
        setConflictResolved(true);
        isConflict.setValue(false);
        saveStory(title, content);
    }

    private boolean pendingSave = false;
    private String pendingSaveTitle;
    private String pendingSaveContent;

    public void saveStory(String title, String content) {
        if (Boolean.TRUE.equals(isSaving.getValue())) {
            // Queue this save request to run immediately after the current one finishes
            pendingSave = true;
            pendingSaveTitle = title;
            pendingSaveContent = content;
            return;
        }

        isSaving.setValue(true);

        storyRepository.saveStoryWithTransaction(relationshipId, storyId, title, content, currentUserId,
                getLocalStoryVersion(), isConflictResolved(), new TimelineRepository.TransactionCallback() {
                    @Override
                    public void onTransactionSuccess(long newVersion) {
                        handleSaveCompletion(newVersion, null);
                    }

                    @Override
                    public void onTransactionError(Exception e) {
                        handleSaveCompletion(-1, e);
                    }
                });
    }

    private void handleSaveCompletion(long newVersion, Exception e) {
        if (newVersion != -1) {
            setLocalStoryVersion(newVersion);
            setConflictResolved(false);
            hasUnsavedChanges.postValue(false);
        }

        if (e != null) {
            // Error handling logic
            if (e instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
                if (firestoreException.getCode() == FirebaseFirestoreException.Code.ABORTED) {
                    isConflict.postValue(true);
                } else if (firestoreException.getCode() == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    // Offline success
                    hasUnsavedChanges.postValue(false);
                    setConflictResolved(false);
                    Story current = story.getValue() != null ? story.getValue().data : null;
                    story.postValue(new Resource<>(Resource.Status.SUCCESS, current, "Saved to device (Offline)"));
                } else {
                    story.postValue(Resource.error("Error saving story: " + e.getMessage(), null));
                }
            } else {
                story.postValue(Resource.error("Error saving story: " + e.getMessage(), null));
            }
        }

        // Check for pending save
        if (pendingSave) {
            String nextTitle = pendingSaveTitle;
            String nextContent = pendingSaveContent;
            pendingSave = false;
            pendingSaveTitle = null;
            pendingSaveContent = null;

            // Execute the pending save immediately (recursive but async due to repository)
            // We set isSaving to false briefly to allow the call, but actually we can just
            // call internal logic?
            // Safer to reset isSaving and call public method to ensure strict state
            isSaving.postValue(false); // Update LiveData? No, we are on bg thread probably?
            // Repository callback is likely on BG thread? No, usually callbacks are posted
            // to main?
            // Let's check Repository. It executes on Executor, but where does it call
            // callback?
            // It calls callback inside executor. So we are on BG thread.
            // isSaving.setValue needs Main Thread. isSaving.postValue is safe.

            // We need to trigger the next save.
            // We can't call strictly recursive since we need to wait for isSaving to clear?
            // Actually, we can just run it directly.

            storyRepository.saveStoryWithTransaction(relationshipId, storyId, nextTitle, nextContent, currentUserId,
                    getLocalStoryVersion(), isConflictResolved(), new TimelineRepository.TransactionCallback() {
                        @Override
                        public void onTransactionSuccess(long nextVer) {
                            handleSaveCompletion(nextVer, null);
                        }

                        @Override
                        public void onTransactionError(Exception nextE) {
                            handleSaveCompletion(-1, nextE);
                        }
                    });
            // Note: We keep isSaving = true implicitly because we don't set it to false
            // yet.
        } else {
            isSaving.postValue(false);
        }
    }

    public void deleteStory() {
        Story currentStory = story.getValue() != null ? story.getValue().data : null;
        if (currentStory != null) {
            isSaving.setValue(true); // to lock UI
            storyRepository.deleteStory(currentStory, new TimelineRepository.Callback() {
                @Override
                public void onSuccess() {
                    isSaving.postValue(false);
                    storyDeleted.postValue(null);
                }

                @Override
                public void onError(Exception e) {
                    isSaving.postValue(false);
                    story.postValue(Resource.error("Error deleting story: " + e.getMessage(), null));
                }
            });
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (storyListener != null) {
            storyListener.remove();
        }
        storyRepository.cleanup();
    }

    public LiveData<Resource<Story>> getStory() {
        return story;
    }

    public LiveData<Boolean> getIsSaving() {
        return isSaving;
    }

    public LiveData<Boolean> getHasUnsavedChanges() {
        return hasUnsavedChanges;
    }

    public LiveData<Boolean> getIsConflict() {
        return isConflict;
    }

    public LiveData<String> getPartnerName() {
        return partnerName;
    }

    public LiveData<Void> getStoryDeleted() {
        return storyDeleted;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public java.util.LinkedList<UndoRedoHelper.EditItem> getUndoHistory() {
        return undoHistory;
    }

    public java.util.LinkedList<UndoRedoHelper.EditItem> getRedoHistory() {
        return redoHistory;
    }

    private long getLocalStoryVersion() {
        Long version = savedStateHandle.get(LOCAL_STORY_VERSION_KEY);
        return version != null ? version : -1L;
    }

    private void setLocalStoryVersion(long version) {
        savedStateHandle.set(LOCAL_STORY_VERSION_KEY, version);
    }

    private boolean isConflictResolved() {
        Boolean resolved = savedStateHandle.get(IS_CONFLICT_RESOLVED_KEY);
        return resolved != null ? resolved : false;
    }

    private void setConflictResolved(boolean isConflictResolved) {
        savedStateHandle.set(IS_CONFLICT_RESOLVED_KEY, isConflictResolved);
    }
}
