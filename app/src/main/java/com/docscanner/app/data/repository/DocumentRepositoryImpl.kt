package com.docscanner.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.docscanner.app.data.local.dao.DocumentDao
import com.docscanner.app.data.local.dao.PageDao
import com.docscanner.app.data.local.db.AppDatabase
import com.docscanner.app.data.local.entity.PageEntity
import com.docscanner.app.data.mapper.toDomain
import com.docscanner.app.data.mapper.toEntity
import com.docscanner.app.domain.model.Document
import com.docscanner.app.domain.model.FilterType
import com.docscanner.app.domain.model.Page
import com.docscanner.app.domain.repository.DocumentRepository
import com.docscanner.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val context: Context
) : DocumentRepository {

    private fun persistImageFile(docId: String, pageIndex: Int, sourceUriOrPath: String): String {
        return try {
            val documentsDir = File(context.filesDir, Constants.DOCUMENTS_DIR).apply { mkdirs() }
            val destFile = File(documentsDir, "${docId}_page_${pageIndex}_${System.currentTimeMillis()}.jpg")

            if (sourceUriOrPath.startsWith("content://")) {
                val uri = Uri.parse(sourceUriOrPath)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else sourceUriOrPath
            } else {
                val sourceFile = File(sourceUriOrPath)
                if (sourceFile.exists() && sourceFile.absolutePath != destFile.absolutePath) {
                    sourceFile.copyTo(destFile, overwrite = true)
                    destFile.absolutePath
                } else if (sourceFile.exists()) {
                    sourceFile.absolutePath
                } else {
                    val uri = Uri.parse(sourceUriOrPath)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else sourceUriOrPath
                }
            }
        } catch (e: Exception) {
            sourceUriOrPath
        }
    }

    private fun shredPageFiles(page: PageEntity) {
        runCatching {
            if (page.originalImagePath.isNotBlank()) {
                val f = File(page.originalImagePath)
                if (f.exists()) f.delete()
            }
        }
        runCatching {
            if (page.processedImagePath.isNotBlank()) {
                val f = File(page.processedImagePath)
                if (f.exists()) f.delete()
            }
        }
        runCatching {
            if (page.thumbnailPath.isNotBlank()) {
                val f = File(page.thumbnailPath)
                if (f.exists()) f.delete()
            }
        }
    }

    override fun getAllDocuments(): Flow<List<Document>> {
        return documentDao.getAllDocuments().map { entities -> entities.map { it.toDomain() } }
    }

    override fun getDocumentById(id: String): Flow<Document?> {
        return documentDao.getDocumentById(id).map { it?.toDomain() }
    }

    override fun getDocumentsByFolder(folderId: String): Flow<List<Document>> {
        return documentDao.getDocumentsByFolder(folderId).map { entities -> entities.map { it.toDomain() } }
    }

    override fun searchDocuments(query: String): Flow<List<Document>> {
        return documentDao.searchDocuments(query).map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTrashedDocuments(): Flow<List<Document>> {
        return documentDao.getTrashedDocuments().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun createDocument(
        title: String,
        pageImagePaths: List<String>,
        pdfPath: String?
    ): Document = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val persistedPaths = pageImagePaths.mapIndexed { index, path ->
            persistImageFile(docId, index + 1, path)
        }

        val firstThumb = persistedPaths.firstOrNull() ?: ""

        val doc = Document(
            id = docId,
            title = title,
            folderId = null,
            pageCount = persistedPaths.size,
            thumbnailPath = firstThumb,
            ocrText = null,
            isEncrypted = false,
            isTrashed = false,
            trashedAt = null,
            createdAt = now,
            updatedAt = now
        )

        appDatabase.withTransaction {
            documentDao.upsert(doc.toEntity())

            val pageEntities = persistedPaths.mapIndexed { index, path ->
                Page(
                    id = UUID.randomUUID().toString(),
                    documentId = docId,
                    pageNumber = index + 1,
                    originalImagePath = path,
                    processedImagePath = path,
                    thumbnailPath = path,
                    width = 0,
                    height = 0,
                    rotation = 0,
                    filter = FilterType.ORIGINAL,
                    brightness = 0f,
                    contrast = 0f,
                    ocrText = null,
                    ocrConfidence = null,
                    createdAt = now
                ).toEntity()
            }
            pageDao.insertAll(pageEntities)
        }

        doc
    }

    override suspend fun updateDocument(document: Document) = withContext(Dispatchers.IO) {
        documentDao.upsert(document.toEntity())
    }

    override suspend fun renameDocument(docId: String, newTitle: String) = withContext(Dispatchers.IO) {
        documentDao.updateTitle(docId, newTitle)
    }

    override suspend fun moveToFolder(docId: String, folderId: String?) = withContext(Dispatchers.IO) {
        documentDao.updateFolder(docId, folderId)
    }

    override suspend fun moveToTrash(docId: String) = withContext(Dispatchers.IO) {
        documentDao.moveToTrash(docId, System.currentTimeMillis())
    }

    override suspend fun restoreFromTrash(docId: String) = withContext(Dispatchers.IO) {
        documentDao.restoreFromTrash(docId)
    }

    override suspend fun permanentlyDelete(docId: String) = withContext(Dispatchers.IO) {
        appDatabase.withTransaction {
            val pages = pageDao.getPagesForDocumentSync(docId)
            pages.forEach { shredPageFiles(it) }
            documentDao.delete(docId)
            pageDao.deleteByDocument(docId)
        }
    }

    override suspend fun purgeOldTrash() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - Constants.TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000
        appDatabase.withTransaction {
            val expiredDocs = documentDao.getOldTrashDocumentsSync(cutoff)
            expiredDocs.forEach { doc ->
                val pages = pageDao.getPagesForDocumentSync(doc.id)
                pages.forEach { shredPageFiles(it) }
                if (doc.thumbnailPath.isNotBlank()) {
                    runCatching {
                        val f = File(doc.thumbnailPath)
                        if (f.exists()) f.delete()
                    }
                }
                pageDao.deleteByDocument(doc.id)
            }
            documentDao.purgeOldTrash(cutoff)
        }
    }

    override suspend fun emptyAllTrash() = withContext(Dispatchers.IO) {
        appDatabase.withTransaction {
            val trashedDocs = documentDao.getTrashedDocumentsSync()
            trashedDocs.forEach { doc ->
                val pages = pageDao.getPagesForDocumentSync(doc.id)
                pages.forEach { shredPageFiles(it) }
                if (doc.thumbnailPath.isNotBlank()) {
                    runCatching {
                        val f = File(doc.thumbnailPath)
                        if (f.exists()) f.delete()
                    }
                }
                pageDao.deleteByDocument(doc.id)
            }
            documentDao.deleteAllTrashed()
        }
    }

    override suspend fun mergeDocuments(docIds: List<String>, newTitle: String): Document = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        appDatabase.withTransaction {
            var pageCount = 0
            var firstThumb: String? = null
            val newPages = mutableListOf<PageEntity>()

            for (sourceId in docIds) {
                val pages = pageDao.getPagesForDocumentSync(sourceId).map { it.toDomain() }
                for (page in pages) {
                    pageCount++
                    val persistentOriginal = persistImageFile(docId, pageCount, page.originalImagePath)
                    val persistentProcessed = if (page.processedImagePath != page.originalImagePath) {
                        persistImageFile(docId, pageCount, page.processedImagePath)
                    } else {
                        persistentOriginal
                    }
                    val thumbPath = persistentProcessed
                    if (firstThumb == null) firstThumb = thumbPath

                    val newPage = page.copy(
                        id = UUID.randomUUID().toString(),
                        documentId = docId,
                        pageNumber = pageCount,
                        originalImagePath = persistentOriginal,
                        processedImagePath = persistentProcessed,
                        thumbnailPath = thumbPath,
                        createdAt = now
                    )
                    newPages.add(newPage.toEntity())
                }
            }

            val finalDoc = Document(
                id = docId,
                title = newTitle,
                folderId = null,
                pageCount = pageCount,
                thumbnailPath = firstThumb ?: "",
                ocrText = null,
                isEncrypted = false,
                isTrashed = false,
                trashedAt = null,
                createdAt = now,
                updatedAt = now
            )

            documentDao.upsert(finalDoc.toEntity())
            pageDao.insertAll(newPages)
            finalDoc
        }
    }

    override suspend fun splitDocument(docId: String, splitAtPage: Int): Pair<Document, Document> = withContext(Dispatchers.IO) {
        appDatabase.withTransaction {
            val originalDoc = documentDao.getDocumentByIdSync(docId)?.toDomain()
                ?: throw IllegalArgumentException("Document not found: $docId")
            val pages = pageDao.getPagesForDocumentSync(docId).map { it.toDomain() }.sortedBy { it.pageNumber }

            val doc1Pages = pages.take(splitAtPage)
            val doc2Pages = pages.drop(splitAtPage)

            val now = System.currentTimeMillis()
            val doc1Id = UUID.randomUUID().toString()
            val doc2Id = UUID.randomUUID().toString()

            val doc1Title = "${originalDoc.title} (1)"
            val doc2Title = "${originalDoc.title} (2)"

            val doc1PageEntities = doc1Pages.mapIndexed { idx, p ->
                val orig = persistImageFile(doc1Id, idx + 1, p.originalImagePath)
                val proc = if (p.processedImagePath != p.originalImagePath) persistImageFile(doc1Id, idx + 1, p.processedImagePath) else orig
                p.copy(
                    id = UUID.randomUUID().toString(),
                    documentId = doc1Id,
                    pageNumber = idx + 1,
                    originalImagePath = orig,
                    processedImagePath = proc,
                    thumbnailPath = proc,
                    createdAt = now
                ).toEntity()
            }
            val doc1 = Document(
                id = doc1Id,
                title = doc1Title,
                folderId = originalDoc.folderId,
                pageCount = doc1PageEntities.size,
                thumbnailPath = doc1PageEntities.firstOrNull()?.thumbnailPath ?: "",
                ocrText = null,
                isEncrypted = originalDoc.isEncrypted,
                isTrashed = false,
                trashedAt = null,
                createdAt = now,
                updatedAt = now
            )
            documentDao.upsert(doc1.toEntity())
            pageDao.insertAll(doc1PageEntities)

            val doc2PageEntities = doc2Pages.mapIndexed { idx, p ->
                val orig = persistImageFile(doc2Id, idx + 1, p.originalImagePath)
                val proc = if (p.processedImagePath != p.originalImagePath) persistImageFile(doc2Id, idx + 1, p.processedImagePath) else orig
                p.copy(
                    id = UUID.randomUUID().toString(),
                    documentId = doc2Id,
                    pageNumber = idx + 1,
                    originalImagePath = orig,
                    processedImagePath = proc,
                    thumbnailPath = proc,
                    createdAt = now
                ).toEntity()
            }
            val doc2 = Document(
                id = doc2Id,
                title = doc2Title,
                folderId = originalDoc.folderId,
                pageCount = doc2PageEntities.size,
                thumbnailPath = doc2PageEntities.firstOrNull()?.thumbnailPath ?: "",
                ocrText = null,
                isEncrypted = originalDoc.isEncrypted,
                isTrashed = false,
                trashedAt = null,
                createdAt = now,
                updatedAt = now
            )
            documentDao.upsert(doc2.toEntity())
            pageDao.insertAll(doc2PageEntities)

            // Shred original pages and delete old document
            pages.forEach { shredPageFiles(it.toEntity()) }
            documentDao.delete(docId)
            pageDao.deleteByDocument(docId)

            Pair(doc1, doc2)
        }
    }

    override fun getPages(documentId: String): Flow<List<Page>> {
        return pageDao.getPagesByDocument(documentId).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun updatePage(page: Page) = withContext(Dispatchers.IO) {
        pageDao.update(page.toEntity())
    }

    override suspend fun deletePage(pageId: String) = withContext(Dispatchers.IO) {
        appDatabase.withTransaction {
            val page = pageDao.getPageById(pageId)
            if (page != null) {
                shredPageFiles(page)
                pageDao.delete(pageId)
                val remainingCount = pageDao.getPageCount(page.documentId)
                val doc = documentDao.getDocumentByIdSync(page.documentId)
                if (doc != null) {
                    val updatedThumb = if (doc.thumbnailPath == page.thumbnailPath || doc.thumbnailPath == page.processedImagePath) {
                        val remainingPages = pageDao.getPagesForDocumentSync(page.documentId)
                        remainingPages.firstOrNull()?.let { it.thumbnailPath.ifBlank { it.processedImagePath } } ?: ""
                    } else {
                        doc.thumbnailPath
                    }
                    documentDao.upsert(doc.copy(pageCount = remainingCount, thumbnailPath = updatedThumb, updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    override suspend fun duplicatePage(pageId: String) = withContext(Dispatchers.IO) {
        appDatabase.withTransaction {
            val pageEntity = pageDao.getPageById(pageId) ?: return@withTransaction
            val documentId = pageEntity.documentId
            val targetPageNumber = pageEntity.pageNumber + 1

            val allPages = pageDao.getPagesForDocumentSync(documentId)
            allPages.filter { it.pageNumber >= targetPageNumber }
                .forEach { pageDao.updatePageNumber(it.id, it.pageNumber + 1) }

            val newId = UUID.randomUUID().toString()
            val dupOriginal = persistImageFile(documentId, targetPageNumber, pageEntity.originalImagePath)
            val dupProcessed = if (pageEntity.processedImagePath != pageEntity.originalImagePath) {
                persistImageFile(documentId, targetPageNumber, pageEntity.processedImagePath)
            } else {
                dupOriginal
            }

            val newPage = pageEntity.copy(
                id = newId,
                pageNumber = targetPageNumber,
                originalImagePath = dupOriginal,
                processedImagePath = dupProcessed,
                thumbnailPath = dupProcessed,
                createdAt = System.currentTimeMillis()
            )
            pageDao.insert(newPage)

            val doc = documentDao.getDocumentByIdSync(documentId)
            if (doc != null) {
                documentDao.upsert(doc.copy(pageCount = allPages.size + 1, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun reorderPages(documentId: String, pageIds: List<String>) = withContext(Dispatchers.IO) {
        appDatabase.withTransaction {
            pageIds.forEachIndexed { index, pageId ->
                pageDao.updatePageNumber(pageId, index + 1)
            }
        }
    }

    override suspend fun addPages(documentId: String, pageImagePaths: List<String>) = withContext(Dispatchers.IO) {
        appDatabase.withTransaction {
            val currentCount = pageDao.getPageCount(documentId)
            val now = System.currentTimeMillis()
            val pages = pageImagePaths.mapIndexed { index, path ->
                val pageNum = currentCount + index + 1
                val persistentPath = persistImageFile(documentId, pageNum, path)
                Page(
                    id = UUID.randomUUID().toString(),
                    documentId = documentId,
                    pageNumber = pageNum,
                    originalImagePath = persistentPath,
                    processedImagePath = persistentPath,
                    thumbnailPath = persistentPath,
                    width = 0,
                    height = 0,
                    rotation = 0,
                    filter = FilterType.ORIGINAL,
                    brightness = 0f,
                    contrast = 0f,
                    ocrText = null,
                    ocrConfidence = null,
                    createdAt = now
                ).toEntity()
            }
            pageDao.insertAll(pages)
            val doc = documentDao.getDocumentByIdSync(documentId)
            if (doc != null) {
                val updatedDoc = doc.copy(
                    pageCount = currentCount + pages.size,
                    thumbnailPath = if (doc.thumbnailPath.isBlank()) pages.firstOrNull()?.thumbnailPath ?: "" else doc.thumbnailPath,
                    updatedAt = now
                )
                documentDao.upsert(updatedDoc)
            }
        }
    }

    override suspend fun updateOcrText(documentId: String, pageId: String, ocrText: String) = withContext(Dispatchers.IO) {
        appDatabase.withTransaction {
            pageDao.updateOcrText(pageId, ocrText, 1.0f)
            val currentDoc = documentDao.getDocumentByIdSync(documentId)
            val combinedOcr = if (currentDoc?.ocrText.isNullOrBlank()) {
                ocrText
            } else if (!currentDoc?.ocrText.orEmpty().contains(ocrText)) {
                "${currentDoc?.ocrText}\n\n$ocrText"
            } else {
                currentDoc?.ocrText ?: ocrText
            }
            documentDao.updateOcrText(documentId, combinedOcr)
        }
    }
}

