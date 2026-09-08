package com.docscanner.app.presentation.cloud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.docscanner.app.R
import com.docscanner.app.presentation.common.ConfirmationDialog
import com.docscanner.app.presentation.common.EmptyState
import com.scanly.data.storage.GoogleDriveAuthType
import com.scanly.data.storage.StorageConfig
import com.scanly.data.storage.StorageProviderType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageProvidersScreen(
    viewModel: StorageProvidersViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val configs by viewModel.configs.collectAsState()
    val activeConfig by viewModel.activeConfig.collectAsState()
    val testStates by viewModel.testState.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var configToEdit by remember { mutableStateOf<StorageConfig?>(null) }
    var configToDelete by remember { mutableStateOf<StorageConfig?>(null) }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Destinations") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Destination")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Destination")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (configs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    EmptyState(
                        title = "No Destinations Configured",
                        subtitle = "Connect your personal Telegram channel, Cloudflare R2 bucket, or Google Drive for private, decentralized backup.",
                        icon = Icons.Outlined.CloudQueue
                    )
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Storage Destination")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Your Private Cloud Backups",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                items(configs, key = { it.id }) { config ->
                    val isActive = config.id == activeConfig?.id
                    val testState = testStates[config.id]

                    ProviderCard(
                        config = config,
                        isActive = isActive,
                        testState = testState,
                        onSetActive = { viewModel.setActiveProvider(config.id) },
                        onTestConnection = { viewModel.testExistingProvider(config) },
                        onToggleEnabled = { viewModel.toggleProviderEnabled(config) },
                        onEdit = { configToEdit = config },
                        onDelete = { configToDelete = config }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddOrEditProviderDialog(
            existingConfig = null,
            viewModel = viewModel,
            onDismiss = {
                showAddDialog = false
                viewModel.clearDialogTestState()
            },
            onSave = { newConfig ->
                viewModel.saveConfig(newConfig) {
                    showAddDialog = false
                    viewModel.clearDialogTestState()
                }
            }
        )
    }

    configToEdit?.let { config ->
        AddOrEditProviderDialog(
            existingConfig = config,
            viewModel = viewModel,
            onDismiss = {
                configToEdit = null
                viewModel.clearDialogTestState()
            },
            onSave = { updatedConfig ->
                viewModel.saveConfig(updatedConfig) {
                    configToEdit = null
                    viewModel.clearDialogTestState()
                }
            }
        )
    }

    configToDelete?.let { config ->
        ConfirmationDialog(
            title = "Delete Storage Destination?",
            message = "Are you sure you want to remove \"${config.displayName}\"? User credentials will be deleted from the encrypted vault.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = {
                viewModel.deleteConfig(config.id)
                configToDelete = null
            },
            onDismiss = { configToDelete = null }
        )
    }
}

@Composable
private fun ProviderCard(
    config: StorageConfig,
    isActive: Boolean,
    testState: ProviderTestState?,
    onSetActive: () -> Unit,
    onTestConnection: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, typeName, summaryText) = when (config) {
        is StorageConfig.Telegram -> Triple(
            Icons.Outlined.Send,
            "Telegram Bot",
            "Chat ID: ${config.chatId}"
        )
        is StorageConfig.CloudflareR2 -> Triple(
            Icons.Outlined.Cloud,
            "Cloudflare R2 / S3",
            "Bucket: ${config.bucketName} (${config.region})"
        )
        is StorageConfig.GoogleDrive -> Triple(
            Icons.Outlined.AddToDrive,
            "Google Drive",
            "Auth: ${config.authType.name}" + (config.folderId?.let { " • Folder: $it" } ?: "")
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = config.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (isActive) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "$typeName • $summaryText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            // Test Results Banner
            testState?.let { state ->
                AnimatedVisibility(visible = state.isTesting || state.result != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = when {
                            state.isTesting -> MaterialTheme.colorScheme.surfaceVariant
                            state.result?.isSuccess == true -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Testing endpoint reachability…", style = MaterialTheme.typography.bodySmall)
                            } else if (state.result?.isSuccess == true) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                Text(state.result.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                            } else {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Text(state.result?.message ?: "Connection test failed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Bottom Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTestConnection,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Connection", style = MaterialTheme.typography.labelMedium)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!isActive && config.isEnabled) {
                        TextButton(onClick = onSetActive) {
                            Text("Set Active", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddOrEditProviderDialog(
    existingConfig: StorageConfig?,
    viewModel: StorageProvidersViewModel,
    onDismiss: () -> Unit,
    onSave: (StorageConfig) -> Unit
) {
    var selectedType by remember {
        mutableStateOf(existingConfig?.type ?: StorageProviderType.TELEGRAM)
    }

    var displayName by remember {
        mutableStateOf(existingConfig?.displayName ?: "")
    }

    // Telegram fields
    var tgBotToken by remember {
        mutableStateOf((existingConfig as? StorageConfig.Telegram)?.botToken ?: "")
    }
    var tgChatId by remember {
        mutableStateOf((existingConfig as? StorageConfig.Telegram)?.chatId ?: "")
    }

    // Cloudflare R2 fields
    var r2Endpoint by remember {
        mutableStateOf((existingConfig as? StorageConfig.CloudflareR2)?.endpointUrl ?: "https://")
    }
    var r2Bucket by remember {
        mutableStateOf((existingConfig as? StorageConfig.CloudflareR2)?.bucketName ?: "")
    }
    var r2AccessKey by remember {
        mutableStateOf((existingConfig as? StorageConfig.CloudflareR2)?.accessKeyId ?: "")
    }
    var r2SecretKey by remember {
        mutableStateOf((existingConfig as? StorageConfig.CloudflareR2)?.secretAccessKey ?: "")
    }
    var r2Region by remember {
        mutableStateOf((existingConfig as? StorageConfig.CloudflareR2)?.region ?: "auto")
    }

    // Google Drive fields
    var driveAuthType by remember {
        mutableStateOf((existingConfig as? StorageConfig.GoogleDrive)?.authType ?: GoogleDriveAuthType.SERVICE_ACCOUNT)
    }
    var driveCredsJson by remember {
        mutableStateOf((existingConfig as? StorageConfig.GoogleDrive)?.credentialsJson ?: "")
    }
    var driveFolderId by remember {
        mutableStateOf((existingConfig as? StorageConfig.GoogleDrive)?.folderId ?: "")
    }

    val dialogTestState by viewModel.dialogTestState.collectAsState()

    fun buildConfig(): StorageConfig {
        val id = existingConfig?.id ?: UUID.randomUUID().toString()
        val finalDisplayName = displayName.ifBlank {
            when (selectedType) {
                StorageProviderType.TELEGRAM -> "My Telegram"
                StorageProviderType.CLOUDFLARE_R2 -> "My R2 Bucket"
                StorageProviderType.GOOGLE_DRIVE -> "My Google Drive"
            }
        }
        return when (selectedType) {
            StorageProviderType.TELEGRAM -> StorageConfig.Telegram(
                id = id,
                displayName = finalDisplayName,
                botToken = tgBotToken.trim(),
                chatId = tgChatId.trim(),
                isEnabled = existingConfig?.isEnabled ?: true
            )
            StorageProviderType.CLOUDFLARE_R2 -> StorageConfig.CloudflareR2(
                id = id,
                displayName = finalDisplayName,
                endpointUrl = r2Endpoint.trim(),
                bucketName = r2Bucket.trim(),
                accessKeyId = r2AccessKey.trim(),
                secretAccessKey = r2SecretKey.trim(),
                region = r2Region.trim().ifBlank { "auto" },
                isEnabled = existingConfig?.isEnabled ?: true
            )
            StorageProviderType.GOOGLE_DRIVE -> StorageConfig.GoogleDrive(
                id = id,
                displayName = finalDisplayName,
                authType = driveAuthType,
                credentialsJson = driveCredsJson.trim(),
                folderId = driveFolderId.trim().ifBlank { null },
                isEnabled = existingConfig?.isEnabled ?: true
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingConfig == null) "Add Storage Destination" else "Edit Destination")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (existingConfig == null) {
                    Text("Select Provider Type", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == StorageProviderType.TELEGRAM,
                            onClick = { selectedType = StorageProviderType.TELEGRAM },
                            label = { Text("Telegram") },
                            leadingIcon = { Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = selectedType == StorageProviderType.CLOUDFLARE_R2,
                            onClick = { selectedType = StorageProviderType.CLOUDFLARE_R2 },
                            label = { Text("R2 / S3") },
                            leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = selectedType == StorageProviderType.GOOGLE_DRIVE,
                            onClick = { selectedType = StorageProviderType.GOOGLE_DRIVE },
                            label = { Text("Drive") },
                            leadingIcon = { Icon(Icons.Outlined.AddToDrive, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Personal Backup") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                when (selectedType) {
                    StorageProviderType.TELEGRAM -> {
                        OutlinedTextField(
                            value = tgBotToken,
                            onValueChange = { tgBotToken = it },
                            label = { Text("Bot Token") },
                            placeholder = { Text("123456789:AA...") },
                            supportingText = { Text("Create a bot via @BotFather on Telegram") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = tgChatId,
                            onValueChange = { tgChatId = it },
                            label = { Text("Target Chat or Channel ID") },
                            placeholder = { Text("e.g. -100123456789 or @my_channel") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    StorageProviderType.CLOUDFLARE_R2 -> {
                        OutlinedTextField(
                            value = r2Endpoint,
                            onValueChange = { r2Endpoint = it },
                            label = { Text("Endpoint URL") },
                            placeholder = { Text("https://<account_id>.r2.cloudflarestorage.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = r2Bucket,
                            onValueChange = { r2Bucket = it },
                            label = { Text("Bucket Name") },
                            placeholder = { Text("scanly-vault") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = r2AccessKey,
                            onValueChange = { r2AccessKey = it },
                            label = { Text("Access Key ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = r2SecretKey,
                            onValueChange = { r2SecretKey = it },
                            label = { Text("Secret Access Key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = r2Region,
                            onValueChange = { r2Region = it },
                            label = { Text("Region") },
                            placeholder = { Text("auto") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    StorageProviderType.GOOGLE_DRIVE -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = driveAuthType == GoogleDriveAuthType.SERVICE_ACCOUNT,
                                onClick = { driveAuthType = GoogleDriveAuthType.SERVICE_ACCOUNT },
                                label = { Text("Service Account") }
                            )
                            FilterChip(
                                selected = driveAuthType == GoogleDriveAuthType.OAUTH2,
                                onClick = { driveAuthType = GoogleDriveAuthType.OAUTH2 },
                                label = { Text("OAuth2 Token") }
                            )
                        }

                        OutlinedTextField(
                            value = driveCredsJson,
                            onValueChange = { driveCredsJson = it },
                            label = { Text(if (driveAuthType == GoogleDriveAuthType.SERVICE_ACCOUNT) "Service Account JSON" else "OAuth2 Access Token") },
                            placeholder = { Text(if (driveAuthType == GoogleDriveAuthType.SERVICE_ACCOUNT) "Paste complete service account JSON" else "Paste access token") },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = driveFolderId,
                            onValueChange = { driveFolderId = it },
                            label = { Text("Folder ID (Optional)") },
                            placeholder = { Text("Leave empty for Root folder") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Test Connection in Dialog
                OutlinedButton(
                    onClick = {
                        val draft = buildConfig()
                        viewModel.testDraftProvider(draft)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !dialogTestState.isTesting
                ) {
                    if (dialogTestState.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying...")
                    } else {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection")
                    }
                }

                dialogTestState.result?.let { res ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (res.isSuccess) Color(0xFF4CAF50).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (res.isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = res.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (res.isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(buildConfig()) }) {
                Text("Save Destination")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
