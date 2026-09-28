package com.thekubics.scanly.domain.model

/**
 * Represents a document in the application.
 *
 * @property id Unique identifier for the document.
 * @property title Title of the document.
 * @property folderId ID of the folder containing this document, if any.
 * @property pageCount Number of pages in the document.
 * @property thumbnailPath Path to the thumbnail image of the document.
 * @property ocrText Extracted text from all pages in the document.
 * @property isEncrypted Whether the document is encrypted locally.
 * @property isTrashed Whether the document is moved to trash.
 * @property trashedAt Timestamp when the document was moved to trash.
 * @property syncStatus Current cloud sync status.
 * @property cloudId Remote cloud identifier if backed up.
 * @property fileSize Estimated file size in bytes.
 * @property lastSyncedAt Timestamp of last successful sync.
 * @property createdAt Timestamp when the document was created.
 * @property updatedAt Timestamp when the document was last updated.
 */
data class Document(
    val id: String,
    val title: String,
    val folderId: String? = null,
    val pageCount: Int,
    val thumbnailPath: String,
    val ocrText: String? = null,
    val isEncrypted: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL,
    val cloudId: String? = null,
    val fileSize: Long = 0L,
    val lastSyncedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
