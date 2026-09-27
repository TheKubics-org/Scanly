package com.thekubics.scanly.domain.repository

import com.thekubics.scanly.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun getAllFolders(): Flow<List<Folder>>
    fun getFolderById(id: String): Flow<Folder?>
    suspend fun createFolder(name: String, color: Long)
    suspend fun renameFolder(id: String, newName: String)
    suspend fun changeFolderColor(id: String, color: Long)
    suspend fun deleteFolder(id: String)
}
