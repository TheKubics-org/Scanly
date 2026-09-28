package com.thekubics.scanly.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a queued synchronization operation for offline resilience.
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index("documentId"),
        Index("status"),
        Index("createdAt")
    ]
)
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val actionType: String, // UPLOAD, DOWNLOAD, DELETE
    val status: String = "PENDING", // PENDING, IN_PROGRESS, FAILED
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
