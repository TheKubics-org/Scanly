package com.thekubics.scanly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.thekubics.scanly.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY createdAt DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    fun getFolderById(id: String): Flow<FolderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("UPDATE folders SET name = :newName WHERE id = :id")
    suspend fun updateName(id: String, newName: String)

    @Query("UPDATE folders SET color = :newColor WHERE id = :id")
    suspend fun updateColor(id: String, newColor: Long)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun delete(folderId: String)

    @Query("SELECT COUNT(*) FROM documents WHERE folderId = :folderId AND isTrashed = 0")
    fun getDocumentCountForFolder(folderId: String): Flow<Int>
}
