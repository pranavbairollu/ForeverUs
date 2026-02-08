package com.example.foreverus;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MemoryRepository {

    private static final String TAG = "MemoryRepository";

    private final MemoryDao memoryDao;
    private final FirebaseFirestore firestore;
    private final ExecutorService executorService;
    private ListenerRegistration firestoreListener;

    public MemoryRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        this.memoryDao = db.memoryDao();
        this.firestore = FirebaseFirestore.getInstance();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<Resource<List<Memory>>> getAllMemories(String relationshipId) {
        MediatorLiveData<Resource<List<Memory>>> result = new MediatorLiveData<>();
        result.setValue(Resource.loading(null));

        LiveData<List<Memory>> localCache = memoryDao.getAllMemories(relationshipId);

        result.addSource(localCache, data -> {
            Resource<List<Memory>> currentResource = result.getValue();
            if (currentResource == null || currentResource.status != Resource.Status.ERROR) {
                result.setValue(Resource.success(data));
            } else {
                result.setValue(Resource.error(currentResource.message, data));
            }
        });

        synchronizeFromFirestore(relationshipId, result, localCache);

        return result;
    }

    private void synchronizeFromFirestore(String relationshipId, MediatorLiveData<Resource<List<Memory>>> result,
            LiveData<List<Memory>> localCache) {
        if (firestoreListener != null) {
            firestoreListener.remove();
        }

        Query query = firestore.collection("relationships").document(relationshipId).collection("memories")
                .orderBy("timestamp", Query.Direction.DESCENDING);

        firestoreListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Firestore listen failed.", e);
                result.postValue(Resource.error(e.getMessage(), localCache.getValue()));
                return;
            }

            if (snapshots != null && !snapshots.isEmpty()) {
                executorService.execute(() -> {
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        try {
                            Memory memory = dc.getDocument().toObject(Memory.class);
                            memory.setId(dc.getDocument().getId());
                            memory.setRelationshipId(relationshipId);
                            switch (dc.getType()) {
                                case ADDED:
                                case MODIFIED:
                                    memoryDao.insert(memory);
                                    break;
                                case REMOVED:
                                    memoryDao.deleteMemoryById(memory.getId());
                                    break;
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error processing memory document", ex);
                        }
                    }
                });
            }
        });
    }

    public void saveMemory(Memory memory, final TimelineRepository.Callback callback) {
        executorService.execute(() -> {
            // 1. Save Locally First (Offline Safety)
            memory.setSyncStatus(TimelineRepository.SyncStatus.UNSYNCED);
            memoryDao.insert(memory);

            // 2. Save to Firestore
            com.google.firebase.firestore.CollectionReference memoriesCollection = firestore.collection("relationships")
                    .document(memory.getRelationshipId())
                    .collection("memories");

            com.google.firebase.firestore.DocumentReference docRef;
            if (memory.getId() == null || memory.getId().isEmpty()) {
                docRef = memoriesCollection.document();
                memory.setId(docRef.getId());
            } else {
                docRef = memoriesCollection.document(memory.getId());
            }

            // Update local ID if it was generated
            memoryDao.insert(memory);

            docRef.set(memory)
                    .addOnSuccessListener(aVoid -> {
                        // 3. Success: Update Sync Status
                        executorService.execute(() -> {
                            memory.setSyncStatus(TimelineRepository.SyncStatus.SYNCED);
                            memoryDao.insert(memory);
                        });
                        if (callback != null)
                            callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        // 4. Failure
                        Log.e(TAG, "Error saving memory to Firestore", e);
                        // If offline/unavailable, we just keep the local copy (UNSYNCED).
                        // We still notify callback of error so UI can decide to show toast,
                        // but data is SAFE locally.
                        if (callback != null)
                            callback.onError(e);
                    });
        });
    }

    public void deleteMemory(String memoryId, String relationshipId, final TimelineRepository.Callback callback) {
        executorService.execute(() -> {
            // 1. Delete locally first (Optimistic)
            memoryDao.deleteMemoryById(memoryId);

            // 2. Delete from Firestore
            firestore.collection("relationships").document(relationshipId)
                    .collection("memories").document(memoryId)
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        if (callback != null)
                            callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error deleting memory from Firestore", e);
                        // Unlike save, if delete fails offline, we might want to queue it.
                        // For now, simpler approach: we already deleted locally.
                        // Sync mechanism (snapshot listener) handles incoming changes, but for outgoing
                        // deletions
                        // without a proper sync queue, it might stay on server if offline.
                        // Robust sync is outside scope for now, just best effort.
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
