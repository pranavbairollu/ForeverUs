package com.example.foreverus;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpecialDateRepository {

    private static final String TAG = "SpecialDateRepository";

    private final SpecialDateDao specialDateDao;
    private final FirebaseFirestore firestore;
    private final ExecutorService executorService;
    private final Context context;
    private final MutableLiveData<String> firestoreError = new MutableLiveData<>();
    private ListenerRegistration firestoreListener;

    public SpecialDateRepository(Context context) {
        this.context = context.getApplicationContext();
        AppDatabase db = AppDatabase.getDatabase(context);
        this.specialDateDao = db.specialDateDao();
        this.firestore = FirebaseFirestore.getInstance();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<SpecialDate>> getAllSpecialDates(String relationshipId) {
        synchronizeSpecialDates(relationshipId);
        LiveData<List<SpecialDate>> dates = specialDateDao.getAllSpecialDates(relationshipId);
        executorService.execute(() -> {
             NotificationScheduler.scheduleNotifications(context, specialDateDao.getAllSpecialDatesSync(relationshipId));
        });
        return dates;
    }

    public LiveData<String> getFirestoreError() {
        return firestoreError;
    }

    public ListenableFuture<List<SpecialDate>> getUpcomingSpecialDates(String relationshipId) {
        Calendar today = Calendar.getInstance();
        String month = String.format(Locale.US, "%02d", today.get(Calendar.MONTH) + 1);
        String day = String.format(Locale.US, "%02d", today.get(Calendar.DAY_OF_MONTH));
        return specialDateDao.getUpcomingSpecialDates(relationshipId, month, day);
    }

    private void synchronizeSpecialDates(String relationshipId) {
        if (firestoreListener != null) {
            firestoreListener.remove();
        }

        Query query = firestore.collection("relationships").document(relationshipId).collection("specialDates");

        // 0. Attempt to sync any local-only items first (Recovery) - Run ONCE on init, not every snapshot
        executorService.execute(() -> uploadUnsyncedSpecialDates(relationshipId));

        firestoreListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                firestoreError.postValue("Failed to load special dates: " + e.getMessage());
                return;
            }

                if (snapshots != null) {
                executorService.execute(() -> {
                    // 1. Get all local dates
                    List<SpecialDate> localDates = specialDateDao.getAllSpecialDatesSync(relationshipId);
                    Map<String, SpecialDate> localMap = new HashMap<>(); // Key: documentId (for synced items)
                    List<SpecialDate> optimisticDates = new ArrayList<>(); // Items with NULL docId

                    for (SpecialDate d : localDates) {
                        if (d.getDocumentId() != null) {
                            localMap.put(d.getDocumentId(), d);
                        } else {
                            optimisticDates.add(d);
                        }
                    }

                    List<SpecialDate> toInsertOrUpdate = new ArrayList<>();
                    List<String> validIds = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String docId = doc.getId();
                        validIds.add(docId);
                        
                        SpecialDate newDate = doc.toObject(SpecialDate.class);
                        newDate.setDocumentId(docId);
                        newDate.setRelationshipId(relationshipId);

                        // Match Logic:
                        if (localMap.containsKey(docId)) {
                            // Case A: Strict Match (We already know this doc)
                            newDate.setId(localMap.get(docId).getId());
                        } else {
                            // Case B: Fuzzy Match (Is this one of our optimistic items?)
                            // We look for a local item with SAME Title and Date but NULL DocId
                            for (SpecialDate opt : optimisticDates) {
                                if (opt.getTitle().equals(newDate.getTitle()) &&
                                    isSameDate(opt.getDate(), newDate.getDate())) {
                                    
                                    newDate.setId(opt.getId()); // Claim the local ID!
                                    // Remove from optimistic list so we don't match it again
                                    optimisticDates.remove(opt); 
                                    Log.d(TAG, "Listener claimed optimistic copy for: " + docId);
                                    break;
                                }
                            }
                        }

                        toInsertOrUpdate.add(newDate);
                    }

                    // 2. Update/Insert valid items
                    specialDateDao.insertAll(toInsertOrUpdate);

                    // 3. Delete items not in Firestore snapshot (Sync deletions)
                    // We only delete items that HAVE a DocId but are not in the snapshot.
                    // Optimistic items (still NULL DocId) are left alone (waiting for their turn).
                    for (SpecialDate local : localDates) {
                        if (local.getDocumentId() != null && !validIds.contains(local.getDocumentId())) {
                            specialDateDao.delete(local);
                            NotificationScheduler.cancelNotifications(context, local);
                        }
                    }
                    
                    // Re-schedule everything to be safe after sync
                    NotificationScheduler.scheduleNotifications(context, specialDateDao.getAllSpecialDatesSync(relationshipId));
                });
            }
        });
    }

    private boolean isSameDate(java.util.Date d1, java.util.Date d2) {
        if (d1 == null || d2 == null) return false;
        Calendar c1 = Calendar.getInstance(); c1.setTime(d1);
        Calendar c2 = Calendar.getInstance(); c2.setTime(d2);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH) &&
               c1.get(Calendar.DAY_OF_MONTH) == c2.get(Calendar.DAY_OF_MONTH);
    }

    private void uploadUnsyncedSpecialDates(String relationshipId) {
        List<SpecialDate> unsynced = specialDateDao.getUnsyncedSpecialDates();
        for (SpecialDate date : unsynced) {
            // FINAL SAFETY CHECK: Re-fetch to ensure Listener didn't just sync it (Fuzzy Match) a millisecond ago.
            SpecialDate current = specialDateDao.getSpecialDateById(date.getId());
            if (current == null || current.getDocumentId() != null) {
                Log.d(TAG, "Skipping offline recovery for " + date.getTitle() + " - already synced or deleted.");
                continue;
            }

            // Ensure relationship ID is set just in case
            date.setRelationshipId(relationshipId);
            
            firestore.collection("relationships").document(relationshipId).collection("specialDates")
                    .add(date)
                    .addOnSuccessListener(documentReference -> executorService.execute(() -> {
                        // ZOMBIE CHECK: Verify it wasn't deleted locally while recovering
                        SpecialDate freshCheck = specialDateDao.getSpecialDateById(date.getId());
                        if (freshCheck == null) {
                            Log.d(TAG, "Zombie Recovery Avoided: User deleted date during sync. Cleaning up remote.");
                            documentReference.delete();
                            return;
                        }

                        // One more check in case listener updated it WHILE we were uploading?
                        // If listener updated it, we just overwrote the DocID.
                        // But listener update meant it found a cloud match.
                        // We just created a NEW cloud doc. 
                        // So we have 2 cloud docs. 
                        // But we updated our local to point to ours.
                        // Listener saw Cloud A. We made Cloud B.
                        // This path is extremely rare. 
                        // The 'current.getDocumentId() != null' check above handles 99% of it.
                        
                        date.setDocumentId(documentReference.getId());
                        specialDateDao.insert(date);
                        Log.d(TAG, "Recovered/Synced offline special date: " + date.getTitle());
                    }))
                    .addOnFailureListener(e -> Log.w(TAG, "Failed to recover offline special date", e));
        }
    }

    public void addSpecialDate(String relationshipId, SpecialDate specialDate) {
        executorService.execute(() -> {
            // 0. Ensure Relationship ID is set
            specialDate.setRelationshipId(relationshipId);
            Log.d(TAG, "Attempting to add Special Date to Firestore: Path=relationships/" + relationshipId + "/specialDates");

            // 1. Save locally first (Optimistic) for instant UI feedback
            // Generate a temporary document ID if missing for local tracking
            if (specialDate.getDocumentId() == null) {
                // Determine a temporary ID logic or just let Room handle the PK.
                // Room will auto-generate 'id' (long). 'documentId' (String) is for Firestore.
                // For optimistic UI, we can just insert it.
                // Determine a temporary ID logic or just let Room handle the PK.
                // Room will auto-generate 'id' (long). 'documentId' (String) is for Firestore.
                // For optimistic UI, we can just insert it.
            }
            long newId = specialDateDao.insert(specialDate);
            specialDate.setId(newId); // CRITICAL: Update memory object so Firestore gets the real ID!

            // 2. Add to Firestore
            firestore.collection("relationships").document(relationshipId).collection("specialDates")
                    .add(specialDate)
                    .addOnSuccessListener(documentReference -> {
                         Log.d(TAG, "SUCCESS: Written to Firestore with ID: " + documentReference.getId());
                        // 3. Update with the real ID from Firestore
                        // 3. Update with the real ID from Firestore
                        executorService.execute(() -> {
                            // The Snapshot Listener might have already caught this and updated the record.
                            // We check the record by its Local ID (PK).
                            // If it still has null DocumentID, we update it.
                            // If it has a DocumentID, we assume the Listener handled it or we handle it now.
                            
                            // Important: We must NOT delete it simply because it's "synced". 
                            // The goal is ensuring 1 record exists with [PK=X, DocID=Y].
                            
                            try {
                                // Fetch the specific record we inserted purely by reference if possible, or by ID
                                // We inserted 'specialDate'. It has an ID now (if Room updated the object).
                                if (specialDate.getId() != 0) {
                                     // RACE CONDITION FIX: Check if user deleted it while we were uploading
                                     SpecialDate latestLocal = specialDateDao.getSpecialDateById(specialDate.getId());
                                     
                                     if (latestLocal == null) {
                                         // User deleted it! Abort update.
                                         Log.d(TAG, "Zombie Avoided: User deleted date during upload. Deleting remote copy.");
                                         // Cleanup remote
                                         documentReference.delete(); 
                                         return;
                                     }

                                     // Safe to update
                                     latestLocal.setDocumentId(documentReference.getId());
                                     specialDateDao.insert(latestLocal); // Update existing
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating local special date with ID", e);
                            }
                        });
                        Log.d(TAG, "Special Date added to Firestore: " + documentReference.getId());
                    })
                    .addOnFailureListener(e -> {
                         Log.w(TAG, "Error adding special date: " + e.getMessage());
                         // Optional: Mark as "Sync Failed" in local DB if we had a status field
                    });
        });
    }

    public void deleteSpecialDate(String relationshipId, SpecialDate specialDate) {
        executorService.execute(() -> {
            if (specialDate.getDocumentId() == null) {
                // Local only (wasn't synced yet), just delete from Room
                specialDateDao.delete(specialDate);
                NotificationScheduler.cancelNotifications(context, specialDate); // Cancel specific
                Log.d(TAG, "Special Date deleted locally (was not synced)");
                return;
            }
            
            firestore.collection("relationships").document(relationshipId).collection("specialDates")
                    .document(specialDate.getDocumentId())
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        // Snapshot listener handles removal
                         Log.d(TAG, "Special Date deleted from Firestore");
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Error deleting special date", e);
                         // Again, suppress offline errors as Firestore queues deletions.
                    });
        });
    }

    public void cleanup() {
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
    }
}
