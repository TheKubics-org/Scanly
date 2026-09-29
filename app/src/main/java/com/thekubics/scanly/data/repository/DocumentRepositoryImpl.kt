package com.thekubics.scanly.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.thekubics.scanly.data.local.dao.DocumentDao
import com.thekubics.scanly.data.local.dao.PageDao
import com.thekubics.scanly.data.local.db.AppDatabase
import com.thekubics.scanly.data.local.entity.PageEntity
import com.thekubics.scanly.data.mapper.toDomain
import com.thekubics.scanly.data.mapper.toEntity
import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.model.FilterType
import com.thekubics.scanly.domain.model.Page
import com.thekubics.scanly.domain.repository.DocumentRepository
import com.thekubics.scanly.service.filter.ImageFilterService
import com.thekubics.scanly.service.sync.CloudSyncManager
import com.thekubics.scanly.util.Constants
import com.thekubics.scanly.util.DocumentFormatConverter
import com.thekubics.scanly.util.ExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val context: Context,
    private val cloudSyncManager: CloudSyncManager,
    private val imageFilterService: ImageFilterService
) : DocumentRepository {

    private fun makeThumbnail(docId: String, pageNum: Int, sourcePath: String, rotationDegrees: Int): String {
        val thumbDir = File(context.filesDir, Constants.THUMBNAILS_DIR)
        return imageFilterService.writeThumbnail(
            sourcePath = sourcePath,
            rotationDegrees = rotationDegrees,
            outputDir = thumbDir,
            name = "${docId}_page_${pageNum}_${System.currentTimeMillis()}.jpg"
        )?.absolutePath ?: sourcePath
    }

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
    ): Document = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val persistedPaths = pageImagePaths
            .take(Constants.MAX_SCAN_PAGES)
            .mapIndexed { index, path ->
                persistImageFile(docId, index + 1, path)
            }

        val firstOriginal = persistedPaths.firstOrNull()
        val firstThumb = firstOriginal?.let { makeThumbnail(docId, 1, it, 0) } ?: ""

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
                    thumbnailPath = if (index == 0) firstThumb else makeThumbnail(docId, index + 1, path, 0),
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

        // Recalculate file size
        recalculateDocumentSize(docId)

        // Trigger on-the-spot cloud sync immediately after document is saved
        cloudSyncManager.triggerImmediateSync()

        doc
    }

    override suspend fun importFiles(title: String, fileUris: List<String>): Document = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "import_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val pagePaths = mutableListOf<String>()
            for (uriOrPath in fileUris.take(Constants.MAX_SCAN_PAGES)) {
                val local = copyImportToCache(uriOrPath, stagingDir) ?: continue
                val ext = local.extension.lowercase()
                when {
                    ext == "pdf" -> {
                        val rendered = DocumentFormatConverter.renderPdfToImages(
                            pdfFile = local,
                            outputDir = File(stagingDir, "pdf_${local.nameWithoutExtension}"),
                            format = ExportFormat.JPEG
                        )
                        pagePaths += rendered.map { it.absolutePath }
                    }
                    DocumentFormatConverter.isSupportedImportExtension(ext) -> {
                        pagePaths += local.absolutePath
                    }
                }
                if (pagePaths.size >= Constants.MAX_SCAN_PAGES) break
            }
            require(pagePaths.isNotEmpty()) { "No supported PDF/PNG/JPG files found to import." }
            createDocument(title, pagePaths.take(Constants.MAX_SCAN_PAGES))
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun copyImportToCache(uriOrPath: String, stagingDir: File): File? {
        return try {
            val nameHint = uriOrPath.substringAfterLast('/').substringBefore('?').ifBlank { "import" }
            val ext = nameHint.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
                .ifBlank {
                    // Probe from content type when possible
                    if (uriOrPath.startsWith("content://")) {
                        context.contentResolver.getType(Uri.parse(uriOrPath))
                            ?.substringAfterLast('/')
                            ?.replace("jpeg", "jpg")
                            ?: "bin"
                    } else "bin"
                }
            if (!DocumentFormatConverter.isSupportedImportExtension(ext) && ext != "bin") {
                // Still copy; extension may be refined after write for content URIs
            }
            val dest = File(stagingDir, "src_${System.nanoTime()}.$ext")
            if (uriOrPath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(uriOrPath))?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } ?: return null
            } else {
                val src = File(uriOrPath)
                if (!src.exists()) return null
                src.copyTo(dest, overwrite = true)
            }
            if (!dest.exists() || dest.length() == 0L) return null
            // Sniff PDF magic if extension unknown
            if (ext == "bin" || !DocumentFormatConverter.isSupportedImportExtension(dest.extension)) {
                val header = dest.inputStream().use { it.readNBytes(5) }.toString(Charsets.US_ASCII)
                val sniffed = when {
                    header.startsWith("%PDF") -> dest.renameTo(File(stagingDir, "${dest.nameWithoutExtension}.pdf"))
                        .let { if (it) File(stagingDir, "${dest.nameWithoutExtension}.pdf") else dest }
                    else -> dest
                }
                return sniffed.takeIf { DocumentFormatConverter.isSupportedImportExtension(it.extension) }
            }
            dest
        } catch (_: Exception) {
            null
        }
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
                val thumbPath = makeThumbnail(docId, pageCount, persistentProcessed, page.rotation)
                if (firstThumb == null) firstThumb = thumbPath

                newPages.add(
                    page.copy(
                        id = UUID.randomUUID().toString(),
                        documentId = docId,
                        pageNumber = pageCount,
                        originalImagePath = persistentOriginal,
                        processedImagePath = persistentProcessed,
                        thumbnailPath = thumbPath,
                        createdAt = now
                    ).toEntity()
                )
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

        appDatabase.withTransaction {
            documentDao.upsert(finalDoc.toEntity())
            pageDao.insertAll(newPages)
            finalDoc
        }
    }

    override suspend fun splitDocument(docId: String, splitAtPage: Int): Pair<Document, Document> = withContext(Dispatchers.IO) {
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
                thumbnailPath = makeThumbnail(doc1Id, idx + 1, proc, p.rotation),
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

        val doc2PageEntities = doc2Pages.mapIndexed { idx, p ->
            val orig = persistImageFile(doc2Id, idx + 1, p.originalImagePath)
            val proc = if (p.processedImagePath != p.originalImagePath) persistImageFile(doc2Id, idx + 1, p.processedImagePath) else orig
            p.copy(
                id = UUID.randomUUID().toString(),
                documentId = doc2Id,
                pageNumber = idx + 1,
                originalImagePath = orig,
                processedImagePath = proc,
                thumbnailPath = makeThumbnail(doc2Id, idx + 1, proc, p.rotation),
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

        appDatabase.withTransaction {
            documentDao.upsert(doc1.toEntity())
            pageDao.insertAll(doc1PageEntities)

            documentDao.upsert(doc2.toEntity())
            pageDao.insertAll(doc2PageEntities)

            // Shred original pages and delete old document
            pages.forEach { shredPageFiles(it.toEntity()) }
            documentDao.delete(docId)
            pageDao.deleteByDocument(docId)
        }

        Pair(doc1, doc2)
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
                thumbnailPath = makeThumbnail(documentId, targetPageNumber, dupProcessed, pageEntity.rotation),
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
            val remainingCapacity = (Constants.MAX_SCAN_PAGES - currentCount).coerceAtLeast(0)
            val pages = pageImagePaths.take(remainingCapacity).mapIndexed { index, path ->
                val pageNum = currentCount + index + 1
                val persistentPath = persistImageFile(documentId, pageNum, path)
                Page(
                    id = UUID.randomUUID().toString(),
                    documentId = documentId,
                    pageNumber = pageNum,
                    originalImagePath = persistentPath,
                    processedImagePath = persistentPath,
                    thumbnailPath = makeThumbnail(documentId, pageNum, persistentPath, 0),
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
        
        recalculateDocumentSize(documentId)

        // On-the-spot sync: upload new pages to cloud immediately
        cloudSyncManager.triggerImmediateSync()
    }

    override suspend fun updateOcrText(documentId: String, pageId: String, ocrText: String) = withContext(Dispatchers.IO) {
        appDatabase.withTransaction {
            pageDao.updateOcrText(pageId, ocrText, 1.0f)
            val currentDoc = documentDao.getDocumentByIdSync(documentId)
            if (currentDoc != null) {
                val combinedOcr = if (currentDoc.ocrText.isNullOrBlank()) {
                    ocrText
                } else if (!currentDoc.ocrText.contains(ocrText)) {
                    "${currentDoc.ocrText}\n\n$ocrText"
                } else {
                    currentDoc.ocrText
                }
                documentDao.updateOcrText(documentId, combinedOcr)
            }
        }
    }

    override suspend fun regenerateThumbnailsIfStale(): Int = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(Constants.PREFS_FILE, Context.MODE_PRIVATE)
        val generated = prefs.getInt(Constants.PREF_THUMBNAIL_GEN, 0)
        if (generated >= Constants.THUMBNAIL_VERSION) return@withContext 0

        var regenerated = 0
        documentDao.getAllDocuments().first().forEach { docEntity ->
            if (docEntity.isTrashed) return@forEach
            val pages = pageDao.getPagesForDocumentSync(docEntity.id)
            if (pages.isEmpty()) return@forEach

            var firstThumb: String? = null
            pages.forEach { page ->
                val source = page.processedImagePath.ifBlank { page.originalImagePath }
                if (source.isBlank()) return@forEach
                val newThumb = makeThumbnail(docEntity.id, page.pageNumber, source, page.rotation)
                pageDao.update(page.copy(thumbnailPath = newThumb))
                if (firstThumb == null) firstThumb = newThumb
            }

            if (firstThumb != null) {
                documentDao.upsert(docEntity.copy(thumbnailPath = firstThumb))
                regenerated++
            }
        }

        prefs.edit().putInt(Constants.PREF_THUMBNAIL_GEN, Constants.THUMBNAIL_VERSION).apply()
        regenerated
    }

    private suspend fun recalculateDocumentSize(documentId: String) = withContext(Dispatchers.IO) {
        val pages = pageDao.getPagesForDocumentSync(documentId)
        val totalSize = pages.sumOf { page ->
            runCatching { File(page.processedImagePath).length() }.getOrDefault(0L)
        }
        documentDao.updateFileSize(documentId, totalSize)
    }
}

