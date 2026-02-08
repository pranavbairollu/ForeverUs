package com.example.foreverus;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public abstract class SpinnerDao {

    @Query("SELECT * FROM spinner_categories ORDER BY isDefault DESC, name ASC")
    public abstract LiveData<List<SpinnerCategory>> getAllCategories();

    // Use LiveData for real-time updates in UI
    @Query("SELECT * FROM spinner_items WHERE categoryId = :categoryId AND isActive = 1 ORDER BY orderIndex ASC")
    public abstract LiveData<List<SpinnerItem>> getItemsForCategory(String categoryId);

    // Direct list for one-shot logic (e.g. spinning)
    @Query("SELECT * FROM spinner_items WHERE categoryId = :categoryId AND isActive = 1 ORDER BY orderIndex ASC")
    public abstract List<SpinnerItem> getItemsForCategorySync(String categoryId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertCategory(SpinnerCategory category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertItem(SpinnerItem item);

    @Query("DELETE FROM spinner_items WHERE id = :itemId")
    public abstract void deleteItem(String itemId);

    @Query("DELETE FROM spinner_categories WHERE id = :categoryId")
    public abstract void deleteCategory(String categoryId);

    @Query("DELETE FROM spinner_items WHERE categoryId = :categoryId")
    public abstract void deleteItemsByCategory(String categoryId);

    @androidx.room.Transaction
    public void deleteCategoryWithItems(String categoryId) {
        deleteItemsByCategory(categoryId);
        deleteCategory(categoryId);
    }

    @Query("SELECT COUNT(*) FROM spinner_categories")
    public abstract int getCategoryCount();
}
