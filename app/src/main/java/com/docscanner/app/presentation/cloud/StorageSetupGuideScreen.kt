package com.docscanner.app.presentation.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docscanner.app.R
import com.scanly.data.storage.StorageProviderType

// ─────────────────────────────────────────────────────────────────────────────
// Entry composable — dispatches to per-provider guide
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSetupGuideScreen(
    providerType: StorageProviderType,
    onNavigateBack: () -> Unit
) {
    val (title, icon) = when (providerType) {
        StorageProviderType.TELEGRAM -> "Telegram Bot Setup" to Icons.Outlined.Send
        StorageProviderType.CLOUDFLARE_R2 -> "Cloudflare R2 Setup" to Icons.Outlined.Cloud
        StorageProviderType.GOOGLE_DRIVE -> "Google Drive Setup" to Icons.Outlined.AddToDrive
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero card
            GuideHeroCard(
                icon = icon,
                providerType = providerType
            )

            // Security note
            SecurityInfoCard()

            // Per-provider step-by-step
            when (providerType) {
                StorageProviderType.TELEGRAM -> TelegramGuide()
                StorageProviderType.CLOUDFLARE_R2 -> CloudflareR2Guide()
                StorageProviderType.GOOGLE_DRIVE -> GoogleDriveGuide()
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GuideHeroCard(icon: ImageVector, providerType: StorageProviderType) {
    val description = when (providerType) {
        StorageProviderType.TELEGRAM ->
            "Use a private Telegram bot to send your scans to a personal channel or group. Free, zero storage limits, and fully under your control."
        StorageProviderType.CLOUDFLARE_R2 ->
            "Store scans in your own Cloudflare R2 bucket — S3-compatible, free egress, and globally distributed. Requires a Cloudflare account."
        StorageProviderType.GOOGLE_DRIVE ->
            "Back up scans directly into your Google Drive using a service account. Files land in a folder of your choosing, private to your account."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Security info card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SecurityInfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1B5E20).copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Your credentials stay on your device",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "All tokens and API keys are stored in hardware-backed encrypted storage (AES-256-GCM). Scanly never sends them to any server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Step components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun GuideStep(
    stepNumber: Int,
    title: String,
    body: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Step circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            body()
        }
    }
}

@Composable
private fun StepBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CodeLabel(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun WarningNote(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TipNote(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0D47A1).copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF1565C0),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TELEGRAM GUIDE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TelegramGuide() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("How to Set Up a Telegram Bot")

        GuideStep(1, "Create your bot via @BotFather") {
            StepBody("Open Telegram and search for @BotFather. Start a chat and send:")
            CodeLabel("/newbot")
            StepBody("Follow the prompts: enter a name, then a username ending in 'bot'. BotFather will reply with your Bot Token.")
        }

        GuideStep(2, "Copy the Bot Token") {
            StepBody("It looks like:")
            CodeLabel("123456789:AAFsXz...")
            WarningNote("Treat this like a password. Never share it publicly. Scanly stores it in encrypted hardware storage only.")
        }

        GuideStep(3, "Create a private channel or group") {
            StepBody("In Telegram, create a new Private Channel (or Private Group). This is where your scans will be sent.")
            TipNote("Use a private channel for cleaner file storage. You can still access your backups from the Telegram app at any time.")
        }

        GuideStep(4, "Add your bot as Administrator") {
            StepBody("Go to your channel/group → Manage → Administrators → Add Administrator → search for your bot's username.\n\nEnable the 'Post Messages' permission.")
            WarningNote("Without Administrator access, the bot cannot send files to the channel and uploads will fail.")
        }

        GuideStep(5, "Get the Channel ID") {
            StepBody("You need the channel's numeric ID. Options:")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StepBody("• Forward a message from your channel to @username_to_id_bot")
                StepBody("• Or open the channel link in a browser — the ID is in the URL")
                StepBody("• For private channels it looks like: -1001234567890")
            }
            TipNote("If your channel is public (e.g. t.me/mychannel), you can also use @mychannel directly.")
        }

        GuideStep(6, "Enter credentials in Scanly") {
            StepBody("Go to Cloud → Add Destination → Telegram. Enter:")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CodeLabel("Bot Token: 123456789:AAFsXz...")
                CodeLabel("Chat ID:   -1001234567890")
            }
            StepBody("Tap 'Test Connection' to verify everything works, then Save.")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CLOUDFLARE R2 GUIDE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CloudflareR2Guide() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("How to Set Up Cloudflare R2")

        TipNote("Cloudflare R2 is free for up to 10 GB storage and 1 million write operations per month. No egress fees.")

        GuideStep(1, "Create a Cloudflare account") {
            StepBody("Go to dash.cloudflare.com and sign up or log in. Navigate to R2 Object Storage from the left sidebar.")
        }

        GuideStep(2, "Create a new R2 Bucket") {
            StepBody("Click 'Create Bucket'. Enter a name (e.g. scanly-vault). Choose your preferred region or leave as auto.")
            WarningNote("Keep the bucket PRIVATE. Do not enable public access — your documents should not be publicly accessible.")
        }

        GuideStep(3, "Create an R2 API Token") {
            StepBody("Go to R2 → Manage R2 API Tokens → Create API Token.\n\nSet permissions to 'Object Read & Write' and scope it to your bucket only.")
            StepBody("After creating, copy:")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CodeLabel("Access Key ID")
                CodeLabel("Secret Access Key")
            }
            WarningNote("The Secret Key is shown only once. Copy it immediately and keep it safe.")
        }

        GuideStep(4, "Get your Endpoint URL") {
            StepBody("Go back to your bucket → Settings → S3 API Endpoint. It looks like:")
            CodeLabel("https://<account_id>.r2.cloudflarestorage.com")
        }

        GuideStep(5, "Enter credentials in Scanly") {
            StepBody("Go to Cloud → Add Destination → R2 / S3. Enter:")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CodeLabel("Endpoint: https://abc123.r2.cloudflarestorage.com")
                CodeLabel("Bucket: scanly-vault")
                CodeLabel("Access Key ID: your-access-key")
                CodeLabel("Secret Key: your-secret-key")
                CodeLabel("Region: auto")
            }
            StepBody("Tap 'Test Connection' to verify, then Save.")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GOOGLE DRIVE GUIDE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GoogleDriveGuide() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("How to Set Up Google Drive")

        TipNote("This uses a Service Account — a robot Google account that can write to your Drive without needing interactive login.")

        GuideStep(1, "Create a Google Cloud project") {
            StepBody("Go to console.cloud.google.com.\n\nClick 'Select a project' → 'New Project'. Give it a name like 'Scanly Backup'.")
        }

        GuideStep(2, "Enable the Google Drive API") {
            StepBody("In your project, go to APIs & Services → Library. Search for 'Google Drive API' and click Enable.")
        }

        GuideStep(3, "Create a Service Account") {
            StepBody("Go to APIs & Services → Credentials → Create Credentials → Service Account.\n\nGive it a name, click Done.")
        }

        GuideStep(4, "Download the JSON key") {
            StepBody("Click your new service account → Keys tab → Add Key → Create New Key → JSON.\n\nA .json file will download automatically.")
            WarningNote("This JSON file contains private key material. Keep it safe and delete it after pasting into Scanly.")
        }

        GuideStep(5, "Share your Drive folder with the service account") {
            StepBody("Create a folder in Google Drive. Right-click → Share.\n\nShare it with the service account email address (found in the JSON under 'client_email').")
            TipNote("If you want files at the root of Drive, you can skip folder sharing — just leave the Folder ID blank in Scanly.")
        }

        GuideStep(6, "Get the Folder ID (optional)") {
            StepBody("Open your target folder in Google Drive. The Folder ID is the string at the end of the URL:")
            CodeLabel("drive.google.com/drive/folders/<FOLDER_ID>")
        }

        GuideStep(7, "Enter credentials in Scanly") {
            StepBody("Go to Cloud → Add Destination → Google Drive. Paste the entire JSON file content into the 'Service Account JSON' field. Add the Folder ID if needed.")
            StepBody("Tap 'Test Connection' to verify, then Save.")
        }
    }
}
