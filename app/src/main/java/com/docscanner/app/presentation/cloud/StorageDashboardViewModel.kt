package com.docscanner.app.presentation.cloud

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscanner.app.domain.model.StorageQuota
import com.docscanner.app.domain.service.cloud.CloudStorageService
import com.docscanner.app.service.sync.CloudSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageDashboardViewModel @Inject constructor(
    private val cloudStorageService: CloudStorageService,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    val storageQuota: StateFlow<StorageQuota> = cloudStorageService.getStorageUsage()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            StorageQuota()
        )

    fun clearCloudCache(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            context.cacheDir.listFiles()?.forEach { file ->
                runCatching { file.deleteRecursively() }
            }
        }
    }

    fun forceSyncAll() {
        cloudSyncManager.triggerImmediateSync()
    }
}
