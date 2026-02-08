package com.example.foreverus;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DailyReminderWorker extends Worker {

    private static final String TAG = "DailyReminderWorker";

    public DailyReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting daily reminder check");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "User not logged in, skipping check");
            return Result.success();
        }

        Context context = getApplicationContext();
        
        // Retrieve relationshipId from InputData passed by WorkManager
        String relationshipId = getInputData().getString("relationshipId");

        if (relationshipId == null) {
            // Fallback: Try shared prefs just in case, or fail
            relationshipId = context.getSharedPreferences("foreverus_prefs", Context.MODE_PRIVATE)
                    .getString("relationship_id", null);
        }

        if (relationshipId == null) {
             Log.d(TAG, "No relationship ID found in InputData or Prefs, skipping");
             return Result.success();
        }

        try {
             AppDatabase db = AppDatabase.getDatabase(context);
             // We need to use a synchronous call or block until LiveData returns (difficult in Worker)
             // Best to add a sync method to DAO. 
             // Note: Depending on DAO implementation, we might need to modify it first.
             // I will use `getAllSpecialDatesSync` assuming I will add it to DAO next.
             List<SpecialDate> allDates = db.specialDateDao().getAllSpecialDatesSync(relationshipId); 
             
             Calendar today = Calendar.getInstance();
             int todayMonth = today.get(Calendar.MONTH);
             int todayDay = today.get(Calendar.DAY_OF_MONTH);

             for (SpecialDate date : allDates) {
                 if (date.getDate() != null) {
                     Calendar eventDate = Calendar.getInstance();
                     eventDate.setTime(date.getDate());
                     
                     if (eventDate.get(Calendar.MONTH) == todayMonth && eventDate.get(Calendar.DAY_OF_MONTH) == todayDay) {
                         sendNotification(date.getTitle(), relationshipId);
                     }
                 }
             }

        } catch (Exception e) {
            Log.e(TAG, "Error checking special dates", e);
            // Don't fail the worker, just retry later or log
            return Result.success();
        }

        return Result.success();
    }

    private void sendNotification(String title, String relationshipId) {
        Context context = getApplicationContext();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Intent to open app
        Intent intent = new Intent(context, DashboardActivity.class);
        intent.putExtra("relationship_id", relationshipId); // Use literal or constant if accessible, using literal to be safe/lazy or import it? Better check imports. Import is static.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ForeverUsApplication.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher) // Fallback to launcher icon
                .setContentTitle("Special Day!")
                .setContentText("Today is " + title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (notificationManager != null) {
            try {
                notificationManager.notify(title.hashCode(), builder.build());
            } catch (SecurityException e) {
                Log.e(TAG, "Notification permission denied at runtime", e);
                // Fallback? Maybe just log since we can't show it.
            } catch (Exception e) {
                Log.e(TAG, "Notification failed", e);
            }
        }
    }
}
