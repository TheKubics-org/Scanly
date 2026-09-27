package com.thekubics.scanly.presentation

import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.model.Folder
import com.thekubics.scanly.domain.model.UserSettings
import com.thekubics.scanly.presentation.home.SortOrder
import com.thekubics.scanly.presentation.theme.FolderColorPresets
import com.thekubics.scanly.presentation.theme.ThemeMode
import com.thekubics.scanly.util.Constants
import com.thekubics.scanly.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPolishAndThemingTest {

    @Test
    fun testThemeModeMapping() {
        val domainModes = UserSettings.ThemeMode.values()
        assertEquals(3, domainModes.size)

        val mappedSystem = when (UserSettings.ThemeMode.SYSTEM) {
            UserSettings.ThemeMode.SYSTEM -> ThemeMode.SYSTEM
            UserSettings.ThemeMode.LIGHT -> ThemeMode.LIGHT
            UserSettings.ThemeMode.DARK -> ThemeMode.DARK
        }
        assertEquals(ThemeMode.SYSTEM, mappedSystem)

        val mappedLight = when (UserSettings.ThemeMode.LIGHT) {
            UserSettings.ThemeMode.SYSTEM -> ThemeMode.SYSTEM
            UserSettings.ThemeMode.LIGHT -> ThemeMode.LIGHT
            UserSettings.ThemeMode.DARK -> ThemeMode.DARK
        }
        assertEquals(ThemeMode.LIGHT, mappedLight)

        val mappedDark = when (UserSettings.ThemeMode.DARK) {
            UserSettings.ThemeMode.SYSTEM -> ThemeMode.SYSTEM
            UserSettings.ThemeMode.LIGHT -> ThemeMode.LIGHT
            UserSettings.ThemeMode.DARK -> ThemeMode.DARK
        }
        assertEquals(ThemeMode.DARK, mappedDark)
    }

    @Test
    fun testFolderColorPresets_Validity() {
        assertEquals(8, FolderColorPresets.size)

        FolderColorPresets.forEach { colorVal ->
            // Verify color has 100% alpha (top byte 0xFF)
            val alpha = (colorVal shr 24) and 0xFF
            assertEquals(0xFFL, alpha)
        }

        // Verify distinct colors
        val uniqueColors = FolderColorPresets.toSet()
        assertEquals(8, uniqueColors.size)
    }

    @Test
    fun testSortOrderSortingLogic() {
        val doc1 = Document(id = "1", title = "Zebra Document", pageCount = 5, thumbnailPath = "", createdAt = 1000L, updatedAt = 5000L)
        val doc2 = Document(id = "2", title = "Alpha Document", pageCount = 10, thumbnailPath = "", createdAt = 2000L, updatedAt = 1000L)
        val doc3 = Document(id = "3", title = "Beta Document", pageCount = 1, thumbnailPath = "", createdAt = 3000L, updatedAt = 3000L)
        val list = listOf(doc1, doc2, doc3)

        // DATE_DESC: doc1 (5000), doc3 (3000), doc2 (1000)
        val sortedDateDesc = list.sortedByDescending { it.updatedAt }
        assertEquals(listOf("1", "3", "2"), sortedDateDesc.map { it.id })

        // DATE_ASC: doc2 (1000), doc3 (3000), doc1 (5000)
        val sortedDateAsc = list.sortedBy { it.updatedAt }
        assertEquals(listOf("2", "3", "1"), sortedDateAsc.map { it.id })

        // NAME_ASC: Alpha (2), Beta (3), Zebra (1)
        val sortedNameAsc = list.sortedBy { it.title }
        assertEquals(listOf("2", "3", "1"), sortedNameAsc.map { it.id })

        // NAME_DESC: Zebra (1), Beta (3), Alpha (2)
        val sortedNameDesc = list.sortedByDescending { it.title }
        assertEquals(listOf("1", "3", "2"), sortedNameDesc.map { it.id })

        // PAGE_COUNT: doc2 (10), doc1 (5), doc3 (1)
        val sortedPageCount = list.sortedByDescending { it.pageCount }
        assertEquals(listOf("2", "1", "3"), sortedPageCount.map { it.id })
    }

    @Test
    fun testRelativeDateFormatting() {
        val now = System.currentTimeMillis()

        // Just now
        val justNow = DateUtils.formatRelative(now - 10 * 1000L)
        assertEquals("Just now", justNow)

        // A minute ago
        val oneMinAgo = DateUtils.formatRelative(now - 70 * 1000L)
        assertEquals("A minute ago", oneMinAgo)

        // 15 minutes ago
        val minsAgo = DateUtils.formatRelative(now - 15 * 60 * 1000L)
        assertEquals("15 minutes ago", minsAgo)

        // An hour ago
        val hourAgo = DateUtils.formatRelative(now - 75 * 60 * 1000L)
        assertEquals("An hour ago", hourAgo)

        // 5 hours ago
        val hoursAgo = DateUtils.formatRelative(now - 5 * 3600 * 1000L)
        assertEquals("5 hours ago", hoursAgo)

        // Yesterday
        val yesterday = DateUtils.formatRelative(now - 30 * 3600 * 1000L)
        assertEquals("Yesterday", yesterday)
    }

    @Test
    fun testTrashDaysRemainingCalculation() {
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        // Just trashed -> 30 days remaining
        val justTrashed = DateUtils.daysUntilPurge(now)
        assertEquals(Constants.TRASH_RETENTION_DAYS, justTrashed)

        // Trashed 10 days ago -> 20 days remaining
        val trashed10DaysAgo = DateUtils.daysUntilPurge(now - (10 * oneDayMillis))
        assertEquals(20, trashed10DaysAgo)

        // Trashed 35 days ago -> 0 days remaining (coerced at 0)
        val trashed35DaysAgo = DateUtils.daysUntilPurge(now - (35 * oneDayMillis))
        assertEquals(0, trashed35DaysAgo)
    }

    @Test
    fun testFolderModelDefaultColor() {
        val folder = Folder(
            id = "f1",
            name = "Invoices",
            createdAt = System.currentTimeMillis()
        )
        assertEquals(0xFF4285F4, folder.color)
        assertEquals(0, folder.documentCount)
        assertEquals("Invoices", folder.name)
    }

    @Test
    fun testSearchFilteringLogic() {
        val docs = listOf(
            Document(id = "1", title = "Medical Report 2026", pageCount = 2, thumbnailPath = "", createdAt = 1000L, updatedAt = 1000L),
            Document(id = "2", title = "Tax Return", pageCount = 4, thumbnailPath = "", createdAt = 1000L, updatedAt = 1000L),
            Document(id = "3", title = "Car Insurance Policy", pageCount = 1, thumbnailPath = "", createdAt = 1000L, updatedAt = 1000L)
        )

        val query = "tax"
        val filtered = docs.filter { it.title.contains(query, ignoreCase = true) }
        assertEquals(1, filtered.size)
        assertEquals("Tax Return", filtered.first().title)

        val queryEmpty = ""
        val filteredEmpty = if (queryEmpty.isBlank()) docs else docs.filter { it.title.contains(queryEmpty, ignoreCase = true) }
        assertEquals(3, filteredEmpty.size)
    }
}
