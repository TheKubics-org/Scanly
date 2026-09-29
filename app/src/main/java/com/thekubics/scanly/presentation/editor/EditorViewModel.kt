package com.thekubics.scanly.presentation.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.model.BackupPolicy
import com.thekubics.scanly.domain.model.FilterType
import com.thekubics.scanly.domain.model.Page
import com.thekubics.scanly.domain.model.SaveAction
import com.thekubics.scanly.domain.model.UserSettings
import com.thekubics.scanly.domain.repository.DocumentRepository
import com.thekubics.scanly.domain.repository.SettingsRepository
import com.thekubics.scanly.domain.service.cloud.CloudStorageService
import com.thekubics.scanly.service.filter.ImageFilterService
import com.thekubics.scanly.service.sync.CloudSyncManager
import com.thekubics.scanly.data.vault.StorageVaultRepository
import com.thekubics.scanly.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
                combine(
                    _pages,
                    _selectedPageIndex,
                    _currentFilter,
                    _brightness,
                    _contrast
                ) { pagesList, index, filter, brightness, contrast ->
                    EditorPreviewState(pagesList, index, filter, brightness, contrast, 0)
                },
                _rotation
            ) { state, rotation ->
                state.copy(rotation = rotation)
            }.collectLatest { state ->
                val pagesList = state.pagesList
                val index = state.index
                val filter = state.filter
                val brightness = state.brightness
                val contrast = state.contrast
                val rotationDegrees = state.rotation

                val currentPage = pagesList.getOrNull(index)
                if (currentPage != null && currentPage.originalImagePath.isNotBlank() && java.io.File(currentPage.originalImagePath).exists()) {
                    try {
                        val adjusted = withContext(Dispatchers.Default) {
                            runCatching {
                                if (originalPreviewBitmap == null || lastLoadedPageIndex != index) {
                                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeFile(currentPage.originalImagePath, bounds)
                                    var sample = 1
                                    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PREVIEW_MAX_EDGE) sample *= 2
                                    val options = BitmapFactory.Options().apply {
                                        inSampleSize = sample
                                        inPreferredConfig = Bitmap.Config.ARGB_8888
                                        inMutable = true
                                    }
                                    val previousBase = originalPreviewBitmap
                                    val decoded = BitmapFactory.decodeFile(currentPage.originalImagePath, options)
                                    originalPreviewBitmap = decoded
                                    lastLoadedPageIndex = index
                                    // Drop the old original only after the replacement exists, and
                                    // never recycle it while the UI may still be drawing it.
                                    // Defer recycle to next frame so Compose can finish drawing it.
                                    if (previousBase != null && previousBase !== decoded && !previousBase.isRecycled) {
                                        val stillPublished = _previewBitmap.value === previousBase
                                        if (!stillPublished) {
                                            deferredRecycle(previousBase)
                                        }
                                    }
                                }

                                val base = originalPreviewBitmap
                                if (base != null) {
                                    // Order matters: filter the ORIGINAL first, then rotate for
                                    // display. This matches the save path, which filters the
                                    // unrotated original and stores rotation as page metadata.
                                    // Rotating first (the previous behaviour) made the preview
                                    // disagree with the saved/exported result at 90/270 degrees.
                                    val filtered = imageFilterService.applyFilter(base, filter)
                                    val adjustedLocal = if (brightness != 0f || contrast != 0f) {
                                        imageFilterService.applyAdjustments(filtered, brightness, contrast)
                                    } else {
                                        filtered
                                    }
                                    val finalBitmap = if (rotationDegrees != 0) {
                                        imageFilterService.rotateImage(adjustedLocal, rotationDegrees.toFloat())
                                    } else {
                                        adjustedLocal
                                    }

                                    // Release intermediates, but never `base`: it is the cached
                                    // original and is still needed for the next emission.
                                    if (adjustedLocal != filtered && adjustedLocal != base && adjustedLocal != finalBitmap) {
                                        adjustedLocal.recycle()
                                    }
                                    if (filtered != base && filtered != adjustedLocal && filtered != finalBitmap) {
                                        filtered.recycle()
                                    }
                                    finalBitmap
                                } else null
                            }.getOrNull()
                        }

                        val oldPreview = _previewBitmap.value
                        _previewBitmap.value = adjusted
                        // Defer recycle of the old preview bitmap to the next frame so Compose's
                        // BitmapPainter can finish drawing it. This prevents
                        // "Canvas: trying to use a recycled bitmap" crashes.
                        if (oldPreview != null && oldPreview != adjusted && oldPreview != originalPreviewBitmap && !oldPreview.isRecycled) {
                            deferredRecycle(oldPreview)
                        }
                    } catch (e: Throwable) {
                        _previewBitmap.value = null
                    }
                } else {
                    _previewBitmap.value = null
                }
            }
        }
    }

    private fun deferredRecycle(bitmap: Bitmap) {
        Handler(Looper.getMainLooper()).post {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
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
            var finalPages = pageList
            val currentPage = pageList.getOrNull(_selectedPageIndex.value)
            if (currentPage != null) {
                var processedPath = currentPage.processedImagePath

                val hasChanges = _currentFilter.value != FilterType.ORIGINAL || _brightness.value != 0f || _contrast.value != 0f
                if (hasChanges) {
                    // Downsample before editing so huge camera captures don't spike heap (~96MB per full-res ARGB)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(currentPage.originalImagePath, bounds)
                    var sample = 1
                    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDIT_EDGE) sample *= 2

                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sample
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

                // Delete the orphaned processed file from a previous edit session
                if (currentPage.processedImagePath.isNotBlank() &&
                    currentPage.processedImagePath != currentPage.originalImagePath &&
                    currentPage.processedImagePath != processedPath
                ) {
                    runCatching { File(currentPage.processedImagePath).delete() }
                }

                // Bake rotation into a lightweight thumbnail for the document grid
                val thumbPath = imageFilterService.writeThumbnail(
                    sourcePath = processedPath,
                    rotationDegrees = _rotation.value,
                    outputDir = File(context.filesDir, Constants.THUMBNAILS_DIR),
                    name = "${currentPage.id}_thumb_${System.currentTimeMillis()}.jpg"
                )?.absolutePath ?: processedPath

                val updatedPage = currentPage.copy(
                    filter = _currentFilter.value,
                    brightness = _brightness.value,
                    contrast = _contrast.value,
                    rotation = _rotation.value,
                    processedImagePath = processedPath,
                    thumbnailPath = thumbPath
                )
                documentRepository.updatePage(updatedPage)

                finalPages = pageList.map { if (it.id == updatedPage.id) updatedPage else it }
                _pages.value = finalPages
            }

            val updatedDoc = _document.value?.copy(updatedAt = System.currentTimeMillis())
            if (updatedDoc != null) {
                documentRepository.updateDocument(updatedDoc)

                val activeProvider = storageVaultRepository.getActiveProvider()
                val currentSettings = settingsRepository.settings.first()
                val shouldUpload = BackupPolicy.shouldUploadOnSave(
                    action = action,
                    settings = currentSettings,
                    hasActiveProvider = activeProvider != null
                )

                if (shouldUpload) {
                    try {
                        cloudStorageService.uploadDocument(updatedDoc, finalPages)
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

    override fun onCleared() {
        super.onCleared()
        // The published preview can BE the cached original (filter ORIGINAL, no rotation,
        // no adjustments), so guard against recycling the same instance twice.
        val published = _previewBitmap.value
        val cached = originalPreviewBitmap
        if (cached != null && !cached.isRecycled) cached.recycle()
        if (published != null && published !== cached && !published.isRecycled) published.recycle()
        originalPreviewBitmap = null
        _previewBitmap.value = null
    }
}

private data class EditorPreviewState(
    val pagesList: List<Page>,
    val index: Int,
    val filter: FilterType,
    val brightness: Float,
    val contrast: Float,
    val rotation: Int
)

private const val MAX_EDIT_EDGE = 4000
private const val PREVIEW_MAX_EDGE = 2048

