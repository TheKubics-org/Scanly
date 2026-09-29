package com.thekubics.scanly.service.pdf

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.thekubics.scanly.domain.model.MarginPreset
import com.thekubics.scanly.domain.model.Page
import com.thekubics.scanly.domain.model.PageSize
import com.thekubics.scanly.domain.model.PdfExportOptions
import com.thekubics.scanly.domain.model.QualityLevel
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfGeneratorService @Inject constructor() {

    fun generatePdf(pages: List<Page>, options: PdfExportOptions, outputFile: File): Result<File> {
        val document = PdfDocument()
        return try {
            pages.forEachIndexed { index, page ->
                val sampleSize = when (options.quality) {
                    QualityLevel.COMPRESSED -> 2
                    else -> 1
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                val imagePath = page.processedImagePath.ifBlank { page.originalImagePath }
                val decoded = BitmapFactory.decodeFile(imagePath, decodeOptions) ?: return@forEachIndexed

                // Rotation is stored as page metadata; bake it into the PDF so exports don't come out sideways
                val rotation = ((page.rotation % 360) + 360) % 360
                val bitmap = if (rotation != 0) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                } else {
                    decoded
                }

                try {
                    if (bitmap != decoded) decoded.recycle()
                    val (docWidth, docHeight) = getDimensions(options.pageSize, bitmap.width, bitmap.height)
                    val margin = getMarginPoints(options.margin)

                    val pageInfo = PdfDocument.PageInfo.Builder(docWidth, docHeight, index + 1).create()
                    val pdfPage = document.startPage(pageInfo)
                    val canvas = pdfPage.canvas

                    val availableWidth = docWidth - 2 * margin
                    val availableHeight = docHeight - 2 * margin

                    val scale = minOf(
                        availableWidth.toFloat() / bitmap.width,
                        availableHeight.toFloat() / bitmap.height
                    )

                    val scaledWidth = bitmap.width * scale
                    val scaledHeight = bitmap.height * scale

                    val left = margin + (availableWidth - scaledWidth) / 2
                    val top = margin + (availableHeight - scaledHeight) / 2

                    canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + scaledWidth, top + scaledHeight), null)
                    document.finishPage(pdfPage)
                } finally {
                    bitmap.recycle()
                }
            }

            FileOutputStream(outputFile).use { fos ->
                document.writeTo(fos)
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                document.close()
            } catch (_: Exception) {}
        }
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }


    fun printPdf(context: Context, file: File) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val printAdapter = object : android.print.PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: android.print.PrintAttributes?,
                newAttributes: android.print.PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder(file.name)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("Destination file descriptor is null")
                    return
                }
                try {
                    java.io.FileInputStream(file).use { input ->
                        java.io.FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                }
            }
        }
        val attributes = android.print.PrintAttributes.Builder()
            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
            .build()
        printManager.print(file.nameWithoutExtension, printAdapter, attributes)
    }

    /**
     * Resolves the PDF page box for one scanned image.
     *
     * A fixed [PageSize] (A4, Letter, …) returns that sheet's dimensions and the
     * image is then fitted inside it, which leaves the scan floating on blank
     * paper. [PageSize.AUTO] instead sizes the page to the scan's own aspect
     * ratio so the image fills the sheet edge to edge, which is what a document
     * scanner should produce. Width is pinned to 595pt (A4 width) for a sane
     * maximum page width; only the height follows the image.
     */
    private fun getDimensions(pageSize: PageSize, imgWidth: Int, imgHeight: Int): Pair<Int, Int> {
        if (pageSize == PageSize.AUTO) {
            if (imgWidth <= 0 || imgHeight <= 0) return Pair(AUTO_BASE_WIDTH_PT, AUTO_BASE_WIDTH_PT)
            val scaledHeight = (imgHeight.toFloat() * AUTO_BASE_WIDTH_PT / imgWidth).toInt()
            return Pair(AUTO_BASE_WIDTH_PT, scaledHeight.coerceAtLeast(1))
        }
        // Single source of truth: the enum already carries the dimensions.
        return Pair(pageSize.width.toInt(), pageSize.height.toInt())
    }

    /**
     * Margin in PDF points. Uses [MarginPreset.dpValue] so the value shown in the
     * export dialog is the value actually applied — this previously returned a
     * hardcoded 72 (1 inch) for NORMAL while the UI advertised 16.
     */
    private fun getMarginPoints(marginPreset: MarginPreset): Int = marginPreset.dpValue

    private companion object {
        /** Page width used for aspect-fitted (AUTO) pages, in points. */
        const val AUTO_BASE_WIDTH_PT = 595
    }
}
