package com.thekubics.scanly.di

import com.thekubics.scanly.data.local.db.AppDatabase
import com.thekubics.scanly.data.local.dao.CloudDocumentDao
import com.thekubics.scanly.data.local.dao.DocumentDao
import com.thekubics.scanly.data.local.dao.FolderDao
import com.thekubics.scanly.data.local.dao.PageDao
import com.thekubics.scanly.data.local.dao.SyncQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()

    @Provides
    fun providePageDao(db: AppDatabase): PageDao = db.pageDao()

    @Provides
    fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideSyncQueueDao(db: AppDatabase): SyncQueueDao = db.syncQueueDao()

    @Provides
    fun provideCloudDocumentDao(db: AppDatabase): CloudDocumentDao = db.cloudDocumentDao()
}
