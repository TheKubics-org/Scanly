package com.thekubics.scanly.presentation.cloud

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thekubics.scanly.data.local.dao.DocumentDao
import com.thekubics.scanly.domain.model.StorageQuota
import com.thekubics.scanly.domain.service.cloud.CloudStorageService
import com.thekubics.scanly.service.sync.CloudSyncManager
import com.thekubics.scanly.util.ScanlyLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Storage dashboard from Room document file sizes.
 * documentBytes = all local docs; imageBytes/pdfBytes reserved for finer breakdown.
 */
@HiltViewModel
class StorageDashboardViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val documentDao: DocumentDao,
    private val cloudStorageService: CloudStorageService,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    /**
     * Local app storage derived purely from the Room documents table.
     * No device partition / StatFs is used — this shows only Scanly-owned bytes.
     */
    val storageQuota: StateFlow<StorageQuota> = documentDao.getTotalFileSizeBytes()
        .map { total ->
            StorageQuota(
                usedBytes = total,
                totalBytes = StatFs(context.filesDir.path).totalBytes,
                documentBytes = total,
                imageBytes = 0L,
                pdfBytes = 0L
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
