package com.docscanner.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.docscanner.app.data.local.converter.Converters
import com.docscanner.app.data.local.dao.CloudDocumentDao
import com.docscanner.app.data.local.dao.DocumentDao
import com.docscanner.app.data.local.dao.FolderDao
import com.docscanner.app.data.local.dao.PageDao
import com.docscanner.app.data.local.dao.SyncQueueDao
import com.docscanner.app.data.local.entity.CloudDocumentEntity
import com.docscanner.app.data.local.entity.DocumentEntity
import com.docscanner.app.data.local.entity.FolderEntity
import com.docscanner.app.data.local.entity.PageEntity
import com.docscanner.app.data.local.entity.SyncQueueEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DocumentEntity::class,
        PageEntity::class,
        FolderEntity::class,
        SyncQueueEntity::class,
        CloudDocumentEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun folderDao(): FolderDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun cloudDocumentDao(): CloudDocumentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to documents
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `cloudId` TEXT")
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `fileSize` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `lastSyncedAt` INTEGER")

                // Create sync_queue table and indexes
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, `actionType` TEXT NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL DEFAULT 0, `errorMessage` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_documentId` ON `sync_queue` (`documentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_status` ON `sync_queue` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_createdAt` ON `sync_queue` (`createdAt`)")

                // Create cloud_documents table and indexes
                db.execSQL("CREATE TABLE IF NOT EXISTS `cloud_documents` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `localDocumentId` TEXT, `title` TEXT NOT NULL, `fileType` TEXT NOT NULL, `pageCount` INTEGER NOT NULL, `fileSize` INTEGER NOT NULL, `thumbnailUrl` TEXT, `cloudFileUrl` TEXT, `uploadDate` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_documents_localDocumentId` ON `cloud_documents` (`localDocumentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_documents_userId` ON `cloud_documents` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_documents_uploadDate` ON `cloud_documents` (`uploadDate`)")
            }
        }
    }
}
