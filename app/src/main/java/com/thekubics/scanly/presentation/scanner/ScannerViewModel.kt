package com.thekubics.scanly.presentation.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thekubics.scanly.data.local.dao.SyncQueueDao
import com.thekubics.scanly.data.local.entity.SyncQueueEntity
import com.thekubics.scanly.domain.repository.DocumentRepository
import com.thekubics.scanly.domain.repository.SettingsRepository
import com.thekubics.scanly.service.sync.CloudSyncManager
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.thekubics.scanly.data.vault.StorageVaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class ScanState { IDLE, SCANNING, PROCESSING, COMPLETE, ERROR }

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val syncQueueDao: SyncQueueDao,
    private val cloudSyncManager: CloudSyncManager,
    private val storageVaultRepository: StorageVaultRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _scannedPages = MutableStateFlow<List<Uri>>(emptyList())
    val scannedPages: StateFlow<List<Uri>> = _scannedPages.asStateFlow()

    private val _pdfUri = MutableStateFlow<Uri?>(null)
    val pdfUri: StateFlow<Uri?> = _pdfUri.asStateFlow()

    fun processScanResult(result: GmsDocumentScanningResult) {
        _scanState.value = ScanState.PROCESSING
        try {
            _scannedPages.value = result.pages?.map { it.imageUri } ?: emptyList()
            _pdfUri.value = result.pdf?.uri
            _scanState.value = ScanState.COMPLETE
        } catch (e: Exception) {
            _scanState.value = ScanState.ERROR
        }
    }

    fun createDocument(title: String, onDocumentCreated: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val pagePaths = _scannedPages.value.map { it.toString() }
                val document = documentRepository.createDocument(title, pagePaths)

                // Auto-queue and trigger immediate cloud backup if provider configured or enabled
                try {
                    val settings = settingsRepository.settings.first()
                    val activeProvider = storageVaultRepository.getActiveProvider()
                    if (activeProvider != null && settings.cloudBackupEnabled && settings.autoSyncEnabled) {
                        syncQueueDao.upsert(
                            SyncQueueEntity(
                                id = UUID.randomUUID().toString(),
                                documentId = document.id,
                                actionType = "UPLOAD",
                                status = "PENDING"
                            )
                        )
                        cloudSyncManager.triggerImmediateSync()
                    }
                } catch (_: Exception) {}

                onDocumentCreated(document.id)
            } catch (e: Exception) {
                _scanState.value = ScanState.ERROR
            }
        }
    }

    fun clearScanResult() {
        _scannedPages.value = emptyList()
        _pdfUri.value = null
        _scanState.value = ScanState.IDLE
    }
}
