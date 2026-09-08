package com.scanly.data.storage

import com.scanly.data.storage.drive.GoogleDriveStorageProvider
import com.scanly.data.storage.s3.S3StorageProvider
import com.scanly.data.storage.telegram.TelegramStorageProvider
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageProviderRegistry @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    fun createProvider(config: StorageConfig): StorageProvider {
        return when (config) {
            is StorageConfig.Telegram -> TelegramStorageProvider(config, okHttpClient)
            is StorageConfig.CloudflareR2 -> S3StorageProvider(config, okHttpClient)
            is StorageConfig.GoogleDrive -> GoogleDriveStorageProvider(config, okHttpClient)
        }
    }
}
