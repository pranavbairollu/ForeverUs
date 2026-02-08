package com.example.foreverus;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.firebase.firestore.ServerTimestamp;
import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.Objects;

@Entity(tableName = "letters")
public class Letter implements TimelineRepository.Syncable, TimelineRepository.TimelineItem {

    @PrimaryKey
    @NonNull
    private String letterId;

    private String relationshipId;
    private String fromId;
    private String toId;
    private String title;
    private String message;
    private Date openDate;
    private boolean isOpened;
    private String theme; // "classic", "midnight", "romance"
    private String audioUrl;
    private String mediaUrl; // Image or Video URL
    
    // Intimacy Features
    private String reaction;
    private String replyContent;
    private Date replyTimestamp;
    private Date readTimestamp;

    // FIX: Firebase Timestamp
    @ServerTimestamp
    private Timestamp timestamp;

    private TimelineRepository.SyncStatus syncStatus;

    public Letter() {
        this.syncStatus = TimelineRepository.SyncStatus.UNSYNCED;
    }

    @Ignore
    public Letter(String relationshipId, String fromId, String toId, String title, String message, Date openDate) {
        this.relationshipId = relationshipId;
        this.fromId = fromId;
        this.toId = toId;
        this.title = title;
        this.message = message;
        this.openDate = openDate;
        this.isOpened = false;
        this.theme = "classic";
        this.syncStatus = TimelineRepository.SyncStatus.UNSYNCED;
    }

    @NonNull
    @Override
    public String getId() { return letterId; }
    public void setId(@NonNull String letterId) { this.letterId = letterId; }

    @NonNull
    public String getLetterId() { return letterId; }
    public void setLetterId(@NonNull String letterId) { this.letterId = letterId; }

    public String getRelationshipId() { return relationshipId; }
    public void setRelationshipId(String relationshipId) { this.relationshipId = relationshipId; }

    public String getFromId() { return fromId; }
    public void setFromId(String fromId) { this.fromId = fromId; }

    public String getToId() { return toId; }
    public void setToId(String toId) { this.toId = toId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Date getOpenDate() { return openDate; }
    public void setOpenDate(Date openDate) { this.openDate = openDate; }

    public boolean isOpened() { return isOpened; }
    public void setOpened(boolean opened) { isOpened = opened; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    
    public String getReaction() { return reaction; }
    public void setReaction(String reaction) { this.reaction = reaction; }
    
    public String getReplyContent() { return replyContent; }
    public void setReplyContent(String replyContent) { this.replyContent = replyContent; }
    
    public Date getReplyTimestamp() { return replyTimestamp; }
    public void setReplyTimestamp(Date replyTimestamp) { this.replyTimestamp = replyTimestamp; }
    
    public Date getReadTimestamp() { return readTimestamp; }
    public void setReadTimestamp(Date readTimestamp) { this.readTimestamp = readTimestamp; }

    @Override
    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    @Override
    public TimelineRepository.SyncStatus getSyncStatus() { return syncStatus; }
    @Override
    public void setSyncStatus(TimelineRepository.SyncStatus syncStatus) { this.syncStatus = syncStatus; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Letter letter = (Letter) o;
        return isOpened == letter.isOpened &&
                letterId.equals(letter.letterId) &&
                Objects.equals(relationshipId, letter.relationshipId) &&
                Objects.equals(fromId, letter.fromId) &&
                Objects.equals(toId, letter.toId) &&
                Objects.equals(title, letter.title) &&
                Objects.equals(message, letter.message) &&
                Objects.equals(openDate, letter.openDate) &&
                Objects.equals(timestamp, letter.timestamp) &&
                syncStatus == letter.syncStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(letterId, relationshipId, fromId, toId, title, message, openDate, isOpened, timestamp, syncStatus);
    }
}
