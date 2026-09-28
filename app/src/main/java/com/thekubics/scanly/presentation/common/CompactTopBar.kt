package com.thekubics.scanly.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Compact drop-in replacement for the Material 3 TopAppBar: a 48dp content row
 * under the status-bar inset (88dp total on this device) with a titleMedium
 * title, instead of the stock 64dp/104dp titleLarge bar. Matches the geometry
 * HomeScreen already uses.
 *
 * Screens opt in with an explicit `import ...presentation.common.TopAppBar`,
 * which shadows the material3 star import (explicit imports win). The
 * `scrollBehavior` parameter is accepted for call-site compatibility but is
 * not acted on; `colors` only drives the container color (every call site
 * passes default content colors, equal to the ambient ones).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.containerColor)
            .windowInsetsPadding(windowInsets)
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rowScope = this
            navigationIcon()
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                    rowScope.title()
                }
            }
            rowScope.actions()
        }
    }
}
