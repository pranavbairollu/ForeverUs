
package com.example.foreverus;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StoryRepository {

    private static final String TAG = "StoryRepository";

    private final StoryDao storyDao;
    private final FirebaseFirestore firestore;
    private final ExecutorService executorService;
    private ListenerRegistration firestoreListener;

    public StoryRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        this.storyDao = db.storyDao();
        this.firestore = FirebaseFirestore.getInstance();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<Resource<List<Story>>> getAllStories(String relationshipId) {
        MediatorLiveData<Resource<List<Story>>> result = new MediatorLiveData<>();
        result.setValue(Resource.loading(null));

        LiveData<List<Story>> localCache = storyDao.getAllStories(relationshipId);

        result.addSource(localCache, data -> {
            Resource<List<Story>> currentResource = result.getValue();
            if (currentResource == null || currentResource.status != Resource.Status.ERROR) {
                result.setValue(Resource.success(data));
            } else {
                result.setValue(Resource.error(currentResource.message, data));
            }
        });

        synchronizeFromFirestore(relationshipId, result, localCache);

        return result;
    }

    private void synchronizeFromFirestore(String relationshipId, MediatorLiveData<Resource<List<Story>>> result,
            LiveData<List<Story>> localCache) {
        if (firestoreListener != null) {
            firestoreListener.remove();
        }

        Query query = firestore.collection("relationships").document(relationshipId).collection("stories")
                .orderBy("timestamp", Query.Direction.DESCENDING);

        firestoreListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Firestore listen failed.", e);
                result.postValue(Resource.error(e.getMessage(), localCache.getValue()));
                return;
            }

            if (snapshots != null) {
                executorService.execute(() -> {
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        Story story = dc.getDocument().toObject(Story.class);
                        story.storyId = dc.getDocument().getId();
                        story.setSyncStatus(TimelineRepository.SyncStatus.SYNCED);

                        switch (dc.getType()) {
                            case ADDED:
                            case MODIFIED:
                                // Safe Sync: Check local state before overwriting
                                Story localStory = storyDao.getStoryByIdSync(story.getId());
                                if (localStory != null
                                        && localStory.getSyncStatus() == TimelineRepository.SyncStatus.UNSYNCED) {
                                    Log.w(TAG, "Ignoring server update for story " + story.getId()
                                            + " due to pending local changes.");
                                    continue; // Skip overwrite
                                }
                                storyDao.insert(story);
                                break;
                            case REMOVED:
                                storyDao.deleteStoryById(story.storyId);
                                break;
                        }
                    }
                });
            }
        });
    }

    public void saveStoryWithTransaction(String relationshipId, String storyId, String title, String content,
            String currentUserId, long localVersion, boolean isConflictResolved,
            final TimelineRepository.TransactionCallback callback) {
        executorService.execute(() -> {
            // 1. Offline-First: Save locally immediately with UNSYNCED status
            // We use a temporary version (localVersion + 1) or keep it same, but mark as
            // UNSYNCED.
            // Using same version or +1 doesn't matter much for local-only, but let's be
            // consistent.
            // We'll trust the server version when we get it.
            Story localStory = new Story(storyId, title, content, currentUserId, relationshipId, localVersion,
                    TimelineRepository.SyncStatus.UNSYNCED, new com.google.firebase.Timestamp(new java.util.Date()));
            storyDao.insert(localStory);

            // 2. Attempt Cloud Sync
            DocumentReference storyRef = firestore.collection(FirestoreConstants.COLLECTION_RELATIONSHIPS)
                    .document(relationshipId).collection(FirestoreConstants.COLLECTION_STORIES).document(storyId);
            firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(storyRef);
                long serverVersion = 0;
                if (snapshot.exists()) {
                    Long serverVersionLong = snapshot.getLong(FirestoreConstants.FIELD_VERSION);
                    serverVersion = serverVersionLong != null ? serverVersionLong : 0L;
                }

                if (serverVersion > localVersion && !isConflictResolved) {
                    throw new FirebaseFirestoreException("Story has been modified by another user.",
                            FirebaseFirestoreException.Code.ABORTED);
                }

                long newVersion = serverVersion + 1;
                Map<String, Object> storyMap = new HashMap<>();
                storyMap.put(FirestoreConstants.FIELD_TITLE, title);
                storyMap.put(FirestoreConstants.FIELD_CONTENT, content);
                storyMap.put(FirestoreConstants.FIELD_TIMESTAMP, FieldValue.serverTimestamp());
                storyMap.put(FirestoreConstants.FIELD_LAST_EDITED_BY, currentUserId);
                storyMap.put(FirestoreConstants.FIELD_VERSION, newVersion);
                storyMap.put(FirestoreConstants.FIELD_RELATIONSHIP_ID, relationshipId);

                transaction.set(storyRef, storyMap);
                return newVersion;
            }).addOnSuccessListener(newVersion -> {
                // 3. Success: Update local with SYNCED status and valid server version
                executorService.execute(() -> {
                    Story syncedStory = new Story(storyId, title, content, currentUserId, relationshipId, newVersion,
                            TimelineRepository.SyncStatus.SYNCED,
                            new com.google.firebase.Timestamp(new java.util.Date()));
                    storyDao.insert(syncedStory);
                });
                if (callback != null)
                    callback.onTransactionSuccess(newVersion);
            }).addOnFailureListener(e -> {
                // 4. Failure: Check if Offline
                if (e instanceof FirebaseFirestoreException
                        && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    // It's already saved locally in step 1.
                    // We treat this as a "Success" from the UI perspective (data is safe).
                    // We pass a special callback or handle it in ViewModel.
                    // To keep interface simple, we can call error but with specific exception, OR
                    // success with -1 version?
                    // Let's rely on the callback interface. The ViewModel handles UNAVAILABLE
                    // specifically.
                    Log.w(TAG, "Offline save: Cloud sync failed, but local save persisted.");
                }

                if (callback != null) {
                    callback.onTransactionError(e);
                }
            });
        });
    }

    public void deleteStory(Story story, final TimelineRepository.Callback callback) {
        executorService.execute(() -> {
            storyDao.deleteStoryById(story.getId());
            firestore.collection("relationships").document(story.getRelationshipId())
                    .collection("stories").document(story.getId())
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        if (callback != null)
                            callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error deleting story", e);
                        executorService.execute(() -> storyDao.insert(story));
                        if (callback != null)
                            callback.onError(e);
                    });
        });
    }

    public void cleanup() {
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
