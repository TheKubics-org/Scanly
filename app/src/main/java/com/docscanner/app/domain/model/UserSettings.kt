package com.docscanner.app.domain.model

/**
 * Represents user preferences and settings for the application.
 *
 * @property theme App theme preference.
 * @property defaultFilter Default filter applied to new captures.
 * @property defaultPageSize Default page size when exporting to PDF.
 * @property defaultPdfQuality Default image quality when exporting to PDF.
 * @property defaultMargin Default margin when exporting to PDF.
 * @property appLockEnabled Whether the app requires biometric/PIN authentication to open.
 * @property hasSeenOnboarding Whether the user has completed the onboarding flow.
 * @property encryptNewDocuments Whether new documents should be encrypted by default.
 * @property cloudBackupEnabled Whether cloud backup is enabled.
 * @property autoSyncEnabled Whether documents sync automatically in background.
 * @property wifiOnlyUpload Whether uploads are restricted to Wi-Fi connections.
 * @property defaultSaveAction Default action after document capture/save.
 * @property uploadQuality Image quality preset used for cloud uploads.
 */
data class UserSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultFilter: FilterType = FilterType.AUTO_ENHANCE,
    val defaultPageSize: PageSize = PageSize.A4,
    val defaultPdfQuality: QualityLevel = QualityLevel.HIGH,
    val defaultMargin: MarginPreset = MarginPreset.NORMAL,
    val appLockEnabled: Boolean = false,
    val hasSeenOnboarding: Boolean = false,
    val encryptNewDocuments: Boolean = false,
    val cloudBackupEnabled: Boolean = true,
    val autoSyncEnabled: Boolean = true,
    val wifiOnlyUpload: Boolean = false,
    val defaultSaveAction: SaveAction = SaveAction.SAVE_AND_UPLOAD,
    val uploadQuality: QualityLevel = QualityLevel.HIGH
) {
    /**
     * Theme options for the application.
     */
    enum class ThemeMode {
        SYSTEM, LIGHT, DARK
    }
}
