package com.thekubics.scanly.util

object Constants {
    const val DB_NAME = "docscanner_db"
    const val DATASTORE_NAME = "docscanner_settings"
    const val DOCUMENTS_DIR = "documents"
    const val THUMBNAILS_DIR = "thumbnails"
    const val PDF_EXPORTS_DIR = "pdf_exports"

    const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    
    const val MAX_SCAN_PAGES = 50
    const val TRASH_RETENTION_DAYS = 30
    const val MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024
    const val THUMBNAIL_MAX_SIZE = 512

    // Bump THUMBNAIL_VERSION whenever thumbnail sizing/quality changes so that
    // existing thumbnails are silently regenerated once on first launch.
    const val PREFS_FILE = "scanly_prefs"
    const val PREF_THUMBNAIL_GEN = "thumbnail_gen_version"
    const val THUMBNAIL_VERSION = 2
    
    const val SCAN_CHANNEL_ID = "scan_channel"
}

