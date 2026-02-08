package com.example.foreverus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.concurrent.Executors;

public class MaintenanceReceiver extends BroadcastReceiver {
    private static final String TAG = "MaintenanceReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Daily Maintenance: Updating notification logic for year rollovers...");
        
        // Run on background thread to access DB
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Fetch all dates (Logic is self-contained in Repo/DAO usually, but here we can just query DAO directly)
                // We use the "Global" query added earlier for BootReceiver
                AppDatabase db = AppDatabase.getDatabase(context);
                SpecialDateDao dao = db.specialDateDao();
                
                // Re-schedule everything. 
                // This automatically handles "Year Rollover" because ".scheduleNotifications" calls ".getNextOccurrence()"
                // which uses "System.currentTimeMillis()".
                // If "Event Date" was Nov 6, and Today is Nov 7, getNextOccurrence now returns Nov 6 NEXT YEAR.
                NotificationScheduler.scheduleNotifications(context, dao.getAllSpecialDatesRaw());
                
                Log.d(TAG, "Maintenance Complete: Alarms refreshed.");
            } catch (Exception e) {
                Log.e(TAG, "Maintenance Failed", e);
            }
        });
    }
}
