package com.docscanner.app.presentation

import androidx.lifecycle.SavedStateHandle
import com.docscanner.app.domain.model.*
import com.docscanner.app.domain.repository.DocumentRepository
import com.docscanner.app.presentation.editor.EditorViewModel
import com.docscanner.app.service.filter.ImageFilterService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorAndViewerPolishTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<DocumentRepository>(relaxed = true)
    private val settingsRepository = mockk<com.docscanner.app.domain.repository.SettingsRepository>(relaxed = true)
    private val cloudStorageService = mockk<com.docscanner.app.domain.service.cloud.CloudStorageService>(relaxed = true)
    private val filterService = mockk<ImageFilterService>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testFilterTypeEnum_CompletenessAndDisplayNames() {
        val filters = FilterType.values()
        assertEquals(9, filters.size)

        val expectedDisplayNames = mapOf(
            FilterType.ORIGINAL to "Original",
            FilterType.AUTO_ENHANCE to "Auto",
            FilterType.GRAYSCALE to "Grayscale",
            FilterType.BLACK_WHITE to "B&W",
            FilterType.HIGH_CONTRAST to "Hi-Contrast",
            FilterType.COLOR_BOOST to "Color",
            FilterType.SHARPEN to "Sharpen",
            FilterType.LIGHTEN to "Lighten",
            FilterType.DARKEN to "Darken"
        )

        filters.forEach { filter ->
            val expected = expectedDisplayNames[filter]
            assertNotNull("Filter $filter should have an expected display name", expected)
            assertEquals(expected, filter.displayName)
            assertTrue(filter.displayName.isNotBlank())
        }
    }

    @Test
    fun testPdfExportOptions_ModelsAndPresets() {
        // PageSize validation
        assertEquals(595.0f, PageSize.A4.width, 0.01f)
        assertEquals(842.0f, PageSize.A4.height, 0.01f)
        assertEquals(612.0f, PageSize.LETTER.width, 0.01f)
        assertEquals(792.0f, PageSize.LETTER.height, 0.01f)
        assertEquals(-1.0f, PageSize.AUTO.width, 0.01f)

        // MarginPreset validation
        assertEquals(0, MarginPreset.NONE.dpValue)
        assertEquals(8, MarginPreset.SMALL.dpValue)
        assertEquals(16, MarginPreset.NORMAL.dpValue)
        assertEquals(32, MarginPreset.LARGE.dpValue)

        // QualityLevel validation
        assertEquals(95, QualityLevel.HIGH.value)
        assertEquals(75, QualityLevel.MEDIUM.value)
        assertEquals(50, QualityLevel.COMPRESSED.value)

        // Default PdfExportOptions
        val defaultOptions = PdfExportOptions()
        assertEquals(PageSize.A4, defaultOptions.pageSize)
        assertEquals(MarginPreset.NORMAL, defaultOptions.margin)
        assertEquals(QualityLevel.HIGH, defaultOptions.quality)
        assertNull(defaultOptions.documentTitle)

        // Custom PdfExportOptions
        val customOptions = PdfExportOptions(
            pageSize = PageSize.LETTER,
            margin = MarginPreset.SMALL,
            quality = QualityLevel.COMPRESSED,
            documentTitle = "Tax_Report_2026",
            author = "Scanly App"
        )
        assertEquals(PageSize.LETTER, customOptions.pageSize)
        assertEquals(MarginPreset.SMALL, customOptions.margin)
        assertEquals(QualityLevel.COMPRESSED, customOptions.quality)
        assertEquals("Tax_Report_2026", customOptions.documentTitle)
        assertEquals("Scanly App", customOptions.author)
    }

    @Test
    fun testEditorViewModel_RotationCycles() = runTest(testDispatcher) {
        val doc = Document(id = "doc1", title = "Invoice", pageCount = 1, thumbnailPath = "", createdAt = 1000L, updatedAt = 1000L)
        val page = Page(
            id = "p1",
            documentId = "doc1",
            pageNumber = 1,
            originalImagePath = "/tmp/p1.jpg",
            processedImagePath = "/tmp/p1_proc.jpg",
            thumbnailPath = "/tmp/p1_thumb.jpg",
            width = 1000,
            height = 1400,
            rotation = 0,
            filter = FilterType.ORIGINAL,
            brightness = 0f,
            contrast = 0f,
            createdAt = 1000L
        )

        every { repository.getDocumentById("doc1") } returns flowOf(doc)
        every { repository.getPages("doc1") } returns flowOf(listOf(page))

        val savedState = SavedStateHandle(mapOf("documentId" to "doc1"))
        val viewModel = EditorViewModel(savedState, repository, settingsRepository, cloudStorageService, filterService)
        testScheduler.advanceUntilIdle()

        assertEquals(0, viewModel.rotation.value)

        viewModel.rotatePage()
        assertEquals(90, viewModel.rotation.value)

        viewModel.rotatePage()
        assertEquals(180, viewModel.rotation.value)

        viewModel.rotatePage()
        assertEquals(270, viewModel.rotation.value)

        viewModel.rotatePage()
        assertEquals(0, viewModel.rotation.value)
    }

    @Test
    fun testEditorViewModel_AdjustmentsAndResets() = runTest(testDispatcher) {
        val doc = Document(id = "doc1", title = "Contract", pageCount = 1, thumbnailPath = "", createdAt = 1000L, updatedAt = 1000L)
        val page = Page(
            id = "p1",
            documentId = "doc1",
            pageNumber = 1,
            originalImagePath = "/tmp/p1.jpg",
            processedImagePath = "/tmp/p1.jpg",
            thumbnailPath = "/tmp/p1.jpg",
            width = 800,
            height = 1200,
            rotation = 0,
            filter = FilterType.ORIGINAL,
            brightness = 0f,
            contrast = 0f,
            createdAt = 1000L
        )

        every { repository.getDocumentById("doc1") } returns flowOf(doc)
        every { repository.getPages("doc1") } returns flowOf(listOf(page))

        val savedState = SavedStateHandle(mapOf("documentId" to "doc1"))
        val viewModel = EditorViewModel(savedState, repository, settingsRepository, cloudStorageService, filterService)
        testScheduler.advanceUntilIdle()

        // Adjust Brightness & Contrast
        viewModel.adjustBrightness(0.45f)
        viewModel.adjustContrast(-0.30f)
        assertEquals(0.45f, viewModel.brightness.value, 0.001f)
        assertEquals(-0.30f, viewModel.contrast.value, 0.001f)

        // Clamping test
        viewModel.adjustBrightness(1.5f)
        assertEquals(1.0f, viewModel.brightness.value, 0.001f)

        viewModel.adjustContrast(-2.0f)
        assertEquals(-1.0f, viewModel.contrast.value, 0.001f)

        // Reset Brightness only
        viewModel.resetBrightness()
        assertEquals(0.0f, viewModel.brightness.value, 0.001f)
        assertEquals(-1.0f, viewModel.contrast.value, 0.001f)

        // Reset Contrast only
        viewModel.adjustBrightness(0.2f)
        viewModel.resetContrast()
        assertEquals(0.2f, viewModel.brightness.value, 0.001f)
        assertEquals(0.0f, viewModel.contrast.value, 0.001f)

        // Reset All
        viewModel.adjustBrightness(0.5f)
        viewModel.adjustContrast(0.7f)
        viewModel.resetAdjustments()
        assertEquals(0.0f, viewModel.brightness.value, 0.001f)
        assertEquals(0.0f, viewModel.contrast.value, 0.001f)
    }

    @Test
    fun testEditorViewModel_FilterApplication() = runTest(testDispatcher) {
        val doc = Document(id = "doc1", title = "Passport", pageCount = 1, thumbnailPath = "", createdAt = 1000L, updatedAt = 1000L)
        val page = Page(
            id = "p1",
            documentId = "doc1",
            pageNumber = 1,
            originalImagePath = "/tmp/p1.jpg",
            processedImagePath = "/tmp/p1.jpg",
            thumbnailPath = "/tmp/p1.jpg",
            width = 800,
            height = 1200,
            rotation = 0,
            filter = FilterType.ORIGINAL,
            brightness = 0f,
            contrast = 0f,
            createdAt = 1000L
        )

        every { repository.getDocumentById("doc1") } returns flowOf(doc)
        every { repository.getPages("doc1") } returns flowOf(listOf(page))

        val savedState = SavedStateHandle(mapOf("documentId" to "doc1"))
        val viewModel = EditorViewModel(savedState, repository, settingsRepository, cloudStorageService, filterService)
        testScheduler.advanceUntilIdle()

        assertEquals(FilterType.ORIGINAL, viewModel.currentFilter.value)

        viewModel.applyFilter(FilterType.BLACK_WHITE)
        assertEquals(FilterType.BLACK_WHITE, viewModel.currentFilter.value)

        viewModel.applyFilter(FilterType.COLOR_BOOST)
        assertEquals(FilterType.COLOR_BOOST, viewModel.currentFilter.value)
    }

    @Test
    fun testOcrTextStatsCalculation() {
        val rawText = "Scanly is an offline Android document scanner.\nPrivacy first with zero tracking!"
        val charCount = rawText.length
        val wordCount = rawText.split(Regex("\\s+")).filter { it.isNotBlank() }.size

        assertEquals(80, charCount)
        assertEquals(12, wordCount)

        // Empty text
        val emptyText: String? = null
        val emptyCharCount = emptyText?.length ?: 0
        val emptyWordCount = emptyText?.split(Regex("\\s+"))?.filter { it.isNotBlank() }?.size ?: 0
        assertEquals(0, emptyCharCount)
        assertEquals(0, emptyWordCount)
    }

    @Test
    fun testZoomGestures_ScaleAndClampingMath() {
        val minScale = 1.0f
        val maxScale = 5.0f

        // Zoom in
        var scale = 1.0f
        val zoomFactor = 1.5f
        scale = (scale * zoomFactor).coerceIn(minScale, maxScale)
        assertEquals(1.5f, scale, 0.001f)

        // Extreme zoom in capped at 5f
        scale = (scale * 10f).coerceIn(minScale, maxScale)
        assertEquals(5.0f, scale, 0.001f)

        // Extreme zoom out capped at 1f
        scale = (scale * 0.01f).coerceIn(minScale, maxScale)
        assertEquals(1.0f, scale, 0.001f)

        // Double-tap toggle math
        val isZoomedIn = scale > 1.05f
        val toggledScale = if (isZoomedIn) 1.0f else 2.5f
        assertEquals(2.5f, toggledScale, 0.001f)

        val secondToggle = if (toggledScale > 1.05f) 1.0f else 2.5f
        assertEquals(1.0f, secondToggle, 0.001f)

        // Bounds clamping calculation
        val containerWidth = 1080f
        val containerHeight = 1920f
        val currentScale = 2.0f
        val maxOffsetX = (containerWidth * (currentScale - 1f)) / 2f
        val maxOffsetY = (containerHeight * (currentScale - 1f)) / 2f
        assertEquals(540f, maxOffsetX, 0.01f)
        assertEquals(960f, maxOffsetY, 0.01f)

        val panX = 1000f
        val clampedX = panX.coerceIn(-maxOffsetX, maxOffsetX)
        assertEquals(540f, clampedX, 0.01f)
    }
}
