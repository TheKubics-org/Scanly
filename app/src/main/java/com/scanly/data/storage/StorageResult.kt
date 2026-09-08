package com.scanly.data.storage

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
