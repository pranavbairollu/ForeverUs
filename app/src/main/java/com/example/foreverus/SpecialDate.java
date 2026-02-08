package com.example.foreverus;

import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

@Entity(tableName = "special_dates")
public class SpecialDate implements Comparable<SpecialDate> {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "documentId")
    private String documentId;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "date")
    private Date date;

    @ColumnInfo(name = "relationshipId")
    private String relationshipId;


    public SpecialDate() {
        // Public no-arg constructor needed for Room and Firestore
    }

    @Ignore
    public SpecialDate(String title, Date date) {
        this.title = title;
        this.date = date;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getRelationshipId() {
        return relationshipId;
    }

    public void setRelationshipId(String relationshipId) {
        this.relationshipId = relationshipId;
    }

    // --- Logic for Special Dates 2.0 ---

    /**
     * Calculates the next occurrence of this special date.
     * If the date for this year has passed, it returns the date for next year.
     * Preserves the time (though usually we ignore time for dates).
     */
    public Date getNextOccurrence() {
        if (date == null) return new Date();

        Calendar today = Calendar.getInstance();
        Calendar eventDate = Calendar.getInstance();
        eventDate.setTime(date);

        Calendar nextEvent = Calendar.getInstance();
        nextEvent.set(Calendar.MONTH, eventDate.get(Calendar.MONTH));
        nextEvent.set(Calendar.DAY_OF_MONTH, eventDate.get(Calendar.DAY_OF_MONTH));
        // Reset time to start of day for accurate comparison
        nextEvent.set(Calendar.HOUR_OF_DAY, 9);
        nextEvent.set(Calendar.MINUTE, 0);
        nextEvent.set(Calendar.SECOND, 0);
        nextEvent.set(Calendar.MILLISECOND, 0);

        // Reset today to start of day
        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);

        if (nextEvent.before(todayStart)) {
            nextEvent.add(Calendar.YEAR, 1);
        }
        return nextEvent.getTime();
    }

    /**
     * Returns the number of days remaining until the next occurrence.
     * 0 = Today.
     */
    public long getDaysRemaining() {
        Date next = getNextOccurrence();
        Calendar today = Calendar.getInstance();
        
        // Reset today to midnight to avoid partial day issues
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        long diff = next.getTime() - today.getTimeInMillis();
        return java.util.concurrent.TimeUnit.DAYS.convert(diff, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Generates a stable unique ID for notifications based on the DB Primary Key.
     * Multiplied by 100 to allow space for multiple notification types per event
     * (e.g., Day Of, Week Before) without collisions between different events.
     */
    public int getNotificationId() {
        return (int) id * 100;
    }

    @Override
    public int compareTo(@NonNull SpecialDate o) {
        return Long.compare(this.getDaysRemaining(), o.getDaysRemaining());
    }
}
