package com.thekubics.scanly.domain.model

/**
 * Represents the cloud synchronization status of a document.
 */
enum class SyncStatus {
    /** Document only exists locally and has not been marked for cloud backup. */
    LOCAL,
    /** Document is fully synchronized with the cloud. */
    SYNCED,
    /** Document is currently uploading to the cloud. */
    UPLOADING,
    /** Document is currently downloading from the cloud. */
    DOWNLOADING,
    /** Device is currently offline; pending sync is queued. */
    OFFLINE,
    /** Cloud synchronization failed; retry is available. */
    SYNC_FAILED
}
