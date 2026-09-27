package com.thekubics.scanly.data.local.dao

import androidx.room.*
import com.thekubics.scanly.data.local.entity.CloudDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudDocumentDao {

    @Query("SELECT * FROM cloud_documents WHERE userId = :userId ORDER BY uploadDate DESC")
    fun getCloudDocuments(userId: String): Flow<List<CloudDocumentEntity>>

    @Query("SELECT * FROM cloud_documents WHERE userId = :userId ORDER BY uploadDate DESC")
    suspend fun getCloudDocumentsSync(userId: String): List<CloudDocumentEntity>

    @Query("SELECT * FROM cloud_documents WHERE id = :id")
    suspend fun getById(id: String): CloudDocumentEntity?

    @Query("SELECT * FROM cloud_documents WHERE localDocumentId = :localDocumentId")
    suspend fun getByLocalDocumentId(localDocumentId: String): CloudDocumentEntity?

    @Upsert
    suspend fun upsert(entity: CloudDocumentEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CloudDocumentEntity>)

    @Query("DELETE FROM cloud_documents WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cloud_documents WHERE localDocumentId = :localDocumentId")
    suspend fun deleteByLocalDocumentId(localDocumentId: String)

    @Query("DELETE FROM cloud_documents WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    @Query("SELECT SUM(fileSize) FROM cloud_documents WHERE userId = :userId")
    fun getTotalUsage(userId: String): Flow<Long?>

    @Query("SELECT SUM(fileSize) FROM cloud_documents WHERE userId = :userId AND fileType = :fileType")
    fun getUsageByType(userId: String, fileType: String): Flow<Long?>
}
