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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

@Composable
fun TheKubicsTopBarLogo(
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.img_thekubics_logo),
        contentDescription = "TheKubics Logo",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .padding(end = 12.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
    )
}
