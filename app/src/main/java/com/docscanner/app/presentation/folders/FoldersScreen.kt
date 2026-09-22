package com.docscanner.app.presentation.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.docscanner.app.R
import com.docscanner.app.domain.model.Folder
import com.docscanner.app.presentation.common.ConfirmationDialog
import com.docscanner.app.presentation.common.EmptyState
import com.docscanner.app.presentation.theme.FolderColorPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    viewModel: FoldersViewModel = hiltViewModel(),
    onNavigateToFolder: (String) -> Unit
) {
    val folders by viewModel.folders.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToChangeColor by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.folders_title)) },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = stringResource(R.string.folders_create)
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
            if (folders.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.folders_empty_title),
                    subtitle = stringResource(R.string.folders_empty_subtitle),
                    actionLabel = stringResource(R.string.folders_create),
                    onAction = { showCreateDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(folders, key = { it.id }) { folder ->
                        FolderCardItem(
                            folder = folder,
                            onClick = { onNavigateToFolder(folder.id) },
                            onRename = { folderToRename = folder },
                            onChangeColor = { folderToChangeColor = folder },
                            onDelete = { folderToDelete = folder }
                        )
                    }
                }
            }
        }

        // Create Folder Dialog
        if (showCreateDialog) {
            FolderDialog(
                title = stringResource(R.string.folders_create),
                confirmLabel = stringResource(R.string.folders_create),
                initialName = "",
                initialColor = FolderColorPresets.first(),
                onConfirm = { name, color ->
                    viewModel.createFolder(name, color)
                    showCreateDialog = false
                },
                onDismiss = { showCreateDialog = false }
            )
        }

        // Rename Folder Dialog
        folderToRename?.let { folder ->
            FolderDialog(
                title = stringResource(R.string.rename),
                confirmLabel = stringResource(R.string.save),
                initialName = folder.name,
                initialColor = folder.color,
                isRenameOnly = true,
                onConfirm = { newName, _ ->
                    viewModel.renameFolder(folder.id, newName)
                    folderToRename = null
                },
                onDismiss = { folderToRename = null }
            )
        }

        // Change Color Dialog
        folderToChangeColor?.let { folder ->
            FolderDialog(
                title = "Folder Color",
                confirmLabel = stringResource(R.string.save),
                initialName = folder.name,
                initialColor = folder.color,
                isColorOnly = true,
                onConfirm = { _, newColor ->
                    viewModel.changeColor(folder.id, newColor)
                    folderToChangeColor = null
                },
                onDismiss = { folderToChangeColor = null }
            )
        }

        // Delete Folder Confirmation Dialog
        folderToDelete?.let { folder ->
            ConfirmationDialog(
                title = "Delete Folder",
                message = stringResource(R.string.folders_delete_confirm),
                confirmLabel = stringResource(R.string.delete),
                dismissLabel = stringResource(R.string.cancel),
                isDestructive = true,
                onConfirm = {
                    viewModel.deleteFolder(folder.id)
                    folderToDelete = null
                },
                onDismiss = { folderToDelete = null }
            )
        }
    }
}

@Composable
fun FolderCardItem(
    folder: Folder,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onChangeColor: () -> Unit,
    onDelete: () -> Unit
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
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tinted folder badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(folder.color).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = Color(folder.color),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.folders_document_count, folder.documentCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Folder actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Change Color") },
                        leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onChangeColor()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
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
fun FolderDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialColor: Long,
    isRenameOnly: Boolean = false,
    isColorOnly: Boolean = false,
    onConfirm: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isColorOnly) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.folders_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!isRenameOnly) {
                    Text(
                        text = "Choose Color",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FolderColorPresets.take(4).forEach { colorValue ->
                            ColorSwatchItem(
                                colorValue = colorValue,
                                isSelected = selectedColor == colorValue,
                                onClick = { selectedColor = colorValue }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FolderColorPresets.drop(4).take(4).forEach { colorValue ->
                            ColorSwatchItem(
                                colorValue = colorValue,
                                isSelected = selectedColor == colorValue,
                                onClick = { selectedColor = colorValue }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isColorOnly || name.isNotBlank()) {
                        onConfirm(name.trim(), selectedColor)
                    }
                },
                enabled = isColorOnly || name.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ColorSwatchItem(
    colorValue: Long,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(colorValue))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
