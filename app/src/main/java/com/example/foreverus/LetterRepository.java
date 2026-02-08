package com.example.foreverus;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LetterRepository {

    private static final String TAG = "LetterRepository";

    private final LetterDao letterDao;
    private final FirebaseFirestore firestore;
    private final ExecutorService executorService;
    private ListenerRegistration firestoreListener;

    public LetterRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        this.letterDao = db.letterDao();
        this.firestore = FirebaseFirestore.getInstance();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<Resource<List<Letter>>> getAllLetters(String relationshipId) {
        MediatorLiveData<Resource<List<Letter>>> result = new MediatorLiveData<>();
        result.setValue(Resource.loading(null));

        LiveData<List<Letter>> localCache = letterDao.getAllLetters(relationshipId);

        result.addSource(localCache, data -> {
            Resource<List<Letter>> current = result.getValue();
            if (current == null || current.status != Resource.Status.ERROR) {
                result.setValue(Resource.success(data));
            } else {
                result.setValue(Resource.error(current.message, data));
            }
        });

        synchronizeFromFirestore(relationshipId, result);

        return result;
    }

    private void synchronizeFromFirestore(String relationshipId, MediatorLiveData<Resource<List<Letter>>> result) {
        if (firestoreListener != null)
            firestoreListener.remove();

        firestoreListener = firestore.collection("relationships")
                .document(relationshipId)
                .collection("letters")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Firestore listen failed.", e);
                        result.postValue(Resource.error(e.getMessage(),
                                result.getValue() != null ? result.getValue().data : null));
                        return;
                    }

                    if (snap != null) {
                        executorService.execute(() -> {
                            for (DocumentChange dc : snap.getDocumentChanges()) {
                                Letter letter = dc.getDocument().toObject(Letter.class);
                                letter.setLetterId(dc.getDocument().getId());
                                letter.setRelationshipId(relationshipId);

                                switch (dc.getType()) {
                                    case ADDED:
                                    case MODIFIED:
                                        letterDao.insert(letter);
                                        break;
                                    case REMOVED:
                                        letterDao.deleteLetterById(letter.getLetterId());
                                        break;
                                }
                            }
                        });
                    }
                });
    }

    public void cleanup() {
        if (firestoreListener != null)
            firestoreListener.remove();
        executorService.shutdown();
    }

    public LiveData<Resource<Letter>> getLetter(String relationshipId, String letterId) {
        MediatorLiveData<Resource<Letter>> result = new MediatorLiveData<>();
        result.setValue(Resource.loading(null));

        LiveData<Letter> local = letterDao.getLetterById(letterId);
        result.addSource(local, letter -> result.setValue(Resource.success(letter)));

        firestore.collection("relationships").document(relationshipId)
                .collection("letters").document(letterId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        result.postValue(Resource.error(e.getMessage(),
                                result.getValue() != null ? result.getValue().data : null));
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        executorService.execute(() -> {
                            Letter letter = snapshot.toObject(Letter.class);
                            if (letter != null) {
                                letter.setLetterId(snapshot.getId());
                                letter.setRelationshipId(relationshipId);
                                letterDao.insert(letter);
                            }
                        });
                    } else {
                        Log.d(TAG, "Current data: null");
                    }
                });

        return result;
    }

    public void markLetterAsOpened(Letter letter) {
        // 1. Optimistic Update (Local)
        executorService.execute(() -> {
            letter.setOpened(true);
            letterDao.update(letter);
        });

        // 2. Network Update
        DocumentReference ref = firestore.collection("relationships")
                .document(letter.getRelationshipId())
                .collection("letters").document(letter.getLetterId());

        ref.update("opened", true)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Letter opened status synced."))
                .addOnFailureListener(
                        e -> Log.w(TAG, "Error updating document (will sync later via listener/worker)", e));
    }

    public void updateReaction(String relationshipId, String letterId, String reaction) {
        // 1. Optimistic Update (Local)
        executorService.execute(() -> {
            Letter letter = letterDao.getLetterByIdSync(letterId);
            if (letter != null) {
                letter.setReaction(reaction);
                letterDao.update(letter);
            }
        });

        // 2. Network Update
        firestore.collection("relationships").document(relationshipId)
                .collection("letters").document(letterId)
                .update("reaction", reaction)
                .addOnFailureListener(e -> Log.e(TAG, "Failed reaction update", e));
    }

    public void updateReply(String relationshipId, String letterId, String replyContent) {
        // 1. Optimistic Update (Local)
        executorService.execute(() -> {
            Letter letter = letterDao.getLetterByIdSync(letterId);
            if (letter != null) {
                letter.setReplyContent(replyContent);
                letter.setReplyTimestamp(new java.util.Date()); // Use local time for immediate UI
                letterDao.update(letter);
            }
        });

        // 2. Network Update
        firestore.collection("relationships").document(relationshipId)
                .collection("letters").document(letterId)
                .update(
                        "replyContent", replyContent,
                        "replyTimestamp", com.google.firebase.Timestamp.now())
                .addOnFailureListener(e -> Log.e(TAG, "Failed reply update", e));
    }

    public void markAsRead(String relationshipId, String letterId) {
        // 1. Optimistic Update (Local)
        executorService.execute(() -> {
            Letter letter = letterDao.getLetterByIdSync(letterId);
            if (letter != null && letter.getReadTimestamp() == null) {
                letter.setReadTimestamp(new java.util.Date()); // Use local time for immediate UI
                letterDao.update(letter);
            }
        });

        // 2. Network Update
        firestore.collection("relationships").document(relationshipId)
                .collection("letters").document(letterId)
                .update("readTimestamp", com.google.firebase.Timestamp.now())
                .addOnFailureListener(e -> Log.e(TAG, "Failed read receipt", e));
    }

    public void sendLetter(Letter letter, final TimelineRepository.Callback callback) {
        executorService.execute(() -> {
            // 1. Save Locally First (Offline Safety)
            letter.setSyncStatus(TimelineRepository.SyncStatus.UNSYNCED);
            letterDao.insert(letter);

            // 2. Save to Firestore
            firestore.collection("relationships").document(letter.getRelationshipId())
                    .collection("letters").document(letter.getLetterId())
                    .set(letter)
                    .addOnSuccessListener(aVoid -> {
                        // 3. Success: Update Sync Status
                        executorService.execute(() -> {
                            letter.setSyncStatus(TimelineRepository.SyncStatus.SYNCED);
                            letterDao.insert(letter);
                        });
                        if (callback != null)
                            callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        // 4. Failure (Offline or Error)
                        // Data is safe locally as UNSYNCED.
                        if (callback != null)
                            callback.onError(e);
                    });
        });
    }

    public static void scheduleSync(android.content.Context context) {
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build();

        androidx.work.OneTimeWorkRequest syncRequest = new androidx.work.OneTimeWorkRequest.Builder(
                SyncLettersWorker.class)
                .setConstraints(constraints)
                .build();

        androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork("SyncLettersWork", androidx.work.ExistingWorkPolicy.KEEP, syncRequest);
    }
}
