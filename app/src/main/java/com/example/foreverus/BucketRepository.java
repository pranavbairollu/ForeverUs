package com.example.foreverus;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.foreverus.TimelineRepository.SyncStatus;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BucketRepository {

    private static final String TAG = "BucketRepository";

    private final BucketDao bucketDao;
    private final FirebaseFirestore firestore;
    private final ExecutorService executorService;
    private ListenerRegistration firestoreListener;

    public BucketRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        this.bucketDao = db.bucketDao();
        this.milestoneDao = db.milestoneDao();
        this.firestore = FirebaseFirestore.getInstance();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<BucketItem>> getAllBucketItems(String relationshipId) {
        synchronizeBucketItems(relationshipId);
        return bucketDao.getAllBucketItems(relationshipId);
    }

    private void synchronizeBucketItems(String relationshipId) {
        if (firestoreListener != null)
            firestoreListener.remove();

        Query query = firestore.collection("relationships").document(relationshipId).collection("bucket_list");

        firestoreListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            if (snapshots != null) {
                executorService.execute(() -> {
                    // 0. Sync Recovery (Offline -> Online)
                    uploadUnsyncedItems(relationshipId);

                    // 1. Get Local Items to map IDs
                    List<BucketItem> localItems = bucketDao.getAllBucketItemsSync(relationshipId);
                    Map<String, BucketItem> localMap = new HashMap<>();
                    for (BucketItem item : localItems) {
                        localMap.put(item.getId(), item);
                    }

                    List<BucketItem> toInsertOrUpdate = new ArrayList<>();
                    List<String> validIds = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String docId = doc.getId();
                        validIds.add(docId);

                        // Check if we have a pending local edit
                        BucketItem localVersion = localMap.get(docId);
                        if (localVersion != null && localVersion.getSyncStatus() == SyncStatus.UNSYNCED) {
                            // We have unsynced local changes. Do NOT overwrite with remote data yet.
                            // The uploadUnsyncedItems() method will handle pushing our changes up.
                            Log.d(TAG, "Skipping overwrite of unsynced local item: " + docId);
                            continue;
                        }

                        BucketItem newItem = doc.toObject(BucketItem.class);
                        newItem.setId(docId); // Ensure ID is consistent
                        newItem.setRelationshipId(relationshipId);
                        newItem.setSyncStatus(SyncStatus.SYNCED);

                        toInsertOrUpdate.add(newItem);
                    }

                    if (!toInsertOrUpdate.isEmpty()) {
                        bucketDao.insertAll(toInsertOrUpdate);
                    }

                    // Sync Deletions (if local has it, but remote doesn't, and local says it WAS
                    // synced, then delete local)
                    // If local says UNSYNCED, keep it (it's new, not yet uploaded).
                    for (BucketItem local : localItems) {
                        if (local.getSyncStatus() == SyncStatus.SYNCED && !validIds.contains(local.getId())) {
                            bucketDao.delete(local);
                        }
                    }
                });
            }
        });
    }

    private void uploadUnsyncedItems(String relationshipId) {
        // Query logic would be: SELECT * FROM bucket_items WHERE syncStatus = UNSYNCED
        // Need to add that query to DAO first if not present.
        // For now, let's just iterate (inefficient but safe for MVP with small lists).
        List<BucketItem> all = bucketDao.getAllBucketItemsSync(relationshipId);
        for (BucketItem item : all) {
            if (item.getSyncStatus() == SyncStatus.UNSYNCED) {
                if (item.isDeleted()) {
                    // Handle queued delete
                    firestore.collection("relationships").document(relationshipId).collection("bucket_list")
                            .document(item.getId())
                            .delete()
                            .addOnSuccessListener(aVoid -> executorService.execute(() -> {
                                bucketDao.delete(item); // Physical cleanup
                                Log.d(TAG, "Synced offline deletion: " + item.getTitle());
                            }));
                } else {
                    // Handle queued update/insert
                    firestore.collection("relationships").document(relationshipId).collection("bucket_list")
                            .document(item.getId())
                            .set(item)
                            .addOnSuccessListener(aVoid -> executorService.execute(() -> {
                                item.setSyncStatus(SyncStatus.SYNCED);
                                bucketDao.update(item);
                                Log.d(TAG, "Synced offline bucket item: " + item.getTitle());
                            }));
                }
            }
        }
    }

    public void addBucketItem(String relationshipId, BucketItem item) {
        executorService.execute(() -> {
            item.setRelationshipId(relationshipId);
            item.setSyncStatus(SyncStatus.UNSYNCED);
            bucketDao.insert(item);

            firestore.collection("relationships").document(relationshipId).collection("bucket_list")
                    .document(item.getId())
                    .set(item)
                    .addOnSuccessListener(aVoid -> executorService.execute(() -> {
                        item.setSyncStatus(SyncStatus.SYNCED);
                        bucketDao.update(item);
                    }))
                    .addOnFailureListener(e -> Log.w(TAG, "Error adding bucket item", e));
        });
    }

    public void updateBucketItem(String relationshipId, BucketItem item) {
        executorService.execute(() -> {
            item.setSyncStatus(SyncStatus.UNSYNCED);
            bucketDao.update(item); // Optimistic

            firestore.collection("relationships").document(relationshipId).collection("bucket_list")
                    .document(item.getId())
                    .set(item)
                    .addOnSuccessListener(aVoid -> executorService.execute(() -> {
                        item.setSyncStatus(SyncStatus.SYNCED);
                        bucketDao.update(item);
                    }));
        });
    }

    public void deleteBucketItem(String relationshipId, BucketItem item) {
        executorService.execute(() -> {
            // Soft Delete Locally first
            item.setDeleted(true);
            item.setSyncStatus(SyncStatus.UNSYNCED);
            bucketDao.update(item);

            // Try to delete from Firestore immediately (if online)
            firestore.collection("relationships").document(relationshipId).collection("bucket_list")
                    .document(item.getId())
                    .delete()
                    .addOnSuccessListener(aVoid -> executorService.execute(() -> {
                        // On success, we can physically delete locally or mark as synced (deleted).
                        // Let's physically delete it to clean up.
                        bucketDao.delete(item);
                    }))
                    .addOnFailureListener(e -> {
                        // Failed (Offline). Item stays Soft Deleted (isDeleted=true) and UNSYNCED.
                        // uploadUnsyncedItems will handle it later.
                        Log.d(TAG, "Offline delete queued for: " + item.getTitle());
                    });
        });
    }

    public void updateItemPriorities(String relationshipId, List<BucketItem> items) {
        executorService.execute(() -> {
            // 1. Update Local DB immediately for UI responsiveness
            // We iterate and update priority fields.
            for (BucketItem item : items) {
                // Assuming 'items' has specific order.
                // Or better, 'items' ALREADY has the new priorityOrder set by the adapter?
                // Yes, adapter usually sets it. We just need to persist.
                item.setSyncStatus(SyncStatus.UNSYNCED);
                bucketDao.update(item);
            }

            // 2. Batch Update to Firestore
            com.google.firebase.firestore.WriteBatch batch = firestore.batch();
            for (BucketItem item : items) {
                batch.update(
                        firestore.collection("relationships").document(relationshipId).collection("bucket_list")
                                .document(item.getId()),
                        "priorityOrder", item.getPriorityOrder());
            }

            batch.commit()
                    .addOnSuccessListener(aVoid -> executorService.execute(() -> {
                        // Mark as Synced
                        for (BucketItem item : items) {
                            item.setSyncStatus(SyncStatus.SYNCED);
                            bucketDao.update(item);
                        }
                    }))
                    .addOnFailureListener(e -> Log.w(TAG, "Batch priority update failed", e));
        });
    }

    // Presence Logic
    private ListenerRegistration presenceListener;

    public void setPresence(String relationshipId, String userId, boolean isOnline) {
        if (relationshipId == null || userId == null)
            return;
        Map<String, Object> data = new HashMap<>();
        data.put("isOnline", isOnline);
        data.put("lastSeen", new java.util.Date());

        firestore.collection("relationships").document(relationshipId)
                .collection("presence").document(userId)
                .set(data);
    }

    public LiveData<Boolean> getPartnerPresence(String relationshipId, String myUserId) {
        if (presenceListener != null) {
            presenceListener.remove();
        }
        MutableLiveData<Boolean> presence = new MutableLiveData<>(false);
        // Listen to the OTHER document in presence collection
        // Since we don't know partner ID easily here, let's listen to the collection
        // and ignore self.

        presenceListener = firestore.collection("relationships").document(relationshipId)
                .collection("presence")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null)
                        return;
                    boolean partnerOnline = false;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                        if (!doc.getId().equals(myUserId)) {
                            // This is a partner
                            Boolean online = doc.getBoolean("isOnline");
                            if (Boolean.TRUE.equals(online)) {
                                partnerOnline = true;
                                break;
                            }
                        }
                    }
                    presence.postValue(partnerOnline);
                });
        return presence;
    }

    public void cleanup() {
        if (firestoreListener != null)
            firestoreListener.remove();
        if (presenceListener != null)
            presenceListener.remove();
        if (executorService != null && !executorService.isShutdown())
            executorService.shutdown();
    }

    // Milestone Logic
    private final MilestoneDao milestoneDao;

    public void addMilestone(Milestone milestone) {
        executorService.execute(() -> {
            // Local
            milestoneDao.insert(milestone);

            // Cloud
            if (milestone.getRelationshipId() != null) {
                firestore.collection("relationships")
                        .document(milestone.getRelationshipId())
                        .collection("milestones")
                        .document(milestone.getId())
                        .set(milestone)
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to sync milestone", e));
            }
        });
    }
}
