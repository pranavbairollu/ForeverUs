package com.example.foreverus;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PolaroidDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PolaroidEntity polaroid);

    @Query("SELECT * FROM polaroids ORDER BY createdAt DESC")
    LiveData<List<PolaroidEntity>> getAllPolaroids();

    @Query("SELECT * FROM polaroids WHERE id = :id")
    PolaroidEntity getPolaroidById(String id);
}
