package com.example.foreverus;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Memory memory);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] insertAll(List<Memory> memories);

    @Update
    int update(Memory memory);

    @Query("DELETE FROM memories WHERE id = :memoryId")
    void deleteMemoryById(String memoryId);

    @Query("SELECT * FROM memories WHERE relationshipId = :relationshipId ORDER BY timestamp DESC")
    LiveData<List<Memory>> getAllMemories(String relationshipId);

    @Query("DELETE FROM memories WHERE relationshipId = :relationshipId")
    void deleteAllForRelationship(String relationshipId);

    @Query("DELETE FROM memories")
    int deleteAll();
}
