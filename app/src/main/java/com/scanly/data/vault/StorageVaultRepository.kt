package com.scanly.data.vault

import com.scanly.data.storage.StorageConfig
import com.scanly.data.storage.StorageProvider
import kotlinx.coroutines.flow.Flow

interface StorageVaultRepository {
    suspend fun getAllConfigs(): List<StorageConfig>
    suspend fun getConfigById(id: String): StorageConfig?
    suspend fun saveConfig(config: StorageConfig)
    suspend fun deleteConfig(id: String)
    suspend fun getActiveProviderId(): String?
    suspend fun setActiveProviderId(id: String?)
    suspend fun getActiveProvider(): StorageProvider?
    suspend fun getProviderById(id: String): StorageProvider?
    fun observeConfigs(): Flow<List<StorageConfig>>
    fun observeActiveConfig(): Flow<StorageConfig?>
}
