package com.example.foreverus;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

@Dao
public interface SpecialDateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SpecialDate specialDate);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SpecialDate> specialDates);

    @Delete
    int delete(SpecialDate specialDate);

    // Note: The date is stored as a long (timestamp). We use strftime to extract parts.
    // The date/1000 is to convert from milliseconds (Java) to seconds (Unix epoch).
    @Query("SELECT * FROM special_dates WHERE relationshipId = :relationshipId ORDER BY strftime('%m', date / 1000, 'unixepoch'), strftime('%d', date / 1000, 'unixepoch') ASC")
    LiveData<List<SpecialDate>> getAllSpecialDates(String relationshipId);

    // Month and day should be formatted as two-digit strings, e.g., "03" for March, "14" for the 14th.
    @Query("SELECT * FROM special_dates WHERE relationshipId = :relationshipId AND strftime('%m', date / 1000, 'unixepoch') = :month AND strftime('%d', date / 1000, 'unixepoch') = :day")
    List<SpecialDate> getSpecialDatesForDate(String relationshipId, String month, String day);

    @Query("SELECT * FROM special_dates WHERE relationshipId = :relationshipId AND strftime('%m', date / 1000, 'unixepoch') = :month AND strftime('%d', date / 1000, 'unixepoch') = :day")
    ListenableFuture<List<SpecialDate>> getUpcomingSpecialDates(String relationshipId, String month, String day);

    @Query("SELECT * FROM special_dates WHERE documentId = :documentId LIMIT 1")
    SpecialDate getSpecialDateByDocumentId(String documentId);
    
    @Query("SELECT * FROM special_dates")
    List<SpecialDate> getAllSpecialDatesRaw();

    @Query("SELECT * FROM special_dates WHERE documentId IS NULL")
    List<SpecialDate> getUnsyncedSpecialDates();

    @Query("SELECT * FROM special_dates WHERE id = :id")
    SpecialDate getSpecialDateById(long id);

    @Query("SELECT * FROM special_dates WHERE relationshipId = :relationshipId")
    List<SpecialDate> getAllSpecialDatesSync(String relationshipId);
}
