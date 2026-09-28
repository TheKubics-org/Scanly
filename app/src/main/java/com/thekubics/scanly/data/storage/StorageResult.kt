package com.thekubics.scanly.data.storage

import java.io.File

/**
 * Result of a connection diagnostic test to a BYOS storage provider.
 */
data class StorageTestResult(
    val isSuccess: Boolean,
    val message: String,
    val latencyMs: Long = 0L,
    val details: String? = null
)

/**
 * Result of an upload operation to a BYOS storage provider.
 */
data class StorageUploadResult(
    val isSuccess: Boolean,
    val remoteId: String? = null,
    val remoteUrl: String? = null,
    val bytesUploaded: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Result of a download operation from a BYOS storage provider.
 */
data class StorageDownloadResult(
    val isSuccess: Boolean,
    val localFile: File? = null,
    val bytesDownloaded: Long = 0L,
    val errorMessage: String? = null
)
