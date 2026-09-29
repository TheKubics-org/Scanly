package com.thekubics.scanly.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

enum class ExportFormat {
    PDF, PNG, JPEG;

    val extension: String
        get() = when (this) {
            PDF -> "pdf"
            PNG -> "png"
            JPEG -> "jpg"
        }

    val mimeType: String
        get() = when (this) {
            PDF -> "application/pdf"
            PNG -> "image/png"
            JPEG -> "image/jpeg"
        }

    companion object {
        fun fromExtension(ext: String): ExportFormat? = when (ext.lowercase()) {
            "pdf" -> PDF
            "png" -> PNG
            "jpg", "jpeg" -> JPEG
            else -> null
        }
    }
}

/**
 * Converts between PDF / PNG / JPEG using platform APIs only.
 */
object DocumentFormatConverter {

    fun isSupportedImportExtension(ext: String): Boolean =
        ExportFormat.fromExtension(ext) != null

    /**
     * Renders each PDF page to an image file under [outputDir].
     * Caps at [Constants.MAX_SCAN_PAGES].
     */
    fun renderPdfToImages(
        pdfFile: File,
        outputDir: File,
        format: ExportFormat,
        jpegQuality: Int = 90
    ): List<File> {
        require(format == ExportFormat.PNG || format == ExportFormat.JPEG) {
            "PDF pages can only be rendered to PNG or JPEG"
        }
        require(pdfFile.exists()) { "PDF not found: ${pdfFile.absolutePath}" }
        outputDir.mkdirs()

        val results = mutableListOf<File>()
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pageCount = minOf(renderer.pageCount, Constants.MAX_SCAN_PAGES)
                for (i in 0 until pageCount) {
                    renderer.openPage(i).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            page.width.coerceAtLeast(1),
                            page.height.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val out = File(outputDir, "page_${i + 1}.${format.extension}")
                        writeBitmap(bitmap, out, format, jpegQuality)
                        bitmap.recycle()
                        results += out
                    }
                }
            }
        }
        return results
    }

    /** Re-encodes an image file as PNG or JPEG. */
    fun convertImage(
        source: File,
        target: File,
        format: ExportFormat,
        jpegQuality: Int = 90
    ): File {
        require(format == ExportFormat.PNG || format == ExportFormat.JPEG) {
            "convertImage only supports PNG/JPEG"
        }
        require(source.exists()) { "Source not found: ${source.absolutePath}" }
        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("Could not decode image: ${source.name}")
        try {
            target.parentFile?.mkdirs()
            writeBitmap(bitmap, target, format, jpegQuality)
        } finally {
            bitmap.recycle()
        }
        return target
    }

    private fun writeBitmap(
        bitmap: Bitmap,
        target: File,
        format: ExportFormat,
        jpegQuality: Int
    ) {
        FileOutputStream(target).use { out ->
            when (format) {
                ExportFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                ExportFormat.JPEG -> {
                    // JPEG has no alpha — flatten onto white if needed
                    val flat = if (bitmap.hasAlpha()) {
                        Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { dst ->
                            val canvas = Canvas(dst)
                            canvas.drawColor(Color.WHITE)
                            canvas.drawBitmap(bitmap, 0f, 0f, null)
                        }
                    } else bitmap
                    try {
                        flat.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(1, 100), out)
                    } finally {
                        if (flat !== bitmap) flat.recycle()
                    }
                }
                ExportFormat.PDF -> error("writeBitmap does not write PDF")
            }
        }
    }
}
