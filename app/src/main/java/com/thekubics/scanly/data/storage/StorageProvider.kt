package com.thekubics.scanly.data.storage

import java.io.File

/**
 * Supported BYOS storage destination types.
 */
enum class StorageProviderType {
    TELEGRAM,
    CLOUDFLARE_R2,
    GOOGLE_DRIVE
}

/**
 * Pluggable abstraction for decentralized BYOS storage endpoints.
 */
interface StorageProvider {
    val id: String
    val type: StorageProviderType
    val displayName: String
    val isEnabled: Boolean

    /**
     * Executes a lightweight diagnostic call to verify credentials and endpoint reachability.
     */
    suspend fun testConnection(): StorageTestResult

    /**
     * Streams and uploads a local file to the remote storage destination.
     *
     * [existingRemoteId] is the identifier returned by a previous successful upload
     * of the same document (`Document.cloudId`). Providers whose backend is
     * key-addressed (S3, R2) already overwrite on a stable [remotePath] and ignore
     * it. Providers whose API is create-only (Google Drive, Telegram) MUST use it
     * to replace the existing remote object instead of creating a second copy —
     * otherwise every re-save of an unchanged document leaves a duplicate behind.
     *
     * Implementations MUST treat a null or blank [existingRemoteId] as "first
     * upload" and fall back to a create.
     */
    suspend fun uploadFile(
        file: File,
        mimeType: String,
        remotePath: String,
        progressCallback: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null,
        existingRemoteId: String? = null
    ): StorageUploadResult

    /**
     * Streams a remote file down to [targetFile], reporting downloaded bytes.
     */
    suspend fun downloadFile(
        remotePath: String,
        targetFile: File,
        progressCallback: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null
    ): StorageDownloadResult

    /**
     * Deletes a previously uploaded file from the remote storage destination.
     */
    suspend fun deleteFile(remotePath: String): Boolean
}
