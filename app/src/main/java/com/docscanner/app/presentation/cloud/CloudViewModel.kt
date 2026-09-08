package com.docscanner.app.presentation.cloud

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscanner.app.domain.model.CloudDocument
import com.docscanner.app.domain.model.StorageQuota
import com.docscanner.app.domain.service.cloud.CloudStorageService
import com.docscanner.app.service.sync.CloudSyncManager
import com.docscanner.app.util.NetworkMonitor
import com.scanly.data.storage.StorageConfig
import com.scanly.data.vault.StorageVaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val cloudStorageService: CloudStorageService,
    private val storageVaultRepository: StorageVaultRepository,
    private val cloudSyncManager: CloudSyncManager,
    val networkMonitor: NetworkMonitor
) : ViewModel() {

    val activeConfig: StateFlow<StorageConfig?> = storageVaultRepository.observeActiveConfig()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    val cloudDocuments: StateFlow<List<CloudDocument>> = cloudStorageService.listCloudDocuments()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val storageQuota: StateFlow<StorageQuota> = cloudStorageService.getStorageUsage()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            StorageQuota()
        )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun triggerSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            cloudSyncManager.triggerImmediateSync()
            val result = cloudStorageService.syncPendingDocuments()
            _isSyncing.value = false
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _message.value = if (count > 0) "Synchronized $count documents" else "All documents up to date"
            } else {
                _message.value = result.exceptionOrNull()?.message ?: "Sync failed"
            }
        }
    }

    fun downloadDocument(context: Context, cloudDoc: CloudDocument) {
        viewModelScope.launch {
            val result = cloudStorageService.downloadDocument(cloudDoc.id, context.filesDir)
            if (result.isSuccess) {
                _message.value = "Downloaded \"${cloudDoc.title}\" to device"
            } else {
                _message.value = result.exceptionOrNull()?.message ?: "Download failed"
            }
        }
    }

    fun deleteCloudDocument(cloudDoc: CloudDocument) {
        viewModelScope.launch {
            val result = cloudStorageService.deleteCloudDocument(cloudDoc.id)
            if (result.isSuccess) {
                _message.value = "Deleted \"${cloudDoc.title}\" from cloud"
            } else {
                _message.value = result.exceptionOrNull()?.message ?: "Delete failed"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
