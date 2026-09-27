package com.thekubics.scanly.domain.model

/**
 * Action taken after scanning/editing a document regarding saving and cloud uploading.
 */
enum class SaveAction {
    /** Save to local storage only. */
    SAVE_LOCAL,
    /** Upload directly to cloud storage. */
    UPLOAD_TO_CLOUD,
    /** Save to local storage and sync to cloud. */
    SAVE_AND_UPLOAD,
    /** Prompt the user each time a document is created/saved. */
    ASK_EVERY_TIME
}
