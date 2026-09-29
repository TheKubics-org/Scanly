package com.thekubics.scanly.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [
        Index("folderId"),
        Index("title"),
        Index("createdAt"),
        Index("isTrashed"),
        Index("syncStatus")
    ]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String?,
    val pageCount: Int,
    val thumbnailPath: String,
    val pdfPath: String?,
    val ocrText: String?,
    val isEncrypted: Boolean,
    val isTrashed: Boolean,
    val trashedAt: Long?,
    val syncStatus: String = "LOCAL",
    val cloudId: String? = null,
    val fileSize: Long = 0L,
    val lastSyncedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
