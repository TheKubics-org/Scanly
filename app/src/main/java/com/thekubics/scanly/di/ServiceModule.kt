package com.thekubics.scanly.di

import com.thekubics.scanly.data.service.cloud.CloudStorageServiceImpl
import com.thekubics.scanly.domain.service.cloud.CloudStorageService
import com.thekubics.scanly.data.vault.EncryptedStorageVaultRepositoryImpl
import com.thekubics.scanly.data.vault.StorageVaultRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindCloudStorageService(
        impl: CloudStorageServiceImpl
    ): CloudStorageService

    @Binds
    @Singleton
    abstract fun bindStorageVaultRepository(
        impl: EncryptedStorageVaultRepositoryImpl
    ): StorageVaultRepository
}
