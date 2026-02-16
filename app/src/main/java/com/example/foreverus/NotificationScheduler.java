package com.example.foreverus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class NotificationScheduler {

    private static final String PREF_ALARMS = "active_alarms";
    private static final String PREF_ID_COUNTER = "notification_id_counter";

    // Data class for internal use
    private static class AlarmData {
        String letterId;
        long triggerTime;
        int notificationId;
        String senderName;
        String relationshipId;

        AlarmData(String lid, long time, int nid, String sender, String relId) {
            letterId = lid;
            triggerTime = time;
            notificationId = nid;
            senderName = sender;
            relationshipId = relId;
        }
    }

    public static void scheduleAlarm(Context context, String letterId, long triggerTime, String senderName,
            String relationshipId) {
        if (triggerTime <= System.currentTimeMillis())
            return;

        SharedPreferences prefs = context.getSharedPreferences("foreverus_alarms", Context.MODE_PRIVATE);

        int notificationId = -1;
        JSONObject targetAlarm = null;

        // 1. Resolve ID Strategy (Mapping: letterId -> notificationId)
        try {
            String existingJson = prefs.getString(PREF_ALARMS, "[]");
            JSONArray alarms = new JSONArray(existingJson);

            // Check for existing mapping
            for (int i = 0; i < alarms.length(); i++) {
                JSONObject obj = alarms.getJSONObject(i);
                if (obj.getString("letterId").equals(letterId)) {
                    targetAlarm = obj;
                    if (obj.has("notificationId")) {
                        notificationId = obj.getInt("notificationId");
                    }
                    break;
                }
            }

            // If new letter, generate NEW unique ID
            if (targetAlarm == null) {
                targetAlarm = new JSONObject();
                // Generate next ID
                int counter = prefs.getInt(PREF_ID_COUNTER, 1000);
                notificationId = counter + 1;
                prefs.edit().putInt(PREF_ID_COUNTER, notificationId).apply();

                targetAlarm.put("letterId", letterId);
                targetAlarm.put("notificationId", notificationId);
                alarms.put(targetAlarm); // Add to list
            } else if (notificationId == -1) {
                // Fallback for corrupt data: assign new ID
                int counter = prefs.getInt(PREF_ID_COUNTER, 1000);
                notificationId = counter + 1;
                prefs.edit().putInt(PREF_ID_COUNTER, notificationId).apply();
                targetAlarm.put("notificationId", notificationId);
            }

            // 2. Update Metadata
            targetAlarm.put("triggerTime", triggerTime);
            targetAlarm.put("senderName", senderName);
            targetAlarm.put("relationshipId", relationshipId);

            // 3. Persist Mapping
            prefs.edit().putString(PREF_ALARMS, alarms.toString()).apply();

        } catch (JSONException e) {
            e.printStackTrace();
            return; // Fail safe
        }

        // 4. Schedule with Stable ID
        performSchedule(context, notificationId, letterId, triggerTime, senderName, relationshipId);
    }

    public static void rescheduleAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("foreverus_alarms", Context.MODE_PRIVATE);
        String json = prefs.getString(PREF_ALARMS, "[]");
        try {
            JSONArray alarms = new JSONArray(json);
            JSONArray activeAlarms = new JSONArray(); // Filter out old ones

            for (int i = 0; i < alarms.length(); i++) {
                JSONObject obj = alarms.getJSONObject(i);
                long time = obj.getLong("triggerTime");
                if (time > System.currentTimeMillis()) {
                    performSchedule(context,
                            obj.getInt("notificationId"),
                            obj.getString("letterId"),
                            time,
                            obj.optString("senderName", "Partner"),
                            obj.optString("relationshipId"));
                    activeAlarms.put(obj);
                }
            }
            // Update prefs (Clean up expired)
            prefs.edit().putString(PREF_ALARMS, activeAlarms.toString()).apply();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static void performSchedule(Context context, int notificationId, String letterId, long time, String sender,
            String relationshipId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("NotificationScheduler", "Cannot schedule exact alarm");
                return; // Or fallback to inexact
            }
        }

        Intent intent = new Intent(context, LetterUnlockReceiver.class);
        intent.putExtra("letterId", letterId);
        intent.putExtra("senderName", sender);
        intent.putExtra("relationshipId", relationshipId);
        intent.putExtra("notificationId", notificationId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId, // Use Stable Integer ID
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

}
