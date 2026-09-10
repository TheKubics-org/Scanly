package com.docscanner.app.data.service.cloud

import android.content.Context
import com.docscanner.app.data.local.dao.CloudDocumentDao
import com.docscanner.app.data.local.dao.DocumentDao
import com.docscanner.app.data.local.dao.PageDao
import com.docscanner.app.data.local.dao.SyncQueueDao
import com.docscanner.app.data.local.entity.CloudDocumentEntity
import com.docscanner.app.data.local.entity.SyncQueueEntity
import com.docscanner.app.data.mapper.toDomain
import com.docscanner.app.data.mapper.toEntity
import com.docscanner.app.domain.model.CloudDocument
import com.docscanner.app.domain.model.Document
import com.docscanner.app.domain.model.Page
import com.docscanner.app.domain.model.StorageQuota
import com.docscanner.app.domain.model.SyncStatus
import com.docscanner.app.domain.model.MarginPreset
import com.docscanner.app.domain.model.PageSize
import com.docscanner.app.domain.model.PdfExportOptions
import com.docscanner.app.domain.model.QualityLevel
import com.docscanner.app.domain.service.cloud.CloudStorageService
import com.docscanner.app.service.pdf.PdfGeneratorService
import com.docscanner.app.util.NetworkMonitor
import com.scanly.data.vault.StorageVaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudStorageServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudDocumentDao: CloudDocumentDao,
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val syncQueueDao: SyncQueueDao,
    private val storageVaultRepository: StorageVaultRepository,
    private val pdfGeneratorService: PdfGeneratorService,
    private val networkMonitor: NetworkMonitor
) : CloudStorageService {

    override suspend fun uploadDocument(
        document: Document,
        pages: List<Page>,
        pdfFile: File?,
        onProgress: (Float) -> Unit
    ): Result<CloudDocument> = withContext(Dispatchers.IO) {
        val userId = "byos_default"

        // If offline, queue sync task and mark document as OFFLINE
        if (!networkMonitor.isOnline()) {
            val queueTask = SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                documentId = document.id,
                actionType = "UPLOAD",
                status = "PENDING"
            )
            syncQueueDao.upsert(queueTask)
            documentDao.updateSyncStateOnly(document.id, SyncStatus.OFFLINE.name)
            return@withContext Result.failure(IllegalStateException("Device is currently offline. Document queued for automatic upload."))
        }

        val provider = storageVaultRepository.getActiveProvider()
        if (provider == null || !provider.isEnabled) {
            val queueTask = SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                documentId = document.id,
                actionType = "UPLOAD",
                status = "PENDING",
                errorMessage = "No active cloud storage destination configured"
            )
            syncQueueDao.upsert(queueTask)
            documentDao.updateSyncStateOnly(document.id, SyncStatus.SYNC_FAILED.name)
            return@withContext Result.failure(IllegalStateException("No cloud storage destination configured. Please configure Telegram, Cloudflare R2, or Google Drive in Cloud Settings."))
        }

        var tempPdfGenerated: File? = null
        try {
            documentDao.updateSyncStateOnly(document.id, SyncStatus.UPLOADING.name)

            // Determine file to upload: use pdfFile if available, or generate from pages
            val fileToUpload: File? = if (pdfFile != null && pdfFile.exists() && pdfFile.length() > 0) {
                pdfFile
            } else if (pages.isNotEmpty()) {
                val safeTitle = document.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "scan" }
                val targetPdf = File(context.cacheDir, "${safeTitle}_${document.id}.pdf")
                val exportOptions = PdfExportOptions(
                    documentTitle = document.title,
                    pageSize = PageSize.A4,
                    quality = QualityLevel.HIGH,
                    margin = MarginPreset.NORMAL
                )
                val genResult = pdfGeneratorService.generatePdf(pages, exportOptions, targetPdf)
                if (genResult.isSuccess && targetPdf.exists() && targetPdf.length() > 0) {
                    tempPdfGenerated = targetPdf
                    targetPdf
                } else {
                    val firstPage = pages.firstOrNull()
                    val imgFile = firstPage?.let { File(it.processedImagePath.ifBlank { it.originalImagePath }) }
                    if (imgFile != null && imgFile.exists() && imgFile.length() > 0) imgFile else null
                }
            } else null

            if (fileToUpload == null || !fileToUpload.exists()) {
                documentDao.updateSyncStateOnly(document.id, SyncStatus.SYNC_FAILED.name)
                return@withContext Result.failure(IllegalStateException("No document file available to upload."))
            }

            val isPdf = fileToUpload.name.endsWith(".pdf", ignoreCase = true)
            val mimeType = if (isPdf) "application/pdf" else "image/jpeg"
            val safeFileName = "${document.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "scan" }}.${if (isPdf) "pdf" else "jpg"}"

            val uploadResult = provider.uploadFile(
                file = fileToUpload,
                mimeType = mimeType,
                remotePath = safeFileName,
                progressCallback = { bytesSent, totalBytes ->
                    if (totalBytes > 0) {
                        onProgress((bytesSent.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
            )

            if (!uploadResult.isSuccess) {
                documentDao.updateSyncStateOnly(document.id, SyncStatus.SYNC_FAILED.name)
                val queueTask = SyncQueueEntity(
                    id = UUID.randomUUID().toString(),
                    documentId = document.id,
                    actionType = "UPLOAD",
                    status = "PENDING",
                    errorMessage = uploadResult.errorMessage
                )
                syncQueueDao.upsert(queueTask)
                return@withContext Result.failure(IllegalStateException(uploadResult.errorMessage ?: "Upload to ${provider.displayName} failed."))
            }

            val cloudId = uploadResult.remoteId ?: document.cloudId ?: "cloud_${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            val totalBytes = uploadResult.bytesUploaded.takeIf { it > 0 } ?: fileToUpload.length()

            val cloudDoc = CloudDocument(
                id = cloudId,
                localDocumentId = document.id,
                title = document.title,
                fileType = if (isPdf) "PDF" else "JPG",
                pageCount = pages.size.coerceAtLeast(document.pageCount),
                fileSize = totalBytes,
                thumbnailUrl = document.thumbnailPath,
                cloudFileUrl = uploadResult.remoteUrl ?: fileToUpload.absolutePath,
                uploadDate = now,
                syncStatus = SyncStatus.SYNCED
            )

            // Persist in cloud catalog
            cloudDocumentDao.upsert(cloudDoc.toEntity(userId))

            // Update local document record
            documentDao.updateSyncStatus(
                docId = document.id,
                status = SyncStatus.SYNCED.name,
                cloudId = cloudId,
                fileSize = totalBytes,
                lastSyncedAt = now
            )

            // Remove any pending queue entries for this document
            syncQueueDao.deleteByDocumentId(document.id)

            Result.success(cloudDoc)
        } catch (e: Exception) {
            documentDao.updateSyncStateOnly(document.id, SyncStatus.SYNC_FAILED.name)
            val queueTask = SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                documentId = document.id,
                actionType = "UPLOAD",
                status = "PENDING",
                errorMessage = e.message
            )
            syncQueueDao.upsert(queueTask)
            Result.failure(e)
        } finally {
            tempPdfGenerated?.let {
                try {
                    if (it.exists()) it.delete()
                } catch (_: Exception) {}
            }
        }
    }

    override suspend fun downloadDocument(
        cloudDocumentId: String,
        targetDir: File,
        onProgress: (Float) -> Unit
    ): Result<Document> = withContext(Dispatchers.IO) {
        val cloudEntity = cloudDocumentDao.getById(cloudDocumentId)
            ?: return@withContext Result.failure(IllegalArgumentException("Cloud document not found: $cloudDocumentId"))

        if (!networkMonitor.isOnline()) {
            return@withContext Result.failure(IllegalStateException("Device is offline. Cannot download cloud document."))
        }

        try {
            for (step in 1..10) {
                delay(50)
                onProgress(step / 10f)
            }

            // Check if local document already exists
            val existingDoc = cloudEntity.localDocumentId?.let { documentDao.getDocumentByIdSync(it) }
            val now = System.currentTimeMillis()

            val doc = if (existingDoc != null) {
                val updated = existingDoc.copy(
                    syncStatus = SyncStatus.SYNCED.name,
                    lastSyncedAt = now
                )
                documentDao.upsert(updated)
                updated.toDomain()
            } else {
                val newDocId = UUID.randomUUID().toString()
                val newDoc = Document(
                    id = newDocId,
                    title = cloudEntity.title,
                    folderId = null,
                    pageCount = cloudEntity.pageCount,
                    thumbnailPath = cloudEntity.thumbnailUrl ?: "",
                    ocrText = null,
                    isEncrypted = false,
                    isTrashed = false,
                    syncStatus = SyncStatus.SYNCED,
                    cloudId = cloudEntity.id,
                    fileSize = cloudEntity.fileSize,
                    lastSyncedAt = now,
                    createdAt = cloudEntity.uploadDate,
                    updatedAt = now
                )
                documentDao.upsert(newDoc.toEntity())
                newDoc
            }

            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteCloudDocument(cloudDocumentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = cloudDocumentDao.getById(cloudDocumentId)
            val provider = storageVaultRepository.getActiveProvider()
            if (provider != null && entity != null) {
                try {
                    provider.deleteFile(entity.id)
                } catch (_: Exception) {}
            }

            if (entity?.localDocumentId != null) {
                documentDao.updateSyncStatus(
                    docId = entity.localDocumentId,
                    status = SyncStatus.LOCAL.name,
                    cloudId = null,
                    fileSize = 0L,
                    lastSyncedAt = 0L
                )
            }
            cloudDocumentDao.delete(cloudDocumentId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun listCloudDocuments(): Flow<List<CloudDocument>> {
        val userId = "byos_default"
        return cloudDocumentDao.getCloudDocuments(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStorageUsage(): Flow<StorageQuota> {
        val userId = "byos_default"
        val totalBytes = 10L * 1024L * 1024L * 1024L // 10 GB default indicator

        return combine(
            cloudDocumentDao.getTotalUsage(userId),
            cloudDocumentDao.getUsageByType(userId, "PDF"),
            cloudDocumentDao.getUsageByType(userId, "JPG")
        ) { total, pdf, img ->
            val used = total ?: 0L
            val pdfs = pdf ?: 0L
            val imgs = img ?: 0L
            val docs = (used - pdfs - imgs).coerceAtLeast(0L)

            StorageQuota(
                usedBytes = used,
                totalBytes = totalBytes,
                documentBytes = docs,
                imageBytes = imgs,
                pdfBytes = pdfs
            )
        }
    }

    override suspend fun syncPendingDocuments(): Result<Int> = withContext(Dispatchers.IO) {
        if (!networkMonitor.isOnline()) {
            return@withContext Result.failure(IllegalStateException("Cannot sync while offline"))
        }

        val provider = storageVaultRepository.getActiveProvider()
            ?: return@withContext Result.failure(IllegalStateException("No active cloud storage destination configured. Please configure Telegram, Cloudflare R2, or Google Drive in Cloud Settings."))

        val pendingTasks = syncQueueDao.getPendingTasks()
        var syncedCount = 0
        val handledDocIds = mutableSetOf<String>()

        for (task in pendingTasks) {
            handledDocIds.add(task.documentId)
            val docEntity = documentDao.getDocumentByIdSync(task.documentId)
            if (docEntity != null && !docEntity.isTrashed) {
                val pages = pageDao.getPagesForDocumentSync(task.documentId).map { it.toDomain() }
                val result = uploadDocument(docEntity.toDomain(), pages)
                if (result.isSuccess) {
                    syncedCount++
                    syncQueueDao.delete(task.id)
                } else {
                    syncQueueDao.incrementRetry(task.id)
                }
            } else {
                syncQueueDao.delete(task.id)
            }
        }

        // Also sync any other unsynced active documents (e.g. captured before cloud was set up)
        val unsyncedDocs = documentDao.getUnsyncedDocuments()
        for (docEntity in unsyncedDocs) {
            if (!handledDocIds.contains(docEntity.id) && !docEntity.isTrashed) {
                val pages = pageDao.getPagesForDocumentSync(docEntity.id).map { it.toDomain() }
                val result = uploadDocument(docEntity.toDomain(), pages)
                if (result.isSuccess) {
                    syncedCount++
                }
            }
        }

        Result.success(syncedCount)
    }
}
