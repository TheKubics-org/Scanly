package com.thekubics.scanly.domain.model

/**
 * Represents the configuration options when exporting a document to PDF.
 *
 * Defaults are chosen for document scanning rather than desktop publishing: the
 * page box matches each scan's own aspect ratio ([PageSize.AUTO]) and there is no
 * margin, so a scan fills its page instead of floating on blank A4 paper. Pick a
 * fixed [pageSize] when you specifically want printable A4/Letter sheets.
 *
 * @property pageSize The physical size of the PDF pages.
 * @property margin The margin applied around the content on each page.
 * @property quality The JPEG compression quality applied to images.
 * @property documentTitle The metadata title of the PDF document.
 * @property author The metadata author of the PDF document.
 */
data class PdfExportOptions(
    val pageSize: PageSize = PageSize.AUTO,
    val margin: MarginPreset = MarginPreset.NONE,
    val quality: QualityLevel = QualityLevel.HIGH,
    val documentTitle: String? = null,
    val author: String? = null
)

/**
 * Predefined page sizes for PDF export. Measurements are in points (1/72 inch).
 */
enum class PageSize(val width: Float, val height: Float) {
    A4(595.0f, 842.0f),
    LETTER(612.0f, 792.0f),
    LEGAL(612.0f, 1008.0f),
    A3(842.0f, 1190.0f),
    A5(420.0f, 595.0f),
    AUTO(-1f, -1f) // Matches the image aspect ratio
}

/**
 * Predefined margins for PDF export. Measurements are in dp.
 */
enum class MarginPreset(val dpValue: Int) {
    NONE(0),
    SMALL(8),
    NORMAL(16),
    LARGE(32)
}

/**
 * JPEG compression quality levels for images in the PDF.
 */
enum class QualityLevel(val value: Int) {
    HIGH(95),
    MEDIUM(75),
    COMPRESSED(50)
}
