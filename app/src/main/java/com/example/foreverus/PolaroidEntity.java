package com.example.foreverus;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "polaroids")
public class PolaroidEntity {
    @PrimaryKey
    @NonNull
    public String id;

    public String imagePath;
    public String caption;
    public long createdAt;

    // Nullable fields for future linking
    public String linkedMemoryId;
    public String linkedAdventureId;

    public PolaroidEntity(@NonNull String id, String imagePath, String caption, long createdAt) {
        this.id = id;
        this.imagePath = imagePath;
        this.caption = caption;
        this.createdAt = createdAt;
    }
}
