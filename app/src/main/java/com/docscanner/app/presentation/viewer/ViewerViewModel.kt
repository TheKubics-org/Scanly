package com.docscanner.app.presentation.viewer

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscanner.app.domain.model.Document
import com.docscanner.app.domain.model.Page
import com.docscanner.app.domain.model.PdfExportOptions
import com.docscanner.app.domain.repository.DocumentRepository
import com.docscanner.app.service.pdf.PdfGeneratorService
import com.docscanner.app.util.Constants
import com.docscanner.app.util.toSafeFileName
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val pdfGeneratorService: PdfGeneratorService,
    private val context: Context
) : ViewModel() {

    val documentId: String = checkNotNull(savedStateHandle["documentId"])

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
        val page = _pages.value.getOrNull(_currentPageIndex.value)
        if (page != null) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val file = File(page.processedImagePath)
                val image = if (file.exists()) {
                    InputImage.fromFilePath(context, Uri.fromFile(file))
                } else if (page.processedImagePath.startsWith("content://")) {
                    InputImage.fromFilePath(context, Uri.parse(page.processedImagePath))
                } else {
                    _ocrText.value = "Error: Image file not found."
                    _ocrLoading.value = false
                    recognizer.close()
                    return
                }

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        _ocrText.value = visionText.text
                        _ocrLoading.value = false
                        recognizer.close()
                    }
                    .addOnFailureListener {
                        _ocrText.value = "Error extracting text."
                        _ocrLoading.value = false
                        recognizer.close()
                    }
            } catch (e: Exception) {
                _ocrText.value = "Error extracting text: ${e.localizedMessage ?: "Unknown error"}"
                _ocrLoading.value = false
                try { recognizer.close() } catch (_: Exception) {}
            }
        } else {
            _ocrLoading.value = false
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

    fun exportPdf(options: PdfExportOptions): Result<File> {
        return exportPdf(context, options)
    }

    fun exportPdf(ctx: Context, options: PdfExportOptions): Result<File> {
        val currentDoc = _document.value
        val title = (currentDoc?.title ?: "Document").toSafeFileName()
        val exportDir = File(ctx.cacheDir, Constants.PDF_EXPORTS_DIR).apply { mkdirs() }

        // Prune temporary exports older than 24h to prevent disk exhaustion and lingering cache files
        try {
            val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            exportDir.listFiles()?.forEach { oldFile ->
                if (oldFile.lastModified() < cutoff) {
                    oldFile.delete()
                }
            }
        } catch (_: Exception) {}

        val outputFile = File(exportDir, "${title}_${System.currentTimeMillis()}.pdf")
        val result = pdfGeneratorService.generatePdf(_pages.value, options, outputFile)
        if (result.isSuccess) {
            pdfGeneratorService.sharePdf(ctx, outputFile)
        }
        return result
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

