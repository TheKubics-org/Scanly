package com.thekubics.scanly.domain.model

/**
 * Represents a folder used to organize [Document]s.
 *
 * @property id Unique identifier for the folder.
 * @property name User-assigned name of the folder.
 * @property color Color code associated with the folder, default is Google Blue.
 * @property documentCount Number of documents contained in this folder.
 * @property createdAt Timestamp when the folder was created.
 */
data class Folder(
    val id: String,
    val name: String,
    val color: Long = 0xFF4285F4,
    val documentCount: Int = 0,
    val createdAt: Long
)
