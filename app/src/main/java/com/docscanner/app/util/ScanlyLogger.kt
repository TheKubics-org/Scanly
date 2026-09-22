package com.docscanner.app.util

import android.util.Log

/**
 * Centralized structured logger for Scanly.
 *
 * Security rules:
 * - NEVER log sensitive values: bot tokens, API keys, passwords, chat IDs, full file paths.
 * - ALWAYS log safe values only: document IDs (UUIDs), file sizes, provider type name,
 *   HTTP status codes, operation timing, and error categories.
 */
object ScanlyLogger {

    private const val TAG = "Scanly"

    // ── Cloud / Upload ────────────────────────────────────────────────────────

    fun cloudInfo(msg: String) {
        Log.i("$TAG/Cloud", msg)
    }

    fun cloudDebug(msg: String) {
        Log.d("$TAG/Cloud", msg)
    }

    fun cloudWarn(msg: String) {
        Log.w("$TAG/Cloud", msg)
    }

    fun cloudError(msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG/Cloud", msg, throwable)
        } else {
            Log.e("$TAG/Cloud", msg)
        }
    }

    // ── Sync Engine ───────────────────────────────────────────────────────────

    fun syncInfo(msg: String) {
        Log.i("$TAG/Sync", msg)
    }

    fun syncDebug(msg: String) {
        Log.d("$TAG/Sync", msg)
    }

    fun syncWarn(msg: String) {
        Log.w("$TAG/Sync", msg)
    }

    fun syncError(msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG/Sync", msg, throwable)
        } else {
            Log.e("$TAG/Sync", msg)
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    fun securityWarn(msg: String) {
        Log.w("$TAG/Security", msg)
    }

    fun securityError(msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG/Security", msg, throwable)
        } else {
            Log.e("$TAG/Security", msg)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Safely formats a file size for logging.
     */
    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
    }

    /**
     * Safely truncates a document ID to the first 8 chars for log brevity.
     */
    fun shortId(id: String): String = id.take(8)
}
