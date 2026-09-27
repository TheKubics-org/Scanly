package com.thekubics.scanly.presentation.viewer.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thekubics.scanly.domain.model.MarginPreset
import com.thekubics.scanly.domain.model.PageSize
import com.thekubics.scanly.domain.model.PdfExportOptions
import com.thekubics.scanly.domain.model.QualityLevel

/**
 * Material 3 dialog for configuring PDF export settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportDialog(
    initialTitle: String = "Scanned_Document",
    onExport: (PdfExportOptions) -> Unit,
    onDismiss: () -> Unit,
    isExporting: Boolean = false
) {
    var documentTitle by remember { mutableStateOf(initialTitle) }
    var selectedPageSize by remember { mutableStateOf(PageSize.A4) }
    var selectedMargin by remember { mutableStateOf(MarginPreset.NORMAL) }
    var selectedQuality by remember { mutableStateOf(QualityLevel.HIGH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Export PDF",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PDF Title Input
                OutlinedTextField(
                    value = documentTitle,
                    onValueChange = { documentTitle = it },
                    label = { Text("File Name") },
                    singleLine = true,
                    trailingIcon = {
                        if (documentTitle.isNotBlank()) {
                            IconButton(onClick = { documentTitle = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear title")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Page Size Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Page Size",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            PageSize.A4 to "A4",
                            PageSize.LETTER to "Letter",
                            PageSize.LEGAL to "Legal",
                            PageSize.AUTO to "Auto"
                        ).forEach { (size, label) ->
                            FilterChip(
                                selected = selectedPageSize == size,
                                onClick = { selectedPageSize = size },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }

                // Quality Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Quality & Compression",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            QualityLevel.HIGH to "High (95%)",
                            QualityLevel.MEDIUM to "Medium (75%)",
                            QualityLevel.COMPRESSED to "Small (50%)"
                        ).forEach { (quality, label) ->
                            FilterChip(
                                selected = selectedQuality == quality,
                                onClick = { selectedQuality = quality },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }

                // Margins Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Page Margins",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            MarginPreset.NONE to "None (0)",
                            MarginPreset.SMALL to "Small (8)",
                            MarginPreset.NORMAL to "Normal (16)",
                            MarginPreset.LARGE to "Large (32)"
                        ).forEach { (margin, label) ->
                            FilterChip(
                                selected = selectedMargin == margin,
                                onClick = { selectedMargin = margin },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onExport(
                        PdfExportOptions(
                            pageSize = selectedPageSize,
                            margin = selectedMargin,
                            quality = selectedQuality,
                            documentTitle = documentTitle.ifBlank { "Document" },
                            author = "DocScanner"
                        )
                    )
                },
                enabled = !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exporting...")
                } else {
                    Text("Export PDF")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting
            ) {
                Text("Cancel")
            }
        }
    )
}
