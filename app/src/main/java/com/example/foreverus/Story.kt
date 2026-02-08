package com.example.foreverus

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

@Entity(tableName = "stories")
data class Story(
    @PrimaryKey
    @JvmField var storyId: String = "",
    var title: String = "",
    var content: String = "",
    var lastEditedBy: String = "",
    var relationshipId: String = "",
    var version: Long = 0L,
    @JvmField var syncStatus: TimelineRepository.SyncStatus = TimelineRepository.SyncStatus.UNSYNCED,

    @JvmField var timestamp: Timestamp? = null
) : TimelineRepository.Syncable, TimelineRepository.TimelineItem {

    @Exclude
    override fun getId(): String = storyId

    override fun getSyncStatus(): TimelineRepository.SyncStatus = syncStatus
    override fun setSyncStatus(status: TimelineRepository.SyncStatus) {
        syncStatus = status
    }

    @Exclude
    override fun getTimestamp(): Timestamp? {
        return timestamp
    }

    fun getAuthorId(): String = lastEditedBy
    fun getIsEdited(): Boolean = version > 0
}
