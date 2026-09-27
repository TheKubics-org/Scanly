package com.thekubics.scanly.domain.model

/**
 * Represents cloud storage metrics and quota distribution.
 *
 * @property usedBytes Total bytes currently consumed across all cloud documents.
 * @property totalBytes Known storage allowance in bytes, or zero when a provider does not expose one.
 * @property documentBytes Bytes used by processed document packages.
 * @property imageBytes Bytes used by raw image assets.
 * @property pdfBytes Bytes used by generated PDF files.
 */
data class StorageQuota(
    val usedBytes: Long = 0L,
    val totalBytes: Long = 0L, // Unknown unless a provider reports a real quota
    val documentBytes: Long = 0L,
    val imageBytes: Long = 0L,
    val pdfBytes: Long = 0L
) {
    val availableBytes: Long
        get() = (totalBytes - usedBytes).coerceAtLeast(0L)

    val usageFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
