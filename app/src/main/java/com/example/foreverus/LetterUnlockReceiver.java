package com.example.foreverus;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class LetterUnlockReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "letter_unlock_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        String letterId = intent.getStringExtra("letterId");
        String senderName = intent.getStringExtra("senderName"); // Optional
        String relationshipId = intent.getStringExtra("relationshipId");

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Safe check for Channel (Already created in App, but robust check)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Letter Unlocks", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(context, DashboardActivity.class); // Or LetterViewActivity directly via DeepLink?
        // Let's go to Dashboard which handles nav, or straight to LetterView if we can.
        // Direct to LetterView might be better IF we have the Letter Object or ID.
        // For robustness, go to Dashboard/Letters List.
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // We can pass extras to help nav
        
        PendingIntent pendingIntent = PendingIntent.getActivity(context, letterId.hashCode(), openIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_email_24) // Fallback to standard email icon
                .setContentTitle("A Letter Unlocked!")
                .setContentText("A letter from " + (senderName != null ? senderName : "your partner") + " is now open.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(letterId.hashCode(), builder.build());
    }
}
