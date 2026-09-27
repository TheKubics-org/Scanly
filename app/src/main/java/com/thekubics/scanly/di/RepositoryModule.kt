package com.thekubics.scanly.di

import com.thekubics.scanly.data.repository.DocumentRepositoryImpl
import com.thekubics.scanly.data.repository.FolderRepositoryImpl
import com.thekubics.scanly.data.repository.SettingsRepositoryImpl
import com.thekubics.scanly.domain.repository.DocumentRepository
import com.thekubics.scanly.domain.repository.FolderRepository
import com.thekubics.scanly.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindDocumentRepository(
        documentRepositoryImpl: DocumentRepositoryImpl
    ): DocumentRepository

    @Binds
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    abstract fun bindFolderRepository(
        folderRepositoryImpl: FolderRepositoryImpl
    ): FolderRepository
}
