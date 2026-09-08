package com.docscanner.app.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscanner.app.domain.model.FilterType
import com.docscanner.app.domain.model.PageSize
import com.docscanner.app.domain.model.QualityLevel
import com.docscanner.app.domain.model.SaveAction
import com.docscanner.app.domain.model.UserSettings.ThemeMode
import com.docscanner.app.domain.model.UserSettings
import com.docscanner.app.domain.repository.SettingsRepository
import com.docscanner.app.service.sync.CloudSyncManager
import com.scanly.data.storage.StorageConfig
import com.scanly.data.vault.StorageVaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val storageVaultRepository: StorageVaultRepository,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    val activeConfig: StateFlow<StorageConfig?> = storageVaultRepository.observeActiveConfig()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(
            viewModelScope, 
            SharingStarted.WhileSubscribed(5000), 
            UserSettings()
        )

    fun updateTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.updateSettings(settings.value.copy(theme = mode)) }
    }

    fun updateDefaultFilter(filter: FilterType) {
        viewModelScope.launch { settingsRepository.updateSettings(settings.value.copy(defaultFilter = filter)) }
    }

    fun updateDefaultPageSize(size: PageSize) {
        viewModelScope.launch { settingsRepository.updateSettings(settings.value.copy(defaultPageSize = size)) }
    }

    fun updateDefaultQuality(quality: QualityLevel) {
        viewModelScope.launch { settingsRepository.updateSettings(settings.value.copy(defaultPdfQuality = quality)) }
    }

    fun toggleAppLock(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateSettings(settings.value.copy(appLockEnabled = enabled)) }
    }

    fun toggleEncryption(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateSettings(settings.value.copy(encryptNewDocuments = enabled)) }
    }

    fun toggleCloudBackup(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings.value.copy(cloudBackupEnabled = enabled))
            if (enabled) {
                cloudSyncManager.schedulePeriodicSync()
            }
        }
    }

    fun toggleAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings.value.copy(autoSyncEnabled = enabled))
            cloudSyncManager.schedulePeriodicSync()
        }
    }

    fun toggleWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings.value.copy(wifiOnlyUpload = enabled))
            cloudSyncManager.schedulePeriodicSync()
        }
    }

    fun updateDefaultSaveAction(action: SaveAction) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings.value.copy(defaultSaveAction = action))
        }
    }

    fun clearCache(context: Context) {
        context.cacheDir.deleteRecursively()
    }
}
