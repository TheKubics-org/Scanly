package com.thekubics.scanly.domain.service.cloud

import com.thekubics.scanly.domain.model.CloudDocument
import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.model.Page
import com.thekubics.scanly.domain.model.StorageQuota
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Result data class for batch or periodic sync operations.
 */
data class SyncProgress(
    val total: Int = 0,
    val completed: Int = 0,
    val currentDocumentTitle: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

/**
 * Pluggable service abstraction for secure cloud document storage and synchronization.
 */
interface CloudStorageService {

    /**
     * Uploads a document with its pages and optional PDF to cloud storage.
     */
    suspend fun uploadDocument(
        document: Document,
        pages: List<Page>,
        pdfFile: File? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<CloudDocument>

    /**
     * Downloads a cloud document and its associated pages/files to local storage.
     */
    suspend fun downloadDocument(
        cloudDocumentId: String,
        targetDir: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Document>

    /**
     * Deletes a document and all its remote assets from cloud storage.
     */
    suspend fun deleteCloudDocument(cloudDocumentId: String): Result<Unit>

    /**
     * Observes the list of cloud-stored documents for the current user.
     */
    fun listCloudDocuments(): Flow<List<CloudDocument>>

    /**
     * Observes current storage usage and quota distribution.
     */
    fun getStorageUsage(): Flow<StorageQuota>

    /**
     * Runs a sync pass for all pending offline queued documents.
     */
    suspend fun syncPendingDocuments(): Result<Int>
}
