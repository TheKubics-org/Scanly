package com.docscanner.app.presentation.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.docscanner.app.domain.model.Page
import com.docscanner.app.presentation.viewer.components.OcrResultSheet
import com.docscanner.app.presentation.viewer.components.PdfExportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    onNavigateToEditor: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val document by viewModel.document.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val ocrText by viewModel.ocrText.collectAsState()
    val ocrLoading by viewModel.ocrLoading.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showOcrSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    // Track when current page changes to notify viewModel
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setPage(pagerState.currentPage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = document?.title ?: "Document Viewer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Primary Action 1: Edit
                    IconButton(onClick = {
                        document?.id?.let { onNavigateToEditor(it) }
                    }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit Document")
                    }

                    // Primary Action 2: Share / Export PDF
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share as PDF")
                    }

                    // Primary Action 3: OCR Text Recognition
                    IconButton(onClick = {
                        showOcrSheet = true
                        viewModel.runOcr(context)
                    }) {
                        Icon(Icons.Outlined.DocumentScanner, contentDescription = "Extract Text (OCR)")
                    }

                    // Overflow Menu
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = {
                                Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null)
                            },
                            onClick = {
                                showOverflowMenu = false
                                renameInput = document?.title ?: ""
                                showRenameDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Export & Share PDF") },
                            leadingIcon = {
                                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                            },
                            onClick = {
                                showOverflowMenu = false
                                showExportDialog = true
                            }
                        )

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Move to Trash",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (pages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No pages found in document",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Zoomable Horizontal Pager Container with Navigation Arrows
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            val page = pages.getOrNull(pageIndex)
                            if (page != null) {
                                ZoomablePageItem(page = page)
                            }
                        }

                        // Floating Previous Arrow
                        if (pages.size > 1 && pagerState.currentPage > 0) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 12.dp)
                                    .size(44.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Page",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Floating Next Arrow
                        if (pages.size > 1 && pagerState.currentPage < pages.size - 1) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp)
                                    .size(44.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Next Page",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Floating Page Indicator Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp, top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                            tonalElevation = 3.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Page ${pagerState.currentPage + 1} of ${pages.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // PDF Export Dialog
    if (showExportDialog) {
        PdfExportDialog(
            initialTitle = (document?.title ?: "Document").replace(" ", "_"),
            onExport = { options ->
                showExportDialog = false
                viewModel.exportPdf(context, options)
            },
            onDismiss = { showExportDialog = false }
        )
    }

    // OCR Result Bottom Sheet
    if (showOcrSheet) {
        OcrResultSheet(
            text = ocrText,
            isLoading = ocrLoading,
            onCopy = { viewModel.copyOcrText(context) },
            onDismiss = { showOcrSheet = false }
        )
    }

    // Move to Trash Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Move to Trash?") },
            text = {
                Text("Are you sure you want to move \"${document?.title ?: "this document"}\" to trash? You can restore it anytime from Trash.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteDocument()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Document Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameDocument(renameInput.trim())
                        }
                        showRenameDialog = false
                    },
                    enabled = renameInput.isNotBlank()
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Interactive pinch-to-zoom and pan container for scanned pages with bounds clamping and double-tap reset.
 */
@Composable
private fun ZoomablePageItem(page: Page) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(page.id) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    }
                )
            }
            .then(
                if (scale > 1.05f) {
                    Modifier.pointerInput(page.id, scale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale

                            if (newScale <= 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                val newOffsetX = (offset.x + pan.x * newScale).coerceIn(-maxOffsetX, maxOffsetX)
                                val newOffsetY = (offset.y + pan.y * newScale).coerceIn(-maxOffsetY, maxOffsetY)
                                offset = Offset(newOffsetX, newOffsetY)
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = java.io.File(page.processedImagePath.ifBlank { page.originalImagePath }),
            contentDescription = "Document Page ${page.pageNumber}",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    rotationZ = page.rotation.toFloat()
                },
            contentScale = ContentScale.Fit
        )
    }
}
