package com.docscanner.app.presentation.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscanner.app.domain.model.Document
import com.docscanner.app.domain.model.FilterType
import com.docscanner.app.domain.model.Page
import com.docscanner.app.domain.model.SaveAction
import com.docscanner.app.domain.model.UserSettings
import com.docscanner.app.domain.repository.DocumentRepository
import com.docscanner.app.domain.repository.SettingsRepository
import com.docscanner.app.domain.service.cloud.CloudStorageService
import com.docscanner.app.service.filter.ImageFilterService
import com.docscanner.app.service.sync.CloudSyncManager
import com.scanly.data.vault.StorageVaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val settingsRepository: SettingsRepository,
    private val cloudStorageService: CloudStorageService,
    private val imageFilterService: ImageFilterService,
    private val storageVaultRepository: StorageVaultRepository,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    val documentId: String = checkNotNull(savedStateHandle["documentId"])

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserSettings()
        )

    private val _document = MutableStateFlow<Document?>(null)
    val document: StateFlow<Document?> = _document.asStateFlow()

    private val _pages = MutableStateFlow<List<Page>>(emptyList())
    val pages: StateFlow<List<Page>> = _pages.asStateFlow()

    private val _selectedPageIndex = MutableStateFlow(0)
    val selectedPageIndex: StateFlow<Int> = _selectedPageIndex.asStateFlow()

    private val _currentFilter = MutableStateFlow(FilterType.ORIGINAL)
    val currentFilter: StateFlow<FilterType> = _currentFilter.asStateFlow()

    private val _brightness = MutableStateFlow(0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _contrast = MutableStateFlow(0f)
    val contrast: StateFlow<Float> = _contrast.asStateFlow()

    private val _rotation = MutableStateFlow(0)
    val rotation: StateFlow<Int> = _rotation.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var originalPreviewBitmap: Bitmap? = null
    private var lastLoadedPageIndex: Int = -1

    init {
        viewModelScope.launch {
            documentRepository.getDocumentById(documentId).collect {
                _document.value = it
            }
        }
        viewModelScope.launch {
            documentRepository.getPages(documentId).collect { pageList ->
                _pages.value = pageList
                if (pageList.isNotEmpty()) {
                    val safeIndex = _selectedPageIndex.value.coerceIn(0, pageList.size - 1)
                    if (_selectedPageIndex.value != safeIndex) {
                        _selectedPageIndex.value = safeIndex
                    }
                    val currentPage = pageList[safeIndex]
                    // Only update these if we are loading for the very first time
                    if (lastLoadedPageIndex == -1) {
                        _currentFilter.value = currentPage.filter
                        _brightness.value = currentPage.brightness
                        _contrast.value = currentPage.contrast
                        _rotation.value = currentPage.rotation
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(
                _pages,
                _selectedPageIndex,
                _currentFilter,
                _brightness,
                _contrast
            ) { pagesList, index, filter, brightness, contrast ->
                listOf(pagesList, index, filter, brightness, contrast)
            }.collectLatest { args ->
                val pagesList = args[0] as List<Page>
                val index = args[1] as Int
                val filter = args[2] as FilterType
                val brightness = args[3] as Float
                val contrast = args[4] as Float

                val currentPage = pagesList.getOrNull(index)
                if (currentPage != null) {
                    try {
                        if (originalPreviewBitmap == null || lastLoadedPageIndex != index) {
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = 2
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                                inMutable = true
                            }
                            originalPreviewBitmap?.recycle()
                            originalPreviewBitmap = BitmapFactory.decodeFile(currentPage.originalImagePath, options)
                            lastLoadedPageIndex = index
                        }

                        val base = originalPreviewBitmap
                        if (base != null) {
                            val rotated = if (_rotation.value != 0) {
                                imageFilterService.rotateImage(base, _rotation.value.toFloat())
                            } else {
                                base
                            }

                            val filtered = imageFilterService.applyFilter(rotated, filter)
                            val adjusted = if (brightness != 0f || contrast != 0f) {
                                imageFilterService.applyAdjustments(filtered, brightness, contrast)
                            } else {
                                filtered
                            }

                            _previewBitmap.value = adjusted
                        } else {
                            _previewBitmap.value = null
                        }
                    } catch (e: Exception) {
                        _previewBitmap.value = null
                    }
                } else {
                    _previewBitmap.value = null
                }
            }
        }
    }

    fun selectPage(index: Int) {
        val pageList = _pages.value
        if (index in pageList.indices) {
            _selectedPageIndex.value = index
            val page = pageList[index]
            _currentFilter.value = page.filter
            _brightness.value = page.brightness
            _contrast.value = page.contrast
            _rotation.value = page.rotation
            lastLoadedPageIndex = -1 // Force reload preview bitmap
        }
    }

    fun setFilter(filter: FilterType) {
        _currentFilter.value = filter
    }

    fun applyFilter(filter: FilterType) {
        setFilter(filter)
    }

    fun setBrightness(value: Float) {
        _brightness.value = value.coerceIn(-1f, 1f)
    }

    fun adjustBrightness(value: Float) {
        setBrightness(value)
    }

    fun setContrast(value: Float) {
        _contrast.value = value.coerceIn(-1f, 1f)
    }

    fun adjustContrast(value: Float) {
        setContrast(value)
    }

    fun resetBrightness() {
        _brightness.value = 0f
    }

    fun resetContrast() {
        _contrast.value = 0f
    }

    fun resetAdjustments() {
        _brightness.value = 0f
        _contrast.value = 0f
    }

    fun rotatePage() {
        _rotation.value = (_rotation.value + 90) % 360
    }

    fun deletePage(pageId: String, onDocumentEmpty: () -> Unit = {}) {
        viewModelScope.launch {
            val pageList = _pages.value
            if (pageList.size <= 1) {
                // Last page deleted -> delete document and navigate back
                documentRepository.moveToTrash(documentId)
                withContext(Dispatchers.Main) {
                    onDocumentEmpty()
                }
            } else {
                documentRepository.deletePage(pageId)
                val newIndex = _selectedPageIndex.value.coerceAtMost(_pages.value.size - 2)
                _selectedPageIndex.value = newIndex.coerceAtLeast(0)
                lastLoadedPageIndex = -1
            }
        }
    }

    fun duplicatePage(pageId: String) {
        viewModelScope.launch {
            documentRepository.duplicatePage(pageId)
        }
    }

    fun reorderPages(pageIds: List<String>) {
        viewModelScope.launch {
            documentRepository.reorderPages(documentId, pageIds)
        }
    }

    fun addMorePages(pageUris: List<Uri>) {
        viewModelScope.launch {
            documentRepository.addPages(documentId, pageUris.map { it.toString() })
        }
    }

    fun saveChanges(
        action: SaveAction = SaveAction.SAVE_AND_UPLOAD,
        rememberAction: Boolean = false,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.value = true

            if (rememberAction) {
                settingsRepository.updateSettings(settings.value.copy(defaultSaveAction = action))
            }

            val pageList = _pages.value
            val currentPage = pageList.getOrNull(_selectedPageIndex.value)
            if (currentPage != null) {
                var processedPath = currentPage.processedImagePath

                val hasChanges = _currentFilter.value != FilterType.ORIGINAL || _brightness.value != 0f || _contrast.value != 0f
                if (hasChanges) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inMutable = true
                    }
                    val origBitmap = BitmapFactory.decodeFile(currentPage.originalImagePath, options)
                    if (origBitmap != null) {
                        var result = imageFilterService.applyFilter(origBitmap, _currentFilter.value)
                        if (_brightness.value != 0f || _contrast.value != 0f) {
                            result = imageFilterService.applyAdjustments(result, _brightness.value, _contrast.value)
                        }

                        val origFile = File(currentPage.originalImagePath)
                        val newFile = File(origFile.parentFile, "${currentPage.id}_processed_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(newFile).use { out ->
                            result.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        processedPath = newFile.absolutePath
                        if (result != origBitmap) {
                            result.recycle()
                        }
                        origBitmap.recycle()
                    }
                } else {
                    processedPath = currentPage.originalImagePath
                }

                val updatedPage = currentPage.copy(
                    filter = _currentFilter.value,
                    brightness = _brightness.value,
                    contrast = _contrast.value,
                    rotation = _rotation.value,
                    processedImagePath = processedPath,
                    thumbnailPath = processedPath
                )
                documentRepository.updatePage(updatedPage)
            }

            val updatedDoc = _document.value?.copy(updatedAt = System.currentTimeMillis())
            if (updatedDoc != null) {
                documentRepository.updateDocument(updatedDoc)

                val activeProvider = storageVaultRepository.getActiveProvider()
                val currentSettings = settingsRepository.settings.first()
                val isCloudAutoEnabled = activeProvider != null || currentSettings.cloudBackupEnabled || currentSettings.autoSyncEnabled
                val shouldUpload = action == SaveAction.UPLOAD_TO_CLOUD ||
                    action == SaveAction.SAVE_AND_UPLOAD ||
                    (action == SaveAction.SAVE_LOCAL && isCloudAutoEnabled)

                // Auto-upload to active BYOS cloud destination (Telegram/R2/Drive)
                if (shouldUpload) {
                    try {
                        cloudStorageService.uploadDocument(updatedDoc, _pages.value)
                    } catch (_: Exception) {}
                    cloudSyncManager.triggerImmediateSync()
                }
            }

            _isSaving.value = false
            withContext(Dispatchers.Main) {
                onSaved()
            }
        }
    }
}
