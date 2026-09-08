package com.scanly.data.storage

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
     */
    suspend fun uploadFile(
        file: File,
        mimeType: String,
        remotePath: String,
        progressCallback: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): StorageUploadResult

    /**
     * Deletes a previously uploaded file from the remote storage destination.
     */
    suspend fun deleteFile(remotePath: String): Boolean
}
