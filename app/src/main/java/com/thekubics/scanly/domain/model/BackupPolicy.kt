package com.thekubics.scanly.domain.model

/**
 * Single rule for when a document may leave the device.
 * Local save is the default. Upload requires an active destination and an explicit backup switch.
 */
object BackupPolicy {

    fun automaticBackupAllowed(settings: UserSettings, hasActiveProvider: Boolean): Boolean =
        hasActiveProvider && settings.cloudBackupEnabled && settings.autoSyncEnabled

    /**
     * Explicit "upload" choices still require backup to be on.
     * "Save locally" never uploads from the editor; later automatic backup is the scheduler's job.
     */
    fun shouldUploadOnSave(
        action: SaveAction,
        settings: UserSettings,
        hasActiveProvider: Boolean
    ): Boolean {
        if (!hasActiveProvider || !settings.cloudBackupEnabled) return false
        return action == SaveAction.SAVE_AND_UPLOAD || action == SaveAction.UPLOAD_TO_CLOUD
    }
}
