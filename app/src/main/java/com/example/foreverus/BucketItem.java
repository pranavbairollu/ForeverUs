package com.example.foreverus;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.example.foreverus.TimelineRepository.SyncStatus;
import com.example.foreverus.TimelineRepository.Syncable;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;
import java.util.Objects;

@Entity(tableName = "bucket_items")
public class BucketItem implements Syncable {

    @PrimaryKey
    @NonNull
    private String id;
    private String title;
    private String description;
    private String type; // TRAVEL, MOVIE, GOAL, ETC
    private boolean isCompleted;
    private String imageUrl;
    private String relationshipId;

    @ServerTimestamp
    private Date timestamp;
    private Date completedDate;

    // Extensions
    private Date targetDate;
    private Double estimatedCost;
    private String currency; // "USD" or "INR"
    private int priorityOrder;

    private SyncStatus syncStatus;

    private boolean isDeleted; // For soft delete sync

    public BucketItem() {
        this.syncStatus = SyncStatus.SYNCED;
    }

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getRelationshipId() {
        return relationshipId;
    }

    public void setRelationshipId(String relationshipId) {
        this.relationshipId = relationshipId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Date getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Date completedDate) {
        this.completedDate = completedDate;
    }

    public Date getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(Date targetDate) {
        this.targetDate = targetDate;
    }

    public Double getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(Double estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public int getPriorityOrder() {
        return priorityOrder;
    }

    public void setPriorityOrder(int priorityOrder) {
        this.priorityOrder = priorityOrder;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    @Override
    public SyncStatus getSyncStatus() {
        return syncStatus;
    }

    @Override
    public void setSyncStatus(SyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BucketItem that = (BucketItem) o;
        return isCompleted == that.isCompleted &&
                id.equals(that.id) &&
                Objects.equals(title, that.title) &&
                Objects.equals(type, that.type) &&
                Objects.equals(description, that.description) &&
                Objects.equals(imageUrl, that.imageUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, description, type, isCompleted, imageUrl);
    }
}
