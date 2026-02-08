package com.example.foreverus;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class ForeverUsApplication extends Application {

    public static final String CHANNEL_ID = "special_dates_reminder_channel";

    public static androidx.media3.datasource.cache.SimpleCache simpleCache;

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applyTheme(this);
        createNotificationChannel();
        setupFirestorePersistence();
        setupExoPlayerCache();
        initCloudinary();
        scheduleSpecialDateCheck();
    }

    private void initCloudinary() {
        try {
            com.cloudinary.android.MediaManager.get();
        } catch (IllegalStateException e) {
            java.util.Map<String, Object> config = new java.util.HashMap<>();
            config.put("cloud_name", "dsuwyan5m");
            config.put("secure", true);
            com.cloudinary.android.MediaManager.init(this, config);
        }
    }

    private void setupFirestorePersistence() {
        com.google.firebase.firestore.FirebaseFirestoreSettings settings = 
            new com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build();
        com.google.firebase.firestore.FirebaseFirestore.getInstance().setFirestoreSettings(settings);
    }

    private void setupExoPlayerCache() {
        if (simpleCache == null) {
            try {
                java.io.File cacheDir = new java.io.File(getCacheDir(), "media");
                androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor evictor = 
                    new androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024); // 100MB
                androidx.media3.database.DatabaseProvider databaseProvider = 
                    new androidx.media3.database.StandaloneDatabaseProvider(this);
                simpleCache = new androidx.media3.datasource.cache.SimpleCache(cacheDir, evictor, databaseProvider);
            } catch (Exception e) {
                // Return safely; app will run without media cache
                // Logging recommended for debug builds
            }
        }
    }

    private void scheduleSpecialDateCheck() {
        // Robustness: Schedule Daily Maintenance (Handle Year Rollover) even if user never reboots.
        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(android.content.Context.ALARM_SERVICE);
        android.content.Intent maintenanceIntent = new android.content.Intent(this, MaintenanceReceiver.class);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
            this, 
            1001, 
            maintenanceIntent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        // Inexact repeating alarm (once per day)
        // This is safe to call repeatedly as FLAG_UPDATE_CURRENT will just update the existing one.
        if (am != null) {
            am.setInexactRepeating(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + android.app.AlarmManager.INTERVAL_DAY,
                android.app.AlarmManager.INTERVAL_DAY,
                pi
            );
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.special_dates_reminder_channel_name);
            String description = getString(R.string.special_dates_reminder_channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
