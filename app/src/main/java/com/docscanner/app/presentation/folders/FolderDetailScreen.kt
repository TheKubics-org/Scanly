package com.docscanner.app.presentation.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.docscanner.app.presentation.home.RenameDocumentDialog
import com.docscanner.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    viewModel: FolderDetailViewModel = hiltViewModel(),
    onNavigateToViewer: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val documents by viewModel.documents.collectAsState()
    val folderName by viewModel.folderName.collectAsState()
    val folderColor by viewModel.folderColor.collectAsState()

    var docToRename by remember { mutableStateOf<Document?>(null) }
    var docToTrash by remember { mutableStateOf<Document?>(null) }
    var docToRemoveFromFolder by remember { mutableStateOf<Document?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(Color(folderColor), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = folderName.ifBlank { "Folder" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (documents.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.FolderOpen,
                    title = "This folder is empty",
                    subtitle = "Organize documents into this folder from the Home screen"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(documents, key = { it.id }) { document ->
                        FolderDocumentCard(
                            document = document,
                            onClick = { onNavigateToViewer(document.id) },
                            onRename = { docToRename = document },
                            onRemoveFromFolder = { docToRemoveFromFolder = document },
                            onMoveToTrash = { docToTrash = document }
                        )
                    }
                }
            }
        }

        // Rename Dialog
        docToRename?.let { doc ->
            RenameDocumentDialog(
                currentTitle = doc.title,
                onConfirm = { newTitle ->
                    viewModel.renameDocument(doc.id, newTitle)
                    docToRename = null
                },
                onDismiss = { docToRename = null }
            )
        }

        // Remove from Folder Confirmation
        docToRemoveFromFolder?.let { doc ->
            ConfirmationDialog(
                title = "Remove from Folder",
                message = "Remove \"${doc.title}\" from this folder? It will remain accessible on the Home screen.",
                confirmLabel = "Remove",
                dismissLabel = stringResource(R.string.cancel),
                onConfirm = {
                    viewModel.removeFromFolder(doc.id)
                    docToRemoveFromFolder = null
                },
                onDismiss = { docToRemoveFromFolder = null }
            )
        }

        // Move to Trash Confirmation
        docToTrash?.let { doc ->
            ConfirmationDialog(
                title = stringResource(R.string.viewer_delete),
                message = "Move \"${doc.title}\" to trash? You can restore it anytime from Trash.",
                confirmLabel = stringResource(R.string.delete),
                dismissLabel = stringResource(R.string.cancel),
                isDestructive = true,
                onConfirm = {
                    viewModel.moveToTrash(doc.id)
                    docToTrash = null
                },
                onDismiss = { docToTrash = null }
            )
        }
    }
}

@Composable
private fun FolderDocumentCard(
    document: Document,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    onMoveToTrash: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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

                if (document.isEncrypted) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopStart)
                            .size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Encrypted",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (document.pageCount == 1) "1 page" else "${document.pageCount} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = DateUtils.formatRelative(document.updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Document actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from Folder") },
                        leadingIcon = { Icon(Icons.Default.FolderOff, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRemoveFromFolder()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.viewer_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onMoveToTrash()
                        }
                    )
                }
            }
        }
    }
}
