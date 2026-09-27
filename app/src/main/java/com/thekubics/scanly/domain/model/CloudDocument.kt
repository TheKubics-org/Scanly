package com.thekubics.scanly.domain.model

/**
 * Represents a document stored or backed up in cloud storage.
 *
 * @property id Unique cloud identifier.
 * @property localDocumentId Corresponding local document ID, if synchronized locally.
 * @property title Document title.
 * @property fileType Primary file format (e.g., "PDF", "JPG", "PNG").
 * @property pageCount Total number of pages in the document.
 * @property fileSize Total size in bytes of the cloud document assets.
 * @property thumbnailUrl URL or local cached path for the document thumbnail.
 * @property cloudFileUrl Remote URL or storage reference for the primary document / PDF.
 * @property uploadDate Epoch timestamp when the document was uploaded or last updated in cloud.
 * @property syncStatus Current synchronization status.
 */
data class CloudDocument(
    val id: String,
    val localDocumentId: String?,
    val title: String,
    val fileType: String = "PDF",
    val pageCount: Int = 1,
    val fileSize: Long = 0L,
    val thumbnailUrl: String? = null,
    val cloudFileUrl: String? = null,
    val uploadDate: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
