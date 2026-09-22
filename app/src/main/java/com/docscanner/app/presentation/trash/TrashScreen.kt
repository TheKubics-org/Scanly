package com.docscanner.app.presentation.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.docscanner.app.R
import com.docscanner.app.domain.model.Document
import com.docscanner.app.presentation.common.ConfirmationDialog
import com.docscanner.app.presentation.common.EmptyState
import com.docscanner.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: TrashViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val trashedDocs by viewModel.trashedDocuments.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var docToDeletePermanently by remember { mutableStateOf<Document?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (trashedDocs.isNotEmpty()) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.trash_empty_all),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showEmptyTrashDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (trashedDocs.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.DeleteOutline,
                    title = stringResource(R.string.trash_empty_title),
                    subtitle = stringResource(R.string.trash_empty_subtitle)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(trashedDocs, key = { it.id }) { doc ->
                        TrashItemCard(
                            document = doc,
                            onRestore = { viewModel.restoreDocument(doc.id) },
                            onDeletePermanently = { docToDeletePermanently = doc }
                        )
                    }
                }
            }
        }

        // Empty All Trash Confirmation Dialog
        if (showEmptyTrashDialog) {
            ConfirmationDialog(
                title = stringResource(R.string.trash_empty_all),
                message = stringResource(R.string.trash_empty_confirm),
                confirmLabel = stringResource(R.string.delete),
                dismissLabel = stringResource(R.string.cancel),
                isDestructive = true,
                onConfirm = {
                    viewModel.emptyTrash()
                    showEmptyTrashDialog = false
                },
                onDismiss = { showEmptyTrashDialog = false }
            )
        }

        // Permanently Delete Single Item Confirmation Dialog
        docToDeletePermanently?.let { doc ->
            ConfirmationDialog(
                title = stringResource(R.string.trash_delete_permanent),
                message = stringResource(R.string.trash_delete_confirm, doc.title),
                confirmLabel = stringResource(R.string.delete),
                dismissLabel = stringResource(R.string.cancel),
                isDestructive = true,
                onConfirm = {
                    viewModel.permanentlyDelete(doc.id)
                    docToDeletePermanently = null
                },
                onDismiss = { docToDeletePermanently = null }
            )
        }
    }
}

@Composable
private fun TrashItemCard(
    document: Document,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    val daysRemaining = DateUtils.daysUntilPurge(document.trashedAt ?: document.updatedAt)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (document.thumbnailPath.isNotBlank()) {
                    AsyncImage(
                        model = java.io.File(document.thumbnailPath),
                        contentDescription = stringResource(R.string.cd_document_thumbnail),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(R.string.trash_days_remaining, daysRemaining),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRestore) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = stringResource(R.string.trash_restore),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDeletePermanently) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = stringResource(R.string.trash_delete_permanent),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
