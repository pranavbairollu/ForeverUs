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
public interface SongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Song song);

    @Update
    void update(Song song);

    @Delete
    void delete(Song song);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Song> songs);

    @Update
    void updateAll(List<Song> songs);

    @Delete
    void deleteAll(List<Song> songs);

    @Query("SELECT * FROM songs WHERE relationshipId = :relationshipId ORDER BY timestamp DESC")
    LiveData<List<Song>> getAllSongs(String relationshipId);

    @Query("SELECT * FROM songs WHERE relationshipId = :relationshipId")
    List<Song> getAllSongsSync(String relationshipId);

    @Query("SELECT * FROM songs WHERE relationshipId = :relationshipId AND youtubeUrl = :videoId LIMIT 1")
    Song findSongByVideoId(String relationshipId, String videoId);

    @Query("DELETE FROM songs WHERE relationshipId = :relationshipId")
    void deleteAllSongsForRelationship(String relationshipId);
}
