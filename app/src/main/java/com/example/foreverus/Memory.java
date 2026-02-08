package com.example.foreverus;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.firebase.Timestamp;
import java.util.Objects;

@Entity(tableName = "memories")
public class Memory implements TimelineRepository.Syncable, TimelineRepository.TimelineItem, MemoryListItem {

    @PrimaryKey
    @NonNull
    private String id = "";

    private String title;
    private String description;
    private String location;
    private String imageUrl;
    private String userId;
    private String mediaType = "image"; // Default to image for backward compatibility

    // FIX: Use Firebase Timestamp
    private Timestamp timestamp;

    private String relationshipId;
    private TimelineRepository.SyncStatus syncStatus;

    @Ignore
    public Memory(String title, Timestamp timestamp, String relationshipId) {
        this.title = title;
        this.timestamp = timestamp;
        this.relationshipId = relationshipId;
        this.syncStatus = TimelineRepository.SyncStatus.UNSYNCED;
    }

    public Memory() {
        this.syncStatus = TimelineRepository.SyncStatus.UNSYNCED;
    }

    @NonNull
    @Override
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    // New field for Voice Note support
    private String audioUrl;

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    @Override
    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public String getRelationshipId() {
        return relationshipId;
    }

    public void setRelationshipId(String relationshipId) {
        this.relationshipId = relationshipId;
    }

    @Override
    public TimelineRepository.SyncStatus getSyncStatus() {
        return syncStatus;
    }

    @Override
    public void setSyncStatus(TimelineRepository.SyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    @Override
    public int getItemType() {
        return TYPE_ITEM;
    }

    // New field for Multi-Image support
    private java.util.List<String> mediaUrls = new java.util.ArrayList<>();

    public java.util.List<String> getMediaUrls() {
        return mediaUrls;
    }

    public void setMediaUrls(java.util.List<String> mediaUrls) {
        this.mediaUrls = mediaUrls;
    }

    // Spinner Data
    private String spinnerCategoryId;
    private String spinnerItemId;

    public String getSpinnerCategoryId() {
        return spinnerCategoryId;
    }

    public void setSpinnerCategoryId(String spinnerCategoryId) {
        this.spinnerCategoryId = spinnerCategoryId;
    }

    public String getSpinnerItemId() {
        return spinnerItemId;
    }

    public void setSpinnerItemId(String spinnerItemId) {
        this.spinnerItemId = spinnerItemId;
    }
}
