package com.docscanner.app.presentation.cloud

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.docscanner.app.R
import com.docscanner.app.domain.model.CloudDocument
import com.docscanner.app.domain.model.SyncStatus
import com.scanly.data.storage.StorageProviderType
import com.docscanner.app.presentation.common.ConfirmationDialog
import com.docscanner.app.presentation.common.EmptyState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen(
    viewModel: CloudViewModel = hiltViewModel(),
    onNavigateToDashboard: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onDocumentClick: (String) -> Unit
) {
    val context = LocalContext.current
    val activeConfig by viewModel.activeConfig.collectAsState()
    val cloudDocuments by viewModel.cloudDocuments.collectAsState()
    val storageQuota by viewModel.storageQuota.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var documentToDelete by remember { mutableStateOf<CloudDocument?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val filteredDocuments = remember(cloudDocuments, searchQuery) {
        if (searchQuery.isBlank()) {
            cloudDocuments
        } else {
            cloudDocuments.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cloud_title)) },
                actions = {
                    IconButton(
                        onClick = { viewModel.triggerSync() },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = stringResource(R.string.cloud_sync_now)
                            )
                        }
                    }

                    IconButton(onClick = onNavigateToProviders) {
                        Icon(
                            imageVector = Icons.Outlined.CloudQueue,
                            contentDescription = "Storage Providers",
                            tint = if (activeConfig != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Storage Quota Quick Card
            item {
                StorageQuickCard(
                    usedBytes = storageQuota.usedBytes,
                    totalBytes = storageQuota.totalBytes,
                    usageFraction = storageQuota.usageFraction,
                    onViewDashboard = onNavigateToDashboard
                )
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search cloud documents…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            }

            // Storage Provider Setup Banner
            if (activeConfig == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Connect Personal Storage",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Configure personal Telegram, Cloudflare R2, or Google Drive for decentralized backup",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onNavigateToProviders,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Configure")
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (activeConfig!!.type) {
                                        StorageProviderType.TELEGRAM -> Icons.Default.Send
                                        StorageProviderType.CLOUDFLARE_R2 -> Icons.Default.Storage
                                        StorageProviderType.GOOGLE_DRIVE -> Icons.Default.Cloud
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeConfig!!.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Active Cloud Destination • Decentralized",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onNavigateToProviders,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Manage")
                            }
                        }
                    }
                }
            }

            // Document List or Empty State
            if (filteredDocuments.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.cloud_empty_title),
                        subtitle = stringResource(R.string.cloud_empty_subtitle),
                        icon = Icons.Outlined.CloudOff
                    )
                }
            } else {
                items(filteredDocuments, key = { it.id }) { cloudDoc ->
                    CloudDocumentCard(
                        document = cloudDoc,
                        onClick = {
                            cloudDoc.localDocumentId?.let { onDocumentClick(it) }
                        },
                        onDownload = { viewModel.downloadDocument(context, cloudDoc) },
                        onDelete = { documentToDelete = cloudDoc }
                    )
                }
            }
        }
    }

    documentToDelete?.let { doc ->
        ConfirmationDialog(
            title = stringResource(R.string.cloud_delete),
            message = stringResource(R.string.cloud_delete_confirm, doc.title),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                viewModel.deleteCloudDocument(doc)
                documentToDelete = null
            },
            onDismiss = { documentToDelete = null }
        )
    }
}

@Composable
private fun StorageQuickCard(
    usedBytes: Long,
    totalBytes: Long,
    usageFraction: Float,
    onViewDashboard: () -> Unit
) {
    val context = LocalContext.current
    val formattedUsed = Formatter.formatFileSize(context, usedBytes)
    val formattedTotal = Formatter.formatFileSize(context, totalBytes)

    val animatedProgress by animateFloatAsState(targetValue = usageFraction, label = "quickProgress")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onViewDashboard),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.cloud_storage_used, formattedUsed, formattedTotal),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(usageFraction * 100).toInt()}% utilized",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = onViewDashboard,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cloud_dashboard),
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun CloudDocumentCard(
    document: CloudDocument,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val formattedSize = Formatter.formatFileSize(context, document.fileSize)
    val formattedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(document.uploadDate))

    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!document.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = File(document.thumbnailUrl),
                        contentDescription = stringResource(R.string.cd_document_thumbnail),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (document.fileType == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // File Type Badge
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = document.fileType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"} • $formattedSize",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SyncStatusBadge(status = document.syncStatus)
                    Text(
                        text = "• $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Context Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cloud_download)) },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDownload()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cloud_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncStatusBadge(status: SyncStatus) {
    val (color, icon, textRes) = when (status) {
        SyncStatus.SYNCED -> Triple(Color(0xFF4CAF50), Icons.Default.CheckCircle, R.string.cloud_status_synced)
        SyncStatus.UPLOADING -> Triple(Color(0xFF2196F3), Icons.Default.CloudUpload, R.string.cloud_status_uploading)
        SyncStatus.DOWNLOADING -> Triple(Color(0xFF2196F3), Icons.Default.CloudDownload, R.string.cloud_status_downloading)
        SyncStatus.OFFLINE -> Triple(Color(0xFF9E9E9E), Icons.Default.CloudOff, R.string.cloud_status_offline)
        SyncStatus.SYNC_FAILED -> Triple(Color(0xFFF44336), Icons.Default.Error, R.string.cloud_status_failed)
        SyncStatus.LOCAL -> Triple(Color(0xFF757575), Icons.Default.Folder, R.string.cloud_status_local)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
