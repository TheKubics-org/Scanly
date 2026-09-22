package com.docscanner.app.data.repository

import com.docscanner.app.data.local.dao.DocumentDao
import com.docscanner.app.data.local.dao.FolderDao
import com.docscanner.app.data.mapper.toDomain
import com.docscanner.app.data.mapper.toEntity
import com.docscanner.app.domain.model.Folder
import com.docscanner.app.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    private val documentDao: DocumentDao
) : FolderRepository {
    
    override fun getAllFolders(): Flow<List<Folder>> {
        return folderDao.getAllFolders().map { entities -> 
            entities.map { it.toDomain() }
        }
    }

    override fun getFolderById(id: String): Flow<Folder?> {
        return folderDao.getFolderById(id).map { it?.toDomain() }
    }

    override suspend fun createFolder(name: String, color: Long) {
        val folder = Folder(
            id = UUID.randomUUID().toString(),
            name = name,
            color = color,
            documentCount = 0,
            createdAt = System.currentTimeMillis()
        )
        folderDao.insert(folder.toEntity())
    }

    override suspend fun renameFolder(id: String, newName: String) {
        folderDao.updateName(id, newName)
    }

    override suspend fun changeFolderColor(id: String, color: Long) {
        folderDao.updateColor(id, color)
    }

    override suspend fun deleteFolder(id: String) {
        documentDao.clearFolderForDocuments(id)
        folderDao.delete(id)
    }
}
