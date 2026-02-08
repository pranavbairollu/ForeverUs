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
public interface BucketDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BucketItem item);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<BucketItem> items);

    @Update
    void update(BucketItem item);

    @Delete
    void delete(BucketItem item);

    @Query("SELECT * FROM bucket_items WHERE relationshipId = :relationshipId AND isDeleted = 0 ORDER BY priorityOrder ASC, isCompleted ASC, timestamp DESC")
    LiveData<List<BucketItem>> getAllBucketItems(String relationshipId);

    @Query("SELECT * FROM bucket_items WHERE relationshipId = :relationshipId")
    List<BucketItem> getAllBucketItemsSync(String relationshipId);

    @Query("DELETE FROM bucket_items WHERE relationshipId = :relationshipId")
    void deleteAllForRelationship(String relationshipId);
}
