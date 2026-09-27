package com.thekubics.scanly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val documentCount: Int,
    val createdAt: Long
)
