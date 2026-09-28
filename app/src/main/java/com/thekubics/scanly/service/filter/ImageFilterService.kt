package com.thekubics.scanly.service.filter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.thekubics.scanly.domain.model.FilterType
import com.thekubics.scanly.util.Constants
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ImageFilterService @Inject constructor() {

    fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        if (filterType == FilterType.ORIGINAL) {
            // Return the input unchanged instead of allocating a full ARGB_8888 copy
            return bitmap
        }

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        when (filterType) {
            FilterType.ORIGINAL -> { /* Handled above */ }
            FilterType.AUTO_ENHANCE -> {
                val cm = ColorMatrix().apply {
                    val scale = 1.1f
                    val translate = 10f
                    set(floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            FilterType.GRAYSCALE -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            FilterType.BLACK_WHITE -> {
                val cm = ColorMatrix().apply {
                    setSaturation(0f)
                    val scale = 128f
                    val translate = -128f * scale
                    postConcat(ColorMatrix(floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            FilterType.HIGH_CONTRAST -> {
                val cm = ColorMatrix().apply {
                    val scale = 1.5f
                    val translate = (-0.5f * scale + 0.5f) * 255f
                    set(floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            FilterType.COLOR_BOOST -> {
                val cm = ColorMatrix().apply { setSaturation(1.5f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            FilterType.SHARPEN -> {
                val width = bitmap.width
                val height = bitmap.height
                val totalPixels = width * height
                if (totalPixels > 4_000_000) {
                    // Safe color-matrix fallback for very large images to prevent 96MB+ heap spikes
                    val cm = ColorMatrix().apply {
                        val scale = 1.2f
                        val translate = -10f
                        set(floatArrayOf(
                            scale, 0f, 0f, 0f, translate,
                            0f, scale, 0f, 0f, translate,
                            0f, 0f, scale, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    paint.colorFilter = ColorMatrixColorFilter(cm)
                    canvas.drawBitmap(bitmap, 0f, 0f, paint)
                } else {
                    val pixels = IntArray(totalPixels)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    val newPixels = IntArray(totalPixels)

                    val kernel = floatArrayOf(
                        0f, -1f, 0f,
                        -1f, 5f, -1f,
                        0f, -1f, 0f
                    )

                    // 3x3 convolution with edge-clamping so borders are sharpened (not left black/transparent)
                    for (y in 0 until height) {
                        val prevY = (y - 1).coerceIn(0, height - 1)
                        val nextY = (y + 1).coerceIn(0, height - 1)
                        for (x in 0 until width) {
                            val prevX = (x - 1).coerceIn(0, width - 1)
                            val nextX = (x + 1).coerceIn(0, width - 1)
                            var r = 0f; var g = 0f; var b = 0f
                            var k = 0
                            for (ky in -1..1) {
                                val row = when (ky) {
                                    -1 -> (y - 1).coerceIn(0, height - 1)
                                    1 -> (y + 1).coerceIn(0, height - 1)
                                    else -> y
                                }
                                val base = row * width
                                for (kx in -1..1) {
                                    val col = when (kx) {
                                        -1 -> (x - 1).coerceIn(0, width - 1)
                                        1 -> (x + 1).coerceIn(0, width - 1)
                                        else -> x
                                    }
                                    val pixel = pixels[base + col]
                                    val weight = kernel[k++]
                                    r += ((pixel shr 16) and 0xFF) * weight
                                    g += ((pixel shr 8) and 0xFF) * weight
                                    b += (pixel and 0xFF) * weight
                                }
                            }
                            val nr = r.toInt().coerceIn(0, 255)
                            val ng = g.toInt().coerceIn(0, 255)
                            val nb = b.toInt().coerceIn(0, 255)
                            newPixels[y * width + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                        }
                    }
                    result.setPixels(newPixels, 0, width, 0, 0, width, height)
                }
            }

            FilterType.LIGHTEN -> {
                val cm = ColorMatrix().apply {
                    val translate = 30f
                    set(floatArrayOf(
                        1f, 0f, 0f, 0f, translate,
                        0f, 1f, 0f, 0f, translate,
                        0f, 0f, 1f, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            FilterType.DARKEN -> {
                val cm = ColorMatrix().apply {
                    val translate = -30f
                    set(floatArrayOf(
                        1f, 0f, 0f, 0f, translate,
                        0f, 1f, 0f, 0f, translate,
                        0f, 0f, 1f, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
        }
        return result
    }

    fun applyAdjustments(bitmap: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val cm = ColorMatrix().apply {
            val scale = contrast + 1f
            val translate = (brightness * 255f) + (-0.5f * scale + 0.5f) * 255f
            set(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun generateThumbnail(bitmap: Bitmap, maxSize: Int = 256): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = maxSize.toFloat() / max(width, height)
        if (ratio >= 1f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Decodes [sourcePath], downsamples it so the longest edge is at most [maxSize],
     * applies [rotationDegrees], and writes a JPEG thumbnail into [outputDir]/[name].
     *
     * Returns the written file, or null if the source could not be decoded or written.
     */
    fun writeThumbnail(
        sourcePath: String,
        rotationDegrees: Int,
        outputDir: File,
        name: String,
        maxSize: Int = Constants.THUMBNAIL_MAX_SIZE
    ): File? {
        try {
            outputDir.mkdirs()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourcePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(sourcePath, options) ?: return null
            val ratio = maxSize.toFloat() / max(decoded.width, decoded.height)
            val scaled = if (ratio >= 1f) {
                decoded
            } else {
                val sw = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val sh = (decoded.height * ratio).toInt().coerceAtLeast(1)
                val s = Bitmap.createScaledBitmap(decoded, sw, sh, true)
                if (s != decoded) decoded.recycle()
                s
            }

            var final = scaled
            if (rotationDegrees % 360 != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                final = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
                if (final != scaled) scaled.recycle()
            }

            val out = File(outputDir, name)
            FileOutputStream(out).use {
                final.compress(Bitmap.CompressFormat.JPEG, 85, it)
            }
            if (final != decoded) final.recycle()
            return out
        } catch (_: Exception) {
            return null
        }
    }

    fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
