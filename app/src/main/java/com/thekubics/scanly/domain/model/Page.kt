package com.thekubics.scanly.domain.model

/**
 * Represents a single page within a [Document].
 *
 * @property id Unique identifier for the page.
 * @property documentId ID of the document this page belongs to.
 * @property pageNumber 1-based index of the page within the document.
 * @property originalImagePath Path to the unedited, original capture.
 * @property processedImagePath Path to the filtered/edited image.
 * @property thumbnailPath Path to a smaller thumbnail of the processed image.
 * @property width Width of the processed image in pixels.
 * @property height Height of the processed image in pixels.
 * @property rotation Rotation applied to the image in degrees (0, 90, 180, 270).
 * @property filter The current filter applied to this page.
 * @property brightness Brightness adjustment value.
 * @property contrast Contrast adjustment value.
 * @property ocrText Extracted text for this specific page.
 * @property ocrConfidence Confidence score of the extracted text (0.0 to 1.0).
 * @property createdAt Timestamp when the page was created.
 */
data class Page(
    val id: String,
    val documentId: String,
    val pageNumber: Int,
    val originalImagePath: String,
    val processedImagePath: String,
    val thumbnailPath: String,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    val filter: FilterType = FilterType.ORIGINAL,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val ocrText: String? = null,
    val ocrConfidence: Float? = null,
    val createdAt: Long
)

/**
 * Available image filters for processing scanned pages.
 */
enum class FilterType {
    ORIGINAL, AUTO_ENHANCE, GRAYSCALE, BLACK_WHITE,
    HIGH_CONTRAST, COLOR_BOOST, SHARPEN, LIGHTEN, DARKEN;

    /**
     * User-friendly display name of the filter.
     */
    val displayName: String
        get() = when (this) {
            ORIGINAL -> "Original"
            AUTO_ENHANCE -> "Auto"
            GRAYSCALE -> "Grayscale"
            BLACK_WHITE -> "B&W"
            HIGH_CONTRAST -> "Hi-Contrast"
            COLOR_BOOST -> "Color"
            SHARPEN -> "Sharpen"
            LIGHTEN -> "Lighten"
            DARKEN -> "Darken"
        }
}
