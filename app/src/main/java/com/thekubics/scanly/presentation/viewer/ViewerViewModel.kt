package com.thekubics.scanly.presentation.viewer

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.model.Page
import com.thekubics.scanly.domain.model.PdfExportOptions
import com.thekubics.scanly.domain.model.UserSettings
import com.thekubics.scanly.domain.repository.DocumentRepository
import com.thekubics.scanly.domain.repository.SettingsRepository
import com.thekubics.scanly.service.pdf.PdfGeneratorService
import com.thekubics.scanly.util.Constants
import com.thekubics.scanly.util.DocumentFormatConverter
import com.thekubics.scanly.util.ExportFormat
import com.thekubics.scanly.util.toSafeFileName
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val pdfGeneratorService: PdfGeneratorService,
    private val settingsRepository: SettingsRepository,
    private val context: Context
) : ViewModel() {

    val documentId: String = checkNotNull(savedStateHandle["documentId"])

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    private val _document = MutableStateFlow<Document?>(null)
    val document: StateFlow<Document?> = _document.asStateFlow()

    private val _pages = MutableStateFlow<List<Page>>(emptyList())
    val pages: StateFlow<List<Page>> = _pages.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _ocrText = MutableStateFlow<String?>(null)
    val ocrText: StateFlow<String?> = _ocrText.asStateFlow()

    private val _ocrLoading = MutableStateFlow(false)
    val ocrLoading: StateFlow<Boolean> = _ocrLoading.asStateFlow()

    init {
        viewModelScope.launch {
            documentRepository.getDocumentById(documentId).collect {
                _document.value = it
            }
        }
        viewModelScope.launch {
            documentRepository.getPages(documentId).collect {
                _pages.value = it
            }
        }
    }

    fun setPage(index: Int) {
        _currentPageIndex.value = index
    }

    fun runOcr(context: Context) {
        _ocrLoading.value = true
        _ocrText.value = null
        val page = _pages.value.getOrNull(_currentPageIndex.value) ?: run {
            _ocrLoading.value = false
            return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        viewModelScope.launch(Dispatchers.IO) {
            var preparedBitmap: Bitmap? = null
            val image = try {
                if (page.processedImagePath.startsWith("content://")) {
                    InputImage.fromFilePath(context, Uri.parse(page.processedImagePath))
                } else {
                    val file = File(page.processedImagePath)
                    if (!file.exists()) {
                        _ocrText.value = "Error: Image file not found."
                        _ocrLoading.value = false
                        recognizer.close()
                        return@launch
                    }
                    // Downsample + bake rotation so OCR sees the image the user actually rotated
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: run {
                        _ocrText.value = "Error extracting text."
                        _ocrLoading.value = false
                        recognizer.close()
                        return@launch
                    }
                    val rotation = ((page.rotation % 360) + 360) % 360
                    val finalBitmap = if (rotation != 0) {
                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                        decoded.recycle()
                        rotated
                    } else {
                        decoded
                    }
                    preparedBitmap = finalBitmap
                    InputImage.fromBitmap(finalBitmap, 0)
                }
            } catch (e: Exception) {
                _ocrText.value = "Error extracting text: ${e.localizedMessage ?: "Unknown error"}"
                _ocrLoading.value = false
                preparedBitmap?.recycle()
                recognizer.close()
                return@launch
            }

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    _ocrText.value = visionText.text
                    _ocrLoading.value = false
                    preparedBitmap?.recycle()
                    viewModelScope.launch {
                        documentRepository.updateOcrText(documentId, page.id, visionText.text)
                    }
                    recognizer.close()
                }
                .addOnFailureListener {
                    _ocrText.value = "Error extracting text."
                    _ocrLoading.value = false
                    preparedBitmap?.recycle()
                    recognizer.close()
                }
        }
    }

    fun copyOcrText(context: Context) {
        val text = _ocrText.value ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OCR Text", text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        clipboard.setPrimaryClip(clip)
    }

    fun exportPdf(ctx: Context, options: PdfExportOptions) {
        exportDocument(ctx, options, ExportFormat.PDF)
    }

    fun exportDocument(ctx: Context, options: PdfExportOptions, format: ExportFormat) {
        viewModelScope.launch {
            val currentDoc = _document.value
            val title = (options.documentTitle?.ifBlank { currentDoc?.title ?: "Document" } ?: currentDoc?.title ?: "Document").toSafeFileName()
            val exportDir = File(ctx.cacheDir, Constants.PDF_EXPORTS_DIR).apply { mkdirs() }

            try {
                val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
                exportDir.listFiles()?.forEach { oldFile ->
                    if (oldFile.lastModified() < cutoff) oldFile.delete()
                }
            } catch (_: Exception) {}

            withContext(Dispatchers.IO) {
                when (format) {
                    ExportFormat.PDF -> {
                        val outputFile = File(exportDir, "${title}_${currentDoc?.id}.pdf")
                        pdfGeneratorService.generatePdf(_pages.value, options, outputFile)
                            .onSuccess { pdfGeneratorService.sharePdf(ctx, it) }
                    }
                    ExportFormat.PNG, ExportFormat.JPEG -> {
                        val pageFiles = mutableListOf<File>()
                        _pages.value.forEachIndexed { index, page ->
                            val src = File(page.processedImagePath.ifBlank { page.originalImagePath })
                            if (!src.exists()) return@forEachIndexed
                            val out = File(exportDir, "${title}_p${index + 1}.${format.extension}")
                            DocumentFormatConverter.convertImage(src, out, format)
                            pageFiles += out
                        }
                        if (pageFiles.isNotEmpty()) {
                            shareFiles(ctx, pageFiles, format.mimeType)
                        }
                    }
                }
            }
        }
    }

    private fun shareFiles(context: Context, files: List<File>, mimeType: String) {
        if (files.isEmpty()) return
        val uris = ArrayList<Uri>(files.size)
        files.forEach { file ->
            uris += androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
        val intent = if (uris.size == 1) {
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
                clipData = android.content.ClipData.newRawUri("", uris.first())
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                clipData = android.content.ClipData.newRawUri("", uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
                }
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = android.content.Intent.createChooser(intent, "Share ${mimeType.substringAfter('/')}")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun sharePdf(context: Context, file: File) {
        pdfGeneratorService.sharePdf(context, file)
    }

    fun printDocument(context: Context, file: File) {
        pdfGeneratorService.printPdf(context, file)
    }

    fun renameDocument(newTitle: String) {
        viewModelScope.launch {
            documentRepository.renameDocument(documentId, newTitle)
        }
    }

    fun deleteDocument() {
        viewModelScope.launch {
            documentRepository.moveToTrash(documentId)
        }
    }
}

