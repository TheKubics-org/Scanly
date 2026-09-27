package com.thekubics.scanly.presentation.editor.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thekubics.scanly.domain.model.FilterType

/**
 * Modern Material 3 interactive filter carousel component.
 */
@Composable
fun FilterSelector(
    filters: List<FilterType> = FilterType.values().toList(),
    selectedFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(filters, key = { it.name }) { filter ->
            val isSelected = selectedFilter == filter
            val (icon, tintColor, containerColor) = getFilterPresentation(filter)

            val borderColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "FilterBorderColor"
            )

            val cardContainerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "FilterContainerColor"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onFilterSelected(filter) }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardContainerColor)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(containerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = filter.displayName,
                            tint = tintColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = filter.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Returns visual iconography and color palette for each filter type.
 */
private data class FilterPresentation(
    val icon: ImageVector,
    val tintColor: Color,
    val containerColor: Color
)

@Composable
private fun getFilterPresentation(filter: FilterType): FilterPresentation {
    return when (filter) {
        FilterType.ORIGINAL -> FilterPresentation(
            icon = Icons.Outlined.Image,
            tintColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
        FilterType.AUTO_ENHANCE -> FilterPresentation(
            icon = Icons.Outlined.AutoAwesome,
            tintColor = MaterialTheme.colorScheme.tertiary,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
        FilterType.GRAYSCALE -> FilterPresentation(
            icon = Icons.Outlined.Gradient,
            tintColor = MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
        FilterType.BLACK_WHITE -> FilterPresentation(
            icon = Icons.Outlined.Contrast,
            tintColor = MaterialTheme.colorScheme.onSurface,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        FilterType.HIGH_CONTRAST -> FilterPresentation(
            icon = Icons.Outlined.Exposure,
            tintColor = Color(0xFFD32F2F),
            containerColor = Color(0xFFFFEBEE)
        )
        FilterType.COLOR_BOOST -> FilterPresentation(
            icon = Icons.Outlined.Palette,
            tintColor = Color(0xFFE91E63),
            containerColor = Color(0xFFFCE4EC)
        )
        FilterType.SHARPEN -> FilterPresentation(
            icon = Icons.Outlined.Tune,
            tintColor = Color(0xFF00796B),
            containerColor = Color(0xFFE0F2F1)
        )
        FilterType.LIGHTEN -> FilterPresentation(
            icon = Icons.Outlined.LightMode,
            tintColor = Color(0xFFF57F17),
            containerColor = Color(0xFFFFFDE7)
        )
        FilterType.DARKEN -> FilterPresentation(
            icon = Icons.Outlined.DarkMode,
            tintColor = Color(0xFF3949AB),
            containerColor = Color(0xFFE8EAF6)
        )
    }
}
