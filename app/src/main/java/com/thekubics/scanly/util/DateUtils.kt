package com.thekubics.scanly.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatRelative(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val minute = 60 * 1000L
        val hour = 60 * minute
        val day = 24 * hour

        return when {
            diff < minute -> "Just now"
            diff < 2 * minute -> "A minute ago"
            diff < hour -> "${diff / minute} minutes ago"
            diff < 2 * hour -> "An hour ago"
            diff < day -> "${diff / hour} hours ago"
            diff < 2 * day -> "Yesterday"
            else -> formatDate(timestamp)
        }
    }

    fun daysUntilPurge(trashedAt: Long): Int {
        val now = System.currentTimeMillis()
        val daysPassed = ((now - trashedAt) / (1000 * 60 * 60 * 24)).toInt()
        return (Constants.TRASH_RETENTION_DAYS - daysPassed).coerceAtLeast(0)
    }
}
