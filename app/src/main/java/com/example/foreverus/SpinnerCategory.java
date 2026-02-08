package com.example.foreverus;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "spinner_categories")
public class SpinnerCategory {
    @PrimaryKey
    @NonNull
    private String id;

    private String name;
    private boolean isDefault;

    public SpinnerCategory() {
        this.id = UUID.randomUUID().toString();
    }

    @androidx.room.Ignore
    public SpinnerCategory(String name, boolean isDefault) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.isDefault = isDefault;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }
}
