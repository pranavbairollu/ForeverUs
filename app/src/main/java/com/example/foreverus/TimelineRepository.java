package com.example.foreverus;

import com.google.firebase.Timestamp;

public interface TimelineRepository {

    enum SyncStatus {
        SYNCED,
        UNSYNCED
    }

    interface Callback {
        void onSuccess();
        void onError(Exception e);
    }

    interface TransactionCallback {
        void onTransactionSuccess(long newVersion);
        void onTransactionError(Exception e);
    }

    interface Syncable {
        String getId();
        SyncStatus getSyncStatus();
        void setSyncStatus(SyncStatus status);
    }

    interface TimelineItem {
        Timestamp getTimestamp();
    }
}
