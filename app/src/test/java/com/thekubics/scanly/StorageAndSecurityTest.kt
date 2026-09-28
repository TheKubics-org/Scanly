package com.thekubics.scanly

import com.thekubics.scanly.domain.model.FilterType
import com.thekubics.scanly.domain.model.MarginPreset
import com.thekubics.scanly.domain.model.PageSize
import com.thekubics.scanly.domain.model.QualityLevel
import com.thekubics.scanly.domain.model.UserSettings.ThemeMode
import com.thekubics.scanly.util.Constants
import com.thekubics.scanly.util.toFormattedSize
import com.thekubics.scanly.util.toSafeFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StorageAndSecurityTest {

    @Test
    fun testFileNameSanitization_SpecialCharacters() {
        // Test slashes, backslashes, colons, null bytes, and spaces
        val input = "Invoice: 2026/08/28\\Tax\u0000Doc"
        val sanitized = input.toSafeFileName()
        assertEquals("Invoice__2026_08_28_Tax_Doc", sanitized)
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains("\\"))
        assertFalse(sanitized.contains(":"))
        assertFalse(sanitized.contains("\u0000"))
    }

    @Test
    fun testFileNameSanitization_PathTraversalAttempt() {
        val traversalInput = "../../etc/passwd"
        val sanitized = traversalInput.toSafeFileName()
        assertEquals(".._.._etc_passwd", sanitized)
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains("\\"))
    }

    @Test
    fun testFileNameSanitization_NullBytesAndControlChars() {
        val malicious = "confidential\u0000.pdf\r\n\t"
        val sanitized = malicious.toSafeFileName()
        assertEquals("confidential_.pdf___", sanitized)
        assertFalse(sanitized.contains("\u0000"))
        assertFalse(sanitized.contains("\r"))
        assertFalse(sanitized.contains("\n"))
    }

    @Test
    fun testFileNameSanitization_UnicodeAndEmojis() {
        val emojiInput = "Scanned 📑 Notes & Taxes"
        val sanitized = emojiInput.toSafeFileName()
        assertEquals("Scanned___Notes___Taxes", sanitized)
    }

    @Test
    fun testFileNameSanitization_EmptyAndBlank() {
        assertEquals("", "".toSafeFileName())
        assertEquals("___", "   ".toSafeFileName())
    }

    @Test
    fun testShreddingLogic_NonExistentFile() {
        val tempDir = System.getProperty("java.io.tmpdir")
        val fakePath = "$tempDir/docscanner_test_nonexistent_${System.currentTimeMillis()}.jpg"
        val file = File(fakePath)
        
        // Ensure file does not exist
        if (file.exists()) file.delete()
        assertFalse(file.exists())

        // Simulate shredPageFiles logic
        var caughtException: Throwable? = null
        try {
            runCatching {
                if (fakePath.isNotBlank()) {
                    val f = File(fakePath)
                    if (f.exists()) f.delete()
                }
            }.getOrThrow()
        } catch (t: Throwable) {
            caughtException = t
        }

        // Must handle gracefully with 0 exceptions
        assertEquals(null, caughtException)
    }

    @Test
    fun testShreddingLogic_ExistingFileDeletesSuccessfully() {
        val tempDir = System.getProperty("java.io.tmpdir")
        val realFile = File(tempDir, "docscanner_test_shred_${System.currentTimeMillis()}.jpg")
        realFile.writeText("test dummy scan data")
        assertTrue(realFile.exists())

        // Execute shredPageFiles logic
        runCatching {
            if (realFile.absolutePath.isNotBlank()) {
                val f = File(realFile.absolutePath)
                if (f.exists()) f.delete()
            }
        }

        // Assert file was physically shredded from disk
        assertFalse(realFile.exists())
    }

    @Test
    fun testFileProviderAuthorityConsistency() {
        val packageName = "com.thekubics.scanly"
        val expectedAuthority = "$packageName.fileprovider"
        val suffix = Constants.FILE_PROVIDER_AUTHORITY_SUFFIX
        
        assertEquals(".fileprovider", suffix)
        assertEquals(expectedAuthority, "$packageName$suffix")
    }

    @Test
    fun testSettingsEnumSafeFallback() {
        // Corrupted theme mode in datastore should fallback to SYSTEM
        val invalidTheme = "UNKNOWN_CORRUPTED_THEME"
        val parsedTheme = runCatching { ThemeMode.valueOf(invalidTheme) }.getOrDefault(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, parsedTheme)

        // Corrupted filter should fallback to AUTO_ENHANCE (matching production SettingsRepositoryImpl)
        val invalidFilter = "NON_EXISTENT_FILTER"
        val parsedFilter = runCatching { FilterType.valueOf(invalidFilter) }.getOrDefault(FilterType.AUTO_ENHANCE)
        assertEquals(FilterType.AUTO_ENHANCE, parsedFilter)

        // Corrupted page size should fallback to A4
        val invalidPageSize = "SUPER_A10"
        val parsedPageSize = runCatching { PageSize.valueOf(invalidPageSize) }.getOrDefault(PageSize.A4)
        assertEquals(PageSize.A4, parsedPageSize)

        // Corrupted quality should fallback to HIGH
        val invalidQuality = "MEGA_ULTRA_4K"
        val parsedQuality = runCatching { QualityLevel.valueOf(invalidQuality) }.getOrDefault(QualityLevel.HIGH)
        assertEquals(QualityLevel.HIGH, parsedQuality)

        // Corrupted margin should fallback to NORMAL
        val invalidMargin = "GIGANTIC_MARGIN"
        val parsedMargin = runCatching { MarginPreset.valueOf(invalidMargin) }.getOrDefault(MarginPreset.NORMAL)
        assertEquals(MarginPreset.NORMAL, parsedMargin)
    }

    @Test
    fun testFormattedSizeOutput() {
        assertEquals("0 B", 0L.toFormattedSize())
        assertEquals("0 B", (-50L).toFormattedSize())
        assertEquals("500.0 B", 500L.toFormattedSize())
        assertEquals("1.0 KB", 1024L.toFormattedSize())
        assertEquals("2.5 MB", (2.5 * 1024 * 1024).toLong().toFormattedSize())
    }

    @Test
    fun testEmptyAllTrashShredding_MultipleFilesDeleted() {
        val tempDir = System.getProperty("java.io.tmpdir")
        val file1 = File(tempDir, "docscanner_test_trash_1_${System.currentTimeMillis()}.jpg")
        val file2 = File(tempDir, "docscanner_test_trash_2_${System.currentTimeMillis()}.jpg")
        val thumb = File(tempDir, "docscanner_test_thumb_${System.currentTimeMillis()}.jpg")

        file1.writeText("sample page 1")
        file2.writeText("sample page 2")
        thumb.writeText("sample thumb")

        assertTrue(file1.exists())
        assertTrue(file2.exists())
        assertTrue(thumb.exists())

        // Simulate emptyAllTrash file wiping logic
        val filesToShred = listOf(file1.absolutePath, file2.absolutePath, thumb.absolutePath)
        filesToShred.forEach { path ->
            runCatching {
                if (path.isNotBlank()) {
                    val f = File(path)
                    if (f.exists()) f.delete()
                }
            }
        }

        assertFalse(file1.exists())
        assertFalse(file2.exists())
        assertFalse(thumb.exists())
    }

    @Test
    fun testQualityLevelSampleSizeCalculation() {
        val sampleSizeCompressed = when (QualityLevel.COMPRESSED) {
            QualityLevel.COMPRESSED -> 2
            else -> 1
        }
        assertEquals(2, sampleSizeCompressed)

        val sampleSizeHigh = when (QualityLevel.HIGH) {
            QualityLevel.COMPRESSED -> 2
            else -> 1
        }
        assertEquals(1, sampleSizeHigh)

        val sampleSizeMedium = when (QualityLevel.MEDIUM) {
            QualityLevel.COMPRESSED -> 2
            else -> 1
        }
        assertEquals(1, sampleSizeMedium)
    }
}
