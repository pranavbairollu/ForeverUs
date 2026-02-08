package com.example.foreverus;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LetterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Letter letter);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Letter> letters);

    @Update
    void update(Letter letter);

    @Update
    void updateAll(List<Letter> letters);

    @Delete
    void deleteAll(List<Letter> letters);

    @Query("DELETE FROM letters WHERE letterId = :letterId")
    void deleteLetterById(String letterId);

    @Query("SELECT * FROM letters WHERE letterId IN (:letterIds)")
    List<Letter> getLetterByIds(List<String> letterIds);

    @Query("SELECT * FROM letters WHERE relationshipId = :relationshipId ORDER BY timestamp DESC")
    LiveData<List<Letter>> getAllLetters(String relationshipId);

    @Query("SELECT * FROM letters WHERE letterId = :letterId")
    LiveData<Letter> getLetterById(String letterId);

    @Query("SELECT * FROM letters WHERE letterId = :letterId")
    Letter getLetterByIdSync(String letterId);
}
