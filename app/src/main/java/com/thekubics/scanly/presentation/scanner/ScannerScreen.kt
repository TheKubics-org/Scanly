package com.thekubics.scanly.presentation.scanner

import android.app.Activity
import com.thekubics.scanly.presentation.common.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onNavigateToEditor: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scanState by viewModel.scanState.collectAsState()
    var showTitleDialog by remember { mutableStateOf(false) }
    var documentTitle by remember { mutableStateOf("New Document") }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.let {
                viewModel.processScanResult(it)
                showTitleDialog = true
            } ?: onNavigateBack()
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        if (scanState == ScanState.IDLE) {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(50)
                .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
                .setScannerMode(SCANNER_MODE_FULL)
                .build()
            
            val scanner = GmsDocumentScanning.getClient(options)
            
            var activityContext = context
            while (activityContext is android.content.ContextWrapper && activityContext !is Activity) {
                activityContext = activityContext.baseContext
            }
            
            if (activityContext is Activity) {
                scanner.getStartScanIntent(activityContext)
                    .addOnSuccessListener { intentSender ->
                        scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                    .addOnFailureListener {
                        onNavigateBack()
                    }
            } else {
                onNavigateBack()
            }
        }
    }

    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text("Save Document") },
            text = {
                OutlinedTextField(
                    value = documentTitle,
                    onValueChange = { documentTitle = it },
                    label = { Text("Document Title") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showTitleDialog = false
                    viewModel.createDocument(documentTitle) { docId ->
                        onNavigateToEditor(docId)
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTitleDialog = false
                    viewModel.clearScanResult()
                    onNavigateBack()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Scanning...") })
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            when (scanState) {
                ScanState.PROCESSING -> CircularProgressIndicator()
                ScanState.ERROR -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("An error occurred during scanning.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
