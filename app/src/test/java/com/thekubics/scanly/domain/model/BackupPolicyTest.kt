package com.thekubics.scanly.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPolicyTest {

    @Test
    fun newInstallDoesNotUpload() {
        val settings = UserSettings()
        assertFalse(settings.cloudBackupEnabled)
        assertFalse(settings.autoSyncEnabled)
        assertFalse(BackupPolicy.automaticBackupAllowed(settings, hasActiveProvider = true))
        assertFalse(
            BackupPolicy.shouldUploadOnSave(SaveAction.SAVE_AND_UPLOAD, settings, hasActiveProvider = true)
        )
    }

    @Test
    fun saveLocalNeverUploads() {
        val settings = UserSettings(cloudBackupEnabled = true, autoSyncEnabled = true)
        assertFalse(BackupPolicy.shouldUploadOnSave(SaveAction.SAVE_LOCAL, settings, hasActiveProvider = true))
    }

    @Test
    fun uploadRequiresBackupSwitchAndDestination() {
        val ready = UserSettings(
            cloudBackupEnabled = true,
            autoSyncEnabled = false,
            defaultSaveAction = SaveAction.SAVE_AND_UPLOAD
        )
        assertTrue(BackupPolicy.shouldUploadOnSave(SaveAction.SAVE_AND_UPLOAD, ready, hasActiveProvider = true))
        assertTrue(BackupPolicy.shouldUploadOnSave(SaveAction.UPLOAD_TO_CLOUD, ready, hasActiveProvider = true))
        assertFalse(BackupPolicy.shouldUploadOnSave(SaveAction.SAVE_AND_UPLOAD, ready, hasActiveProvider = false))
        assertFalse(BackupPolicy.automaticBackupAllowed(ready, hasActiveProvider = true))
    }

    @Test
    fun automaticBackupNeedsBothSwitches() {
        val settings = UserSettings(cloudBackupEnabled = true, autoSyncEnabled = true)
        assertTrue(BackupPolicy.automaticBackupAllowed(settings, hasActiveProvider = true))
        assertFalse(BackupPolicy.automaticBackupAllowed(settings, hasActiveProvider = false))
        assertFalse(
            BackupPolicy.automaticBackupAllowed(
                settings.copy(cloudBackupEnabled = false),
                hasActiveProvider = true
            )
        )
    }
}
