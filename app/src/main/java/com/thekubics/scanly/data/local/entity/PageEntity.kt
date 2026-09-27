package com.thekubics.scanly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("documentId")
    ]
)
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageNumber: Int,
    val originalImagePath: String,
    val processedImagePath: String,
    val thumbnailPath: String,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val filter: String,
    val brightness: Float,
    val contrast: Float,
    val ocrText: String?,
    val ocrConfidence: Float?,
    val createdAt: Long
)
