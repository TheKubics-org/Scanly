package com.thekubics.scanly.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
 * Compact drop-in replacement for the Material 3 TopAppBar: one status-bar
 * inset plus a single title row (at least 48dp). The root scaffold must not
 * also pad for the status bar, or this spacer is applied twice.
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
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.containerColor)
    ) {
        Spacer(Modifier.windowInsetsTopHeight(windowInsets))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 4.dp),
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
