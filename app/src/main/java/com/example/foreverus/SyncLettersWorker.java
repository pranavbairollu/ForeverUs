package com.example.foreverus;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class SyncLettersWorker extends Worker {

    private static final String TAG = "SyncLettersWorker";
    private final LetterDao letterDao;
    private final FirebaseFirestore firestore;

    public SyncLettersWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        AppDatabase db = AppDatabase.getDatabase(context);
        this.letterDao = db.letterDao();
        this.firestore = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting sync of pending letters...");
        List<Letter> pendingLetters = letterDao.getUnsyncedLetters();

        if (pendingLetters == null || pendingLetters.isEmpty()) {
            Log.d(TAG, "No pending letters found.");
            return Result.success();
        }

        Log.d(TAG, "Found " + pendingLetters.size() + " pending letters.");
        boolean allSuccess = true;

        for (Letter letter : pendingLetters) {
            boolean success = uploadLetter(letter);
            if (!success) {
                allSuccess = false;
            }
        }

        return allSuccess ? Result.success() : Result.retry();
    }

    private boolean uploadLetter(Letter letter) {
        try {
            // Use Tasks.await to keep execution on the Worker thread (background)
            // This prevents callbacks from jumping to Main Thread and causing Room crashes.
            com.google.android.gms.tasks.Tasks.await(
                    firestore.collection("relationships").document(letter.getRelationshipId())
                            .collection("letters").document(letter.getLetterId())
                            .set(letter, SetOptions.merge()));

            Log.d(TAG, "Successfully synced letter: " + letter.getLetterId());

            // Now safe to access DB because we are still on the Worker thread
            letter.setSyncStatus(TimelineRepository.SyncStatus.SYNCED);
            letterDao.update(letter);

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to sync letter: " + letter.getLetterId(), e);
            return false;
        }
    }
}
