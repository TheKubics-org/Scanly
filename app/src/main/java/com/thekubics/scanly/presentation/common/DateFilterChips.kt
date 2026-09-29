package com.thekubics.scanly.presentation.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thekubics.scanly.R
import com.thekubics.scanly.domain.model.DateFilter

@Composable
fun DateFilterChips(
    selected: DateFilter,
    onSelect: (DateFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DateFilter.values().forEach { filter ->
            val labelRes = when (filter) {
                DateFilter.ALL -> R.string.home_filter_all
                DateFilter.TODAY -> R.string.home_filter_today
                DateFilter.THIS_WEEK -> R.string.home_filter_week
                DateFilter.THIS_MONTH -> R.string.home_filter_month
            }
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}