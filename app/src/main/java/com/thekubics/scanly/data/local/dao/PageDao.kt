package com.thekubics.scanly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.thekubics.scanly.data.local.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageNumber ASC")
    fun getPagesByDocument(documentId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageNumber ASC")
    suspend fun getPagesForDocumentSync(documentId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageById(pageId: String): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<PageEntity>)

    @Update
    suspend fun update(page: PageEntity)

    @Query("UPDATE pages SET rotation = :rotation WHERE id = :pageId")
    suspend fun updateRotation(pageId: String, rotation: Int)

    @Query("UPDATE pages SET filter = :filter, brightness = :brightness, contrast = :contrast WHERE id = :pageId")
    suspend fun updateFilter(pageId: String, filter: String, brightness: Float, contrast: Float)

    @Query("UPDATE pages SET ocrText = :ocrText, ocrConfidence = :confidence WHERE id = :pageId")
    suspend fun updateOcrText(pageId: String, ocrText: String, confidence: Float)

    @Query("UPDATE pages SET pageNumber = :pageNumber WHERE id = :pageId")
    suspend fun updatePageNumber(pageId: String, pageNumber: Int)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun delete(pageId: String)

    @Query("DELETE FROM pages WHERE documentId = :documentId")
    suspend fun deleteByDocument(documentId: String)

    @Query("SELECT COUNT(*) FROM pages WHERE documentId = :documentId")
    suspend fun getPageCount(documentId: String): Int
}
