
package com.example.foreverus;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface StoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Story story);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] insertAll(List<Story> stories);

    @Update
    int update(Story story);

    @Query("SELECT * FROM stories WHERE storyId = :storyId")
    Story getStoryByIdSync(String storyId);

    @Query("SELECT * FROM stories WHERE storyId = :storyId")
    LiveData<Story> getStoryById(String storyId);

    @Query("SELECT * FROM stories WHERE relationshipId = :relationshipId ORDER BY timestamp DESC")
    LiveData<List<Story>> getAllStories(String relationshipId);

    @Query("SELECT * FROM stories WHERE relationshipId = :relationshipId ORDER BY timestamp DESC")
    List<Story> getAllStoriesList(String relationshipId);

    @Query("DELETE FROM stories WHERE storyId = :storyId")
    int deleteStoryById(String storyId);

    @Query("DELETE FROM stories WHERE relationshipId = :relationshipId")
    int deleteStoriesByRelationshipId(String relationshipId);

    @Query("DELETE FROM stories")
    int deleteAll();
}
