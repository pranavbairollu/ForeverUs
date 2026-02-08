package com.example.foreverus;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.Objects;

@Entity(tableName = "songs")
public class Song implements TimelineRepository.Syncable {

    @PrimaryKey
    @NonNull
    private String id;
    private String title;
    private String artist;
    private String youtubeUrl; // We'll use YouTube for now, but this could be any URL
    private String addedBy;
    private String relationshipId;
    
    private TimelineRepository.SyncStatus syncStatus;

    @Ignore
    private String videoId;

    @ServerTimestamp
    private Date timestamp;

    public Song() {
        // Needed for Firestore
        this.syncStatus = TimelineRepository.SyncStatus.SYNCED; // Default for existing items
    }

    // Getters and Setters
    @NonNull
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

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getYoutubeUrl() {
        return youtubeUrl;
    }

    public void setYoutubeUrl(String youtubeUrl) {
        this.youtubeUrl = youtubeUrl;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public String getRelationshipId() {
        return relationshipId;
    }

    public void setRelationshipId(String relationshipId) {
        this.relationshipId = relationshipId;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        return id.equals(song.id) &&
                Objects.equals(title, song.title) &&
                Objects.equals(artist, song.artist) &&
                Objects.equals(youtubeUrl, song.youtubeUrl) &&
                Objects.equals(videoId, song.videoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, artist, youtubeUrl, videoId);
    }
}
