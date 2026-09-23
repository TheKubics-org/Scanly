package com.docscanner.app.presentation.cloud

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscanner.app.data.local.dao.DocumentDao
import com.docscanner.app.domain.model.StorageQuota
import com.docscanner.app.domain.service.cloud.CloudStorageService
import com.docscanner.app.service.sync.CloudSyncManager
import com.docscanner.app.util.ScanlyLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Storage dashboard showing REAL app usage from Room DB document file sizes.
 * The [StorageQuota] here reflects:
 *  - usedBytes  = sum of all non-trashed document fileSizes on device
 *  - totalBytes = 10 GB soft cap for display purposes
 *  - documentBytes = plain (non-encrypted) docs
 *  - imageBytes    = not used directly; reserved for future per-type tracking
 *  - pdfBytes      = encrypted vault docs
 */
@HiltViewModel
class StorageDashboardViewModel @Inject constructor(
    private val documentDao: DocumentDao,
    private val cloudStorageService: CloudStorageService,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    /**
     * Local app storage derived purely from the Room documents table.
     * No device partition / StatFs is used — this shows only Scanly-owned bytes.
     */
    val storageQuota: StateFlow<StorageQuota> = combine(
        documentDao.getTotalFileSizeBytes(),
        documentDao.getPlainFileSizeBytes(),
        documentDao.getEncryptedFileSizeBytes()
    ) { total, plain, encrypted ->
        StorageQuota(
            usedBytes    = total,
            totalBytes   = 10L * 1024L * 1024L * 1024L,  // 10 GB display cap
            documentBytes = plain,
            imageBytes   = 0L,          // reserved for per-type tracking
            pdfBytes     = encrypted    // encrypted vault documents
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StorageQuota()
    )

    /** Cloud quota from the active provider (may be 0 if provider not configured). */
    val cloudQuota: StateFlow<StorageQuota> = cloudStorageService.getStorageUsage()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StorageQuota()
        )

    fun clearCloudCache(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            context.cacheDir.listFiles()?.forEach { file ->
                runCatching { file.deleteRecursively() }
            }
            ScanlyLogger.syncInfo("Cloud cache cleared by user")
        }
    }

    fun forceSyncAll() {
        ScanlyLogger.syncInfo("User triggered force-sync from dashboard")
        cloudSyncManager.triggerImmediateSync()
    }
}
