package com.docscanner.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.docscanner.app.domain.model.FilterType
import com.docscanner.app.domain.model.MarginPreset
import com.docscanner.app.domain.model.PageSize
import com.docscanner.app.domain.model.QualityLevel
import com.docscanner.app.domain.model.SaveAction
import com.docscanner.app.domain.model.UserSettings
import com.docscanner.app.domain.model.UserSettings.ThemeMode
import com.docscanner.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_FILTER = stringPreferencesKey("default_filter")
        val DEFAULT_PAGE_SIZE = stringPreferencesKey("default_page_size")
        val DEFAULT_PDF_QUALITY = stringPreferencesKey("default_pdf_quality")
        val DEFAULT_MARGIN = stringPreferencesKey("default_margin")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val ENCRYPT_NEW_DOCUMENTS = booleanPreferencesKey("encrypt_new_documents")
        val CLOUD_BACKUP_ENABLED = booleanPreferencesKey("cloud_backup_enabled")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val WIFI_ONLY_UPLOAD = booleanPreferencesKey("wifi_only_upload")
        val DEFAULT_SAVE_ACTION = stringPreferencesKey("default_save_action")
        val UPLOAD_QUALITY = stringPreferencesKey("upload_quality")
    }

    override val settings: Flow<UserSettings> = dataStore.data.map { preferences ->
        val themeMode = preferences[PreferencesKeys.THEME_MODE]?.let { raw ->
            runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
        } ?: ThemeMode.SYSTEM

        val defaultFilter = preferences[PreferencesKeys.DEFAULT_FILTER]?.let { raw ->
            runCatching { FilterType.valueOf(raw) }.getOrDefault(FilterType.AUTO_ENHANCE)
        } ?: FilterType.AUTO_ENHANCE

        val defaultPageSize = preferences[PreferencesKeys.DEFAULT_PAGE_SIZE]?.let { raw ->
            runCatching { PageSize.valueOf(raw) }.getOrDefault(PageSize.A4)
        } ?: PageSize.A4

        val defaultPdfQuality = preferences[PreferencesKeys.DEFAULT_PDF_QUALITY]?.let { raw ->
            runCatching { QualityLevel.valueOf(raw) }.getOrDefault(QualityLevel.HIGH)
        } ?: QualityLevel.HIGH

        val defaultMargin = preferences[PreferencesKeys.DEFAULT_MARGIN]?.let { raw ->
            runCatching { MarginPreset.valueOf(raw) }.getOrDefault(MarginPreset.NORMAL)
        } ?: MarginPreset.NORMAL

        val defaultSaveAction = preferences[PreferencesKeys.DEFAULT_SAVE_ACTION]?.let { raw ->
            runCatching { SaveAction.valueOf(raw) }.getOrDefault(SaveAction.SAVE_LOCAL)
        } ?: SaveAction.SAVE_LOCAL

        val uploadQuality = preferences[PreferencesKeys.UPLOAD_QUALITY]?.let { raw ->
            runCatching { QualityLevel.valueOf(raw) }.getOrDefault(QualityLevel.HIGH)
        } ?: QualityLevel.HIGH

        UserSettings(
            theme = themeMode,
            defaultFilter = defaultFilter,
            defaultPageSize = defaultPageSize,
            defaultPdfQuality = defaultPdfQuality,
            defaultMargin = defaultMargin,
            appLockEnabled = preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false,
            hasSeenOnboarding = preferences[PreferencesKeys.HAS_SEEN_ONBOARDING] ?: false,
            encryptNewDocuments = preferences[PreferencesKeys.ENCRYPT_NEW_DOCUMENTS] ?: false,
            cloudBackupEnabled = preferences[PreferencesKeys.CLOUD_BACKUP_ENABLED] ?: true,
            autoSyncEnabled = preferences[PreferencesKeys.AUTO_SYNC_ENABLED] ?: true,
            wifiOnlyUpload = preferences[PreferencesKeys.WIFI_ONLY_UPLOAD] ?: false,
            defaultSaveAction = defaultSaveAction,
            uploadQuality = uploadQuality
        )
    }

    override suspend fun updateSettings(settings: UserSettings) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = settings.theme.name
            preferences[PreferencesKeys.DEFAULT_FILTER] = settings.defaultFilter.name
            preferences[PreferencesKeys.DEFAULT_PAGE_SIZE] = settings.defaultPageSize.name
            preferences[PreferencesKeys.DEFAULT_PDF_QUALITY] = settings.defaultPdfQuality.name
            preferences[PreferencesKeys.DEFAULT_MARGIN] = settings.defaultMargin.name
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = settings.appLockEnabled
            preferences[PreferencesKeys.HAS_SEEN_ONBOARDING] = settings.hasSeenOnboarding
            preferences[PreferencesKeys.ENCRYPT_NEW_DOCUMENTS] = settings.encryptNewDocuments
            preferences[PreferencesKeys.CLOUD_BACKUP_ENABLED] = settings.cloudBackupEnabled
            preferences[PreferencesKeys.AUTO_SYNC_ENABLED] = settings.autoSyncEnabled
            preferences[PreferencesKeys.WIFI_ONLY_UPLOAD] = settings.wifiOnlyUpload
            preferences[PreferencesKeys.DEFAULT_SAVE_ACTION] = settings.defaultSaveAction.name
            preferences[PreferencesKeys.UPLOAD_QUALITY] = settings.uploadQuality.name
        }
    }

    override fun getTheme(): Flow<ThemeMode> {
        return dataStore.data.map { preferences ->
            preferences[PreferencesKeys.THEME_MODE]?.let { raw ->
                runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
            } ?: ThemeMode.SYSTEM
        }
    }

    override fun isAppLockEnabled(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false
        }
    }
}
