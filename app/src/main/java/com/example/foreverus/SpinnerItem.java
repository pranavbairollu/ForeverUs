package com.example.foreverus;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "spinner_items")
public class SpinnerItem {
    @PrimaryKey
    @NonNull
    private String id;

    private String categoryId;
    private String text;
    private String emoji;
    private int orderIndex;
    private boolean isActive;

    public SpinnerItem() {
        this.id = UUID.randomUUID().toString();
        this.isActive = true;
    }

    @androidx.room.Ignore
    public SpinnerItem(String categoryId, String text, String emoji, int orderIndex) {
        this.id = UUID.randomUUID().toString();
        this.categoryId = categoryId;
        this.text = text;
        this.emoji = emoji;
        this.orderIndex = orderIndex;
        this.isActive = true;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
