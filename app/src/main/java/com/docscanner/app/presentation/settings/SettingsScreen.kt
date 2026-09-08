package com.docscanner.app.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.docscanner.app.R
import com.docscanner.app.domain.model.SaveAction
import com.docscanner.app.domain.model.UserSettings.ThemeMode
import com.docscanner.app.presentation.common.ConfirmationDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToTrash: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToCloud: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val activeConfig by viewModel.activeConfig.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSaveActionDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category: Cloud Storage & Sync
            SettingsCard(
                categoryTitle = stringResource(R.string.settings_cloud_category),
                categoryIcon = Icons.Outlined.Cloud
            ) {
                val providerSubtitle = if (activeConfig != null) {
                    "Active: ${activeConfig!!.displayName}"
                } else {
                    "Not configured (Tap to configure)"
                }

                SettingsClickableItem(
                    title = "Storage Destinations (BYOS)",
                    subtitle = providerSubtitle,
                    onClick = onNavigateToProviders
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    title = stringResource(R.string.settings_cloud_backup),
                    subtitle = stringResource(R.string.settings_cloud_backup_desc),
                    checked = settings.cloudBackupEnabled,
                    onCheckedChange = { viewModel.toggleCloudBackup(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    title = stringResource(R.string.settings_auto_sync),
                    subtitle = stringResource(R.string.settings_auto_sync_desc),
                    checked = settings.autoSyncEnabled,
                    onCheckedChange = { viewModel.toggleAutoSync(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    title = stringResource(R.string.settings_wifi_only),
                    subtitle = stringResource(R.string.settings_wifi_only_desc),
                    checked = settings.wifiOnlyUpload,
                    onCheckedChange = { viewModel.toggleWifiOnly(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                val saveActionSubtitle = when (settings.defaultSaveAction) {
                    SaveAction.SAVE_LOCAL -> stringResource(R.string.save_dialog_save_local)
                    SaveAction.UPLOAD_TO_CLOUD -> stringResource(R.string.save_dialog_upload_cloud)
                    SaveAction.SAVE_AND_UPLOAD -> stringResource(R.string.save_dialog_save_and_upload)
                    SaveAction.ASK_EVERY_TIME -> "Ask every time"
                }

                SettingsClickableItem(
                    title = stringResource(R.string.settings_default_save_action),
                    subtitle = saveActionSubtitle,
                    onClick = { showSaveActionDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickableItem(
                    title = stringResource(R.string.settings_cloud_dashboard),
                    subtitle = "View storage quota breakdown and manage cloud cache",
                    onClick = onNavigateToDashboard
                )
            }

            // Category: Appearance
            SettingsCard(
                categoryTitle = stringResource(R.string.settings_appearance),
                categoryIcon = Icons.Outlined.Palette
            ) {
                val themeSubtitle = when (settings.theme) {
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                }

                SettingsClickableItem(
                    title = stringResource(R.string.settings_theme),
                    subtitle = themeSubtitle,
                    onClick = { showThemeDialog = true }
                )
            }

            // Category: Security
            SettingsCard(
                categoryTitle = stringResource(R.string.settings_security),
                categoryIcon = Icons.Outlined.Lock
            ) {
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_app_lock),
                    subtitle = stringResource(R.string.settings_app_lock_subtitle),
                    checked = settings.appLockEnabled,
                    onCheckedChange = { viewModel.toggleAppLock(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    title = stringResource(R.string.settings_encrypt),
                    subtitle = stringResource(R.string.settings_encrypt_subtitle),
                    checked = settings.encryptNewDocuments,
                    onCheckedChange = { viewModel.toggleEncryption(it) }
                )
            }

            // Category: Data & Storage
            SettingsCard(
                categoryTitle = stringResource(R.string.settings_data),
                categoryIcon = Icons.Outlined.Storage
            ) {
                SettingsClickableItem(
                    title = stringResource(R.string.settings_trash),
                    subtitle = stringResource(R.string.trash_empty_subtitle),
                    onClick = onNavigateToTrash
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickableItem(
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = "Remove cached files and temporary export data",
                    onClick = { showClearCacheDialog = true }
                )
            }

            // Category: About
            SettingsCard(
                categoryTitle = stringResource(R.string.settings_about),
                categoryIcon = Icons.Outlined.Info
            ) {
                SettingsInfoItem(
                    title = stringResource(R.string.settings_version),
                    value = "1.1.0 (Cloud & Offline Storage)"
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsInfoItem(
                    title = "Offline Privacy",
                    value = "100% On-Device Scanning • Optional End-to-End Cloud Backup"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Theme Selection Dialog
        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentTheme = settings.theme,
                onThemeSelected = { newTheme ->
                    viewModel.updateTheme(newTheme)
                    showThemeDialog = false
                },
                onDismiss = { showThemeDialog = false }
            )
        }

        // Save Action Selection Dialog
        if (showSaveActionDialog) {
            SaveActionSelectionDialog(
                currentAction = settings.defaultSaveAction,
                onActionSelected = { newAction ->
                    viewModel.updateDefaultSaveAction(newAction)
                    showSaveActionDialog = false
                },
                onDismiss = { showSaveActionDialog = false }
            )
        }

        // Clear Cache Confirmation Dialog
        if (showClearCacheDialog) {
            ConfirmationDialog(
                title = stringResource(R.string.settings_clear_cache),
                message = "Clear all temporary PDF exports and cached thumbnails? Your saved documents and folders will not be affected.",
                confirmLabel = "Clear Cache",
                dismissLabel = stringResource(R.string.cancel),
                onConfirm = {
                    viewModel.clearCache(context)
                    showClearCacheDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Cache cleared successfully")
                    }
                },
                onDismiss = { showClearCacheDialog = false }
            )
        }
    }
}

@Composable
private fun SettingsCard(
    categoryTitle: String,
    categoryIcon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        ) {
            Icon(
                imageVector = categoryIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = categoryTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsClickableItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = subtitle?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = subtitle?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    )
}

@Composable
private fun SettingsInfoItem(
    title: String,
    value: String
) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTheme by remember { mutableStateOf(currentTheme) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val themes = listOf(
                    ThemeMode.SYSTEM to R.string.settings_theme_system,
                    ThemeMode.LIGHT to R.string.settings_theme_light,
                    ThemeMode.DARK to R.string.settings_theme_dark
                )

                themes.forEach { (mode, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTheme = mode }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTheme == mode,
                            onClick = { selectedTheme = mode }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onThemeSelected(selectedTheme) }
            ) {
                Text(stringResource(R.string.confirm))
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
fun SaveActionSelectionDialog(
    currentAction: SaveAction,
    onActionSelected: (SaveAction) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAction by remember { mutableStateOf(currentAction) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_default_save_action)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val actions = listOf(
                    SaveAction.SAVE_LOCAL to R.string.save_dialog_save_local,
                    SaveAction.SAVE_AND_UPLOAD to R.string.save_dialog_save_and_upload,
                    SaveAction.UPLOAD_TO_CLOUD to R.string.save_dialog_upload_cloud
                )

                actions.forEach { (action, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedAction = action }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedAction == action,
                            onClick = { selectedAction = action }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onActionSelected(selectedAction) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
