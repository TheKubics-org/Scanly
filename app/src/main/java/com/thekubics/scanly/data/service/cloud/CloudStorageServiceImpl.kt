package com.thekubics.scanly.data.service.cloud

import android.content.Context
import androidx.room.withTransaction
import com.thekubics.scanly.data.local.dao.CloudDocumentDao
import com.thekubics.scanly.data.local.dao.DocumentDao
import com.thekubics.scanly.data.local.dao.PageDao
import com.thekubics.scanly.data.local.dao.SyncQueueDao
import com.thekubics.scanly.data.local.db.AppDatabase
import com.thekubics.scanly.data.local.entity.CloudDocumentEntity
import com.thekubics.scanly.data.local.entity.SyncQueueEntity
import com.thekubics.scanly.data.mapper.toDomain
import com.thekubics.scanly.data.mapper.toEntity
import com.thekubics.scanly.domain.model.CloudDocument
import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.model.FilterType
import com.thekubics.scanly.domain.model.Page
import com.thekubics.scanly.domain.model.StorageQuota
import com.thekubics.scanly.domain.model.SyncStatus
import com.thekubics.scanly.domain.model.MarginPreset
import com.thekubics.scanly.domain.model.PageSize
import com.thekubics.scanly.domain.model.PdfExportOptions
import com.thekubics.scanly.domain.model.QualityLevel
import com.thekubics.scanly.domain.service.cloud.CloudStorageService
import com.thekubics.scanly.service.filter.ImageFilterService
import com.thekubics.scanly.service.pdf.PdfGeneratorService
import com.thekubics.scanly.util.Constants
import com.thekubics.scanly.util.NetworkMonitor
import com.thekubics.scanly.util.ScanlyLogger
import com.thekubics.scanly.data.vault.StorageVaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Allowed file extensions for upload — prevents accidental upload of DB or keystore files. */
private val ALLOWED_UPLOAD_EXTENSIONS = setOf("pdf", "jpg", "jpeg", "png")

/** Max retry attempts before a sync queue entry is abandoned to prevent infinite loops. */
private const val MAX_SYNC_RETRIES = 3

@Singleton
class CloudStorageServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val cloudDocumentDao: CloudDocumentDao,
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val syncQueueDao: SyncQueueDao,
    private val storageVaultRepository: StorageVaultRepository,
    private val pdfGeneratorService: PdfGeneratorService,
    private val imageFilterService: ImageFilterService,
    private val networkMonitor: NetworkMonitor
) : CloudStorageService {

    /**
     * Serializes all uploads and background sync scans so that overlapping triggers
     * (periodic worker, immediate worker, dashboard "Sync now", editor auto-upload)
     * never upload the same document twice. Without this, two concurrent
     * [syncPendingDocuments] runs would read the same PENDING queue + unsynced docs
     * and push duplicate copies to the cloud (Telegram/Drive are not idempotent).
     */
    private val syncMutex = Mutex()

    override suspend fun uploadDocument(
        document: Document,
        pages: List<Page>,
        pdfFile: File?,
        onProgress: (Float) -> Unit
    ): Result<CloudDocument> = syncMutex.withLock {
        doUpload(document, pages, pdfFile, onProgress)
    }

    private suspend fun doUpload(
        document: Document,
        pages: List<Page>,
        pdfFile: File? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<CloudDocument> = withContext(Dispatchers.IO) {
        val docShortId = ScanlyLogger.shortId(document.id)
        val userId = "byos_default"

        ScanlyLogger.cloudInfo("UPLOAD_START doc=$docShortId pages=${pages.size}")

        // If offline, queue sync task and mark document as OFFLINE
        if (!networkMonitor.isOnline()) {
            ScanlyLogger.cloudWarn("UPLOAD_QUEUED_OFFLINE doc=$docShortId — device is offline")
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
            ScanlyLogger.cloudWarn("UPLOAD_SKIP doc=$docShortId — no active provider configured")
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

        ScanlyLogger.cloudInfo("UPLOAD_PROVIDER doc=$docShortId provider=${provider.type.name}")

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
                    ScanlyLogger.cloudWarn("PDF_GEN_FAILED doc=$docShortId — falling back to first page image")
                    val firstPage = pages.firstOrNull()
                    val imgFile = firstPage?.let { File(it.processedImagePath.ifBlank { it.originalImagePath }) }
                    if (imgFile != null && imgFile.exists() && imgFile.length() > 0) imgFile else null
                }
            } else null

            if (fileToUpload == null || !fileToUpload.exists()) {
                ScanlyLogger.cloudError("UPLOAD_FAILED doc=$docShortId — no file to upload")
                documentDao.updateSyncStateOnly(document.id, SyncStatus.SYNC_FAILED.name)
                return@withContext Result.failure(IllegalStateException("No document file available to upload."))
            }

            // Security: reject disallowed file extensions
            val fileExtension = fileToUpload.extension.lowercase()
            if (fileExtension !in ALLOWED_UPLOAD_EXTENSIONS) {
                ScanlyLogger.securityWarn("UPLOAD_BLOCKED doc=$docShortId — disallowed extension .$fileExtension")
                documentDao.updateSyncStateOnly(document.id, SyncStatus.SYNC_FAILED.name)
                return@withContext Result.failure(SecurityException("Upload blocked: file type '.$fileExtension' is not allowed."))
            }

            // Enforce the configured per-file upload size guard
            if (fileToUpload.length() > Constants.MAX_FILE_SIZE_BYTES) {
                ScanlyLogger.securityWarn("UPLOAD_BLOCKED doc=$docShortId — size ${fileToUpload.length()} exceeds ${Constants.MAX_FILE_SIZE_BYTES}")
                documentDao.updateSyncStateOnly(document.id, SyncStatus.SYNC_FAILED.name)
                return@withContext Result.failure(IllegalStateException("Upload blocked: file exceeds the ${Constants.MAX_FILE_SIZE_BYTES / (1024 * 1024)} MB size limit."))
            }

            ScanlyLogger.cloudInfo("UPLOAD_FILE doc=$docShortId ext=.$fileExtension size=${ScanlyLogger.formatBytes(fileToUpload.length())}")

            val isPdf = fileExtension == "pdf"
            val mimeType = if (isPdf) "application/pdf" else "image/jpeg"
            // Stable idempotency key: the same document always targets the same cloud
            // object, so retries/re-saves overwrite instead of appending duplicates.
            val remoteKey = "${document.id}.${if (isPdf) "pdf" else "jpg"}"

            val startTime = System.currentTimeMillis()
            val uploadResult = provider.uploadFile(
                file = fileToUpload,
                mimeType = mimeType,
                remotePath = remoteKey,
                progressCallback = { bytesSent, totalBytes ->
                    if (totalBytes > 0) {
                        onProgress((bytesSent.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
            )
            val elapsedMs = System.currentTimeMillis() - startTime

            if (!uploadResult.isSuccess) {
                ScanlyLogger.cloudError("UPLOAD_FAILED doc=$docShortId provider=${provider.type.name} msg=${uploadResult.errorMessage}")
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

            // Security: verify remoteId returned before marking SYNCED
            val cloudId = uploadResult.remoteId
            if (cloudId.isNullOrBlank()) {
                ScanlyLogger.cloudWarn("UPLOAD_NO_REMOTE_ID doc=$docShortId — provider returned no ID, using local fallback")
            }
            val resolvedCloudId = cloudId ?: document.cloudId ?: "cloud_${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            val totalBytes = uploadResult.bytesUploaded.takeIf { it > 0 } ?: fileToUpload.length()

            ScanlyLogger.cloudInfo("UPLOAD_SUCCESS doc=$docShortId size=${ScanlyLogger.formatBytes(totalBytes)} elapsed=${elapsedMs}ms provider=${provider.type.name}")

            val cloudDoc = CloudDocument(
                id = resolvedCloudId,
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
                cloudId = resolvedCloudId,
                fileSize = totalBytes,
                lastSyncedAt = now
            )

            // Remove any pending queue entries for this document
            syncQueueDao.deleteByDocumentId(document.id)

            Result.success(cloudDoc)
        } catch (e: Exception) {
            ScanlyLogger.cloudError("UPLOAD_EXCEPTION doc=$docShortId", e)
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

        val provider = storageVaultRepository.getActiveProvider()
            ?: return@withContext Result.failure(IllegalStateException("No active cloud storage destination configured."))

        // Avoid re-streaming files that already exist locally for this cloud record
        cloudEntity.localDocumentId?.let { existingId ->
            documentDao.getDocumentByIdSync(existingId)?.takeIf { !it.isTrashed }?.let { existing ->
                if (existing.cloudId == cloudEntity.id) {
                    ScanlyLogger.cloudInfo("DOWNLOAD_SKIP_LOCAL_EXISTS cloudId=${ScanlyLogger.shortId(cloudDocumentId)}")
                    return@withContext Result.success(existing.toDomain())
                }
            }
        }

        try {
            val isPdf = cloudEntity.fileType.equals("PDF", ignoreCase = true)
            val ext = if (isPdf) "pdf" else "jpg"
            val safeTitle = cloudEntity.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "download" }
            val targetFile = File(targetDir, "${safeTitle}_${System.currentTimeMillis()}.$ext")

            targetDir.mkdirs()
            val downloadResult = provider.downloadFile(
                remotePath = cloudEntity.id,
                targetFile = targetFile,
                progressCallback = { bytes, total ->
                    if (total > 0) {
                        onProgress((bytes.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            )

            if (!downloadResult.isSuccess) {
                ScanlyLogger.cloudError("DOWNLOAD_FAILED cloudId=${ScanlyLogger.shortId(cloudDocumentId)} msg=${downloadResult.errorMessage}")
                return@withContext Result.failure(
                    IllegalStateException(downloadResult.errorMessage ?: "Download from ${provider.displayName} failed.")
                )
            }

            val downloaded = downloadResult.localFile
                ?: return@withContext Result.failure(IllegalStateException("Download from ${provider.displayName} produced no file."))

            if (isPdf) {
                downloaded.delete()
                return@withContext Result.failure(
                    IllegalStateException("PDF backups cannot be restored as editable pages yet. The backup remains safe in cloud; download it from the storage destination.")
                )
            }

            val now = System.currentTimeMillis()
            val newDocId = UUID.randomUUID().toString()
            val newPageId = UUID.randomUUID().toString()

            val thumbnailPath = if (!isPdf) {
                imageFilterService.writeThumbnail(
                    sourcePath = downloaded.absolutePath,
                    rotationDegrees = 0,
                    outputDir = File(context.filesDir, Constants.THUMBNAILS_DIR),
                    name = "${newDocId}_page_1_$now.jpg"
                )?.absolutePath ?: downloaded.absolutePath
            } else {
                // PDFs have no image thumbnail; grid falls back to a placeholder
                ""
            }

            val newDoc = Document(
                id = newDocId,
                title = cloudEntity.title,
                folderId = null,
                pageCount = 1,
                thumbnailPath = thumbnailPath,
                ocrText = null,
                isEncrypted = false,
                isTrashed = false,
                syncStatus = SyncStatus.SYNCED,
                cloudId = cloudEntity.id,
                fileSize = downloadResult.bytesDownloaded.takeIf { it > 0 } ?: downloaded.length(),
                lastSyncedAt = now,
                createdAt = cloudEntity.uploadDate,
                updatedAt = now
            )
            val newPage = Page(
                id = newPageId,
                documentId = newDocId,
                pageNumber = 1,
                originalImagePath = downloaded.absolutePath,
                processedImagePath = downloaded.absolutePath,
                thumbnailPath = thumbnailPath,
                width = 0,
                height = 0,
                rotation = 0,
                filter = FilterType.ORIGINAL,
                brightness = 0f,
                contrast = 0f,
                ocrText = null,
                ocrConfidence = null,
                createdAt = now
            )

            appDatabase.withTransaction {
                documentDao.upsert(newDoc.toEntity())
                pageDao.insert(newPage.toEntity())
            }

            ScanlyLogger.cloudInfo("DOWNLOAD_SUCCESS cloudId=${ScanlyLogger.shortId(cloudDocumentId)} size=${ScanlyLogger.formatBytes(downloaded.length())} target=${downloaded.name}")
            Result.success(newDoc)
        } catch (e: Exception) {
            ScanlyLogger.cloudError("DOWNLOAD_EXCEPTION cloudId=${ScanlyLogger.shortId(cloudDocumentId)}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteCloudDocument(cloudDocumentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = cloudDocumentDao.getById(cloudDocumentId)
            val provider = storageVaultRepository.getActiveProvider()
            if (provider != null && entity != null) {
                if (!provider.deleteFile(entity.id)) {
                    return@withContext Result.failure(
                        IllegalStateException("Could not delete the backup from ${provider.displayName}. It remains listed so you can retry.")
                    )
                }
                ScanlyLogger.cloudInfo("DELETE_SUCCESS cloudId=${ScanlyLogger.shortId(cloudDocumentId)} provider=${provider.type.name}")
            } else if (entity != null) {
                return@withContext Result.failure(
                    IllegalStateException("No active backup destination is available to delete this file.")
                )
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
            ScanlyLogger.cloudError("DELETE_EXCEPTION cloudId=${ScanlyLogger.shortId(cloudDocumentId)}", e)
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
        val totalBytes = 0L // BYOS providers do not share a universal quota

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
        syncMutex.withLock {
            if (!networkMonitor.isOnline()) {
                ScanlyLogger.syncWarn("SYNC_PENDING_SKIP — device is offline")
                return@withContext Result.failure(IllegalStateException("Cannot sync while offline"))
            }

            val provider = storageVaultRepository.getActiveProvider()
                ?: run {
                    ScanlyLogger.syncWarn("SYNC_PENDING_SKIP — no active provider configured")
                    return@withContext Result.failure(IllegalStateException("No active cloud storage destination configured. Please configure Telegram, Cloudflare R2, or Google Drive in Cloud Settings."))
                }

            val pendingTasks = syncQueueDao.getPendingTasks()
            ScanlyLogger.syncInfo("SYNC_PENDING_START count=${pendingTasks.size} provider=${provider.type.name}")

            var syncedCount = 0
            val handledDocIds = mutableSetOf<String>()

            for (task in pendingTasks) {
                // Dead-letter tasks that have exceeded max retries so they surface in the UI
                // instead of being silently dropped or retried forever.
                if ((task.retryCount ?: 0) >= MAX_SYNC_RETRIES) {
                    ScanlyLogger.syncWarn("SYNC_DEAD_LETTER doc=${ScanlyLogger.shortId(task.documentId)} retries=${task.retryCount} msg=${task.errorMessage}")
                    syncQueueDao.updateStatus(
                        id = task.id,
                        status = "FAILED",
                        error = task.errorMessage ?: "Sync failed after $MAX_SYNC_RETRIES attempts"
                    )
                    continue
                }

                // Atomically claim the task; an overlapping sync run will skip it.
                if (syncQueueDao.claimTask(task.id) == 0) continue

                handledDocIds.add(task.documentId)
                val docEntity = documentDao.getDocumentByIdSync(task.documentId)
                if (docEntity == null || docEntity.isTrashed) {
                    syncQueueDao.delete(task.id)
                    continue
                }

                val pages = pageDao.getPagesForDocumentSync(task.documentId).map { it.toDomain() }
                val result = doUpload(docEntity.toDomain(), pages)
                if (result.isSuccess) {
                    syncedCount++
                    syncQueueDao.delete(task.id)
                } else {
                    // If a concurrent path (e.g. editor auto-upload) already synced it,
                    // drop the task instead of uploading a duplicate copy.
                    val freshState = documentDao.getDocumentByIdSync(task.documentId)
                    if (freshState?.syncStatus == SyncStatus.SYNCED.name) {
                        ScanlyLogger.syncInfo("SYNC_ALREADY_SYNCED_BY_OTHER doc=${ScanlyLogger.shortId(task.documentId)} — dropping queue task")
                        syncQueueDao.delete(task.id)
                    } else {
                        syncQueueDao.releaseTask(task.id)
                        syncQueueDao.incrementRetry(task.id)
                    }
                }
            }

            // Also sync any other unsynced active documents (e.g. captured before cloud was set up)
            val unsyncedDocs = documentDao.getUnsyncedDocuments()
            for (docEntity in unsyncedDocs) {
                if (!handledDocIds.contains(docEntity.id) && !docEntity.isTrashed) {
                    val pages = pageDao.getPagesForDocumentSync(docEntity.id).map { it.toDomain() }
                    val result = doUpload(docEntity.toDomain(), pages)
                    if (result.isSuccess) {
                        syncedCount++
                    }
                }
            }

            ScanlyLogger.syncInfo("SYNC_PENDING_DONE synced=$syncedCount")
            Result.success(syncedCount)
        }
    }
}
