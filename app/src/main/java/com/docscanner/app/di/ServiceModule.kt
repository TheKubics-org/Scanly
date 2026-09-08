package com.docscanner.app.di

import com.docscanner.app.data.service.cloud.CloudStorageServiceImpl
import com.docscanner.app.domain.service.cloud.CloudStorageService
import com.scanly.data.vault.EncryptedStorageVaultRepositoryImpl
import com.scanly.data.vault.StorageVaultRepository
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
