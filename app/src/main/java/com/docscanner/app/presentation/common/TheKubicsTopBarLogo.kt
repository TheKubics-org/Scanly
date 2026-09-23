package com.docscanner.app.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.docscanner.app.R

/**
 * TheKubics branded logo shown in every screen's TopAppBar.
 * Tinted with the current surface-variant color so it matches both light and dark themes.
 *
 * Usage:
 * ```
 * TopAppBar(
 *   title = { ... },
 *   actions = { TheKubicsTopBarLogo() }
 * )
 * ```
 */
@Composable
fun TheKubicsTopBarLogo(
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.ic_thekubics_logo),
        contentDescription = "TheKubics",
        modifier = modifier
            .height(24.dp)
            .padding(end = 12.dp),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
    )
}
