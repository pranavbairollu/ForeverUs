package com.example.foreverus;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MilestoneDao {
    @Query("SELECT * FROM milestones WHERE relationshipId = :relationshipId ORDER BY completedDate DESC")
    LiveData<List<Milestone>> getMilestones(String relationshipId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Milestone milestone);

    @Query("SELECT SUM(cost) FROM milestones WHERE relationshipId = :relationshipId")
    LiveData<Double> getTotalCost(String relationshipId);

    @Query("SELECT COUNT(*) FROM milestones WHERE relationshipId = :relationshipId")
    LiveData<Integer> getMilestoneCount(String relationshipId);
}
