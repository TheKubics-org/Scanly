package com.docscanner.app.presentation.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.docscanner.app.domain.model.FilterType
import com.docscanner.app.domain.model.Page
import com.docscanner.app.domain.model.SaveAction
import com.docscanner.app.presentation.editor.components.AdjustmentsPanel
import com.docscanner.app.presentation.editor.components.FilterSelector
import com.docscanner.app.presentation.editor.components.SaveActionDialog

enum class EditorTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    FILTERS("Filters", Icons.Outlined.PhotoFilter),
    ADJUSTMENTS("Adjust", Icons.Outlined.Tune),
    PAGES("Pages", Icons.Outlined.Collections)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateToViewer: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val document by viewModel.document.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val selectedPageIndex by viewModel.selectedPageIndex.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val contrast by viewModel.contrast.collectAsState()
    val rotation by viewModel.rotation.collectAsState()
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(EditorTab.FILTERS) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSaveActionDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = selectedPageIndex, pageCount = { pages.size })

    // Sync pager scroll with selected page in ViewModel
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedPageIndex) {
            viewModel.selectPage(pagerState.currentPage)
        }
    }

    // Sync ViewModel selection with pager
    LaunchedEffect(selectedPageIndex) {
        if (pagerState.currentPage != selectedPageIndex && selectedPageIndex in pages.indices) {
            pagerState.animateScrollToPage(selectedPageIndex)
        }
    }

    // Add more pages picker launcher
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addMorePages(uris)
        }
    }

    val currentPage = pages.getOrNull(selectedPageIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document?.title ?: "Edit Document",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (pages.isNotEmpty()) {
                            Text(
                                text = "Page ${selectedPageIndex + 1} of ${pages.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        IconButton(onClick = {
                            if (settings.defaultSaveAction == SaveAction.ASK_EVERY_TIME) {
                                showSaveActionDialog = true
                            } else {
                                viewModel.saveChanges(action = settings.defaultSaveAction) {
                                    document?.id?.let { onNavigateToViewer(it) }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save Changes", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.height(72.dp)
                ) {
                    EditorTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Main Document Image Preview Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (pages.isEmpty()) {
                    Text(
                        text = "No pages found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIdx ->
                        val page = pages.getOrNull(pageIdx)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (pageIdx == selectedPageIndex && previewBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = previewBitmap!!.asImageBitmap(),
                                    contentDescription = "Document Preview Page ${pageIdx + 1}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                        .graphicsLayer(rotationZ = rotation.toFloat()),
                                    contentScale = ContentScale.Fit
                                )
                            } else if (page != null) {
                                AsyncImage(
                                    model = java.io.File(page.processedImagePath.ifBlank { page.originalImagePath }),
                                    contentDescription = "Document Page ${page.pageNumber}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                        .then(
                                            if (pageIdx == selectedPageIndex) Modifier.graphicsLayer(rotationZ = rotation.toFloat())
                                            else Modifier
                                        ),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    // Floating Page indicator badge
                    if (pages.size > 1) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                        ) {
                            Text(
                                text = "Page ${pagerState.currentPage + 1} of ${pages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Floating Navigation Arrows
                    if (pages.size > 1 && pagerState.currentPage > 0) {
                        FilledTonalIconButton(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp)
                                .size(36.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Previous Page",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (pages.size > 1 && pagerState.currentPage < pages.size - 1) {
                        FilledTonalIconButton(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .size(36.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Next Page",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Quick thumbnail carousel if multiple pages and not in Pages tab
            if (pages.size > 1 && selectedTab != EditorTab.PAGES) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(pages.size) { index ->
                        val page = pages[index]
                        val isCurrent = index == selectedPageIndex
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .size(width = 44.dp, height = 58.dp)
                                .clickable {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.BottomCenter) {
                                AsyncImage(
                                    model = java.io.File(page.processedImagePath.ifBlank { page.originalImagePath }),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tabbed Tool Panel Container
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    label = "EditorTabContentTransition"
                ) { tab ->
                    when (tab) {
                        EditorTab.FILTERS -> {
                            FilterSelector(
                                filters = FilterType.values().toList(),
                                selectedFilter = currentFilter,
                                onFilterSelected = viewModel::setFilter,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        EditorTab.ADJUSTMENTS -> {
                            AdjustmentsPanel(
                                brightness = brightness,
                                contrast = contrast,
                                onBrightnessChange = viewModel::setBrightness,
                                onContrastChange = viewModel::setContrast,
                                onResetBrightness = { viewModel.setBrightness(0f) },
                                onResetContrast = { viewModel.setContrast(0f) },
                                onResetAll = { viewModel.setBrightness(0f); viewModel.setContrast(0f) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        EditorTab.PAGES -> {
                            PagesOrganizePanel(
                                pages = pages,
                                selectedPageIndex = selectedPageIndex,
                                onPageSelected = viewModel::selectPage,
                                onRotatePage = viewModel::rotatePage,
                                onDeletePage = {
                                    if (currentPage != null) {
                                        showDeleteConfirmDialog = true
                                    }
                                },
                                onDuplicatePage = {
                                    currentPage?.let { viewModel.duplicatePage(it.id) }
                                },
                                onAddPages = {
                                    pickImagesLauncher.launch("image/*")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Page Confirmation Dialog
    if (showDeleteConfirmDialog && currentPage != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Page?") },
            text = {
                Text(
                    "Are you sure you want to delete Page ${selectedPageIndex + 1}? " +
                    if (pages.size <= 1) "This will remove the only page in the document." else "This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePage(currentPage.id) {
                            onNavigateBack()
                        }
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSaveActionDialog) {
        SaveActionDialog(
            onDismiss = { showSaveActionDialog = false },
            onActionSelected = { action, rememberChoice ->
                showSaveActionDialog = false
                viewModel.saveChanges(action = action, rememberAction = rememberChoice) {
                    document?.id?.let { onNavigateToViewer(it) }
                }
            }
        )
    }
}

/**
 * Tab 3: Pages & Organize tool panel.
 */
@Composable
private fun PagesOrganizePanel(
    pages: List<Page>,
    selectedPageIndex: Int,
    onPageSelected: (Int) -> Unit,
    onRotatePage: () -> Unit,
    onDeletePage: () -> Unit,
    onDuplicatePage: () -> Unit,
    onAddPages: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Page Thumbnails Strip
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                val isSelected = index == selectedPageIndex

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPageSelected(index) }
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 76.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = java.io.File(page.thumbnailPath.ifBlank { page.processedImagePath }),
                            contentDescription = "Page ${index + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )

                        // Page Number Badge
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rotate 90
            FilledTonalButton(
                onClick = onRotatePage,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.RotateRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rotate 90°", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }

            // Duplicate
            OutlinedButton(
                onClick = onDuplicatePage,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Duplicate", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }

            // Add Pages
            OutlinedButton(
                onClick = onAddPages,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }

            // Delete Page
            IconButton(
                onClick = onDeletePage,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete Page"
                )
            }
        }
    }
}
