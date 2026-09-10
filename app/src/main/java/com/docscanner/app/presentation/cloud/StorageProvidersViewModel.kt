package com.docscanner.app.presentation.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscanner.app.domain.model.SaveAction
import com.docscanner.app.domain.repository.SettingsRepository
import com.docscanner.app.service.sync.CloudSyncManager
import com.scanly.data.storage.GoogleDriveAuthType
import com.scanly.data.storage.StorageConfig
import com.scanly.data.storage.StorageProviderRegistry
import com.scanly.data.storage.StorageProviderType
import com.scanly.data.storage.StorageTestResult
import com.scanly.data.vault.StorageVaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ProviderTestState(
    val isTesting: Boolean = false,
    val result: StorageTestResult? = null
)

@HiltViewModel
class StorageProvidersViewModel @Inject constructor(
    private val vaultRepository: StorageVaultRepository,
    private val registry: StorageProviderRegistry,
    private val settingsRepository: SettingsRepository,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    val configs: StateFlow<List<StorageConfig>> = vaultRepository.observeConfigs()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val activeConfig: StateFlow<StorageConfig?> = vaultRepository.observeActiveConfig()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    private val _testState = MutableStateFlow<Map<String, ProviderTestState>>(emptyMap())
    val testState: StateFlow<Map<String, ProviderTestState>> = _testState.asStateFlow()

    private val _dialogTestState = MutableStateFlow(ProviderTestState())
    val dialogTestState: StateFlow<ProviderTestState> = _dialogTestState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearDialogTestState() {
        _dialogTestState.value = ProviderTestState()
    }

    fun testExistingProvider(config: StorageConfig) {
        viewModelScope.launch {
            _testState.value = _testState.value + (config.id to ProviderTestState(isTesting = true))
            val result = withContext(Dispatchers.IO) {
                try {
                    val provider = registry.createProvider(config)
                    provider.testConnection()
                } catch (e: Exception) {
                    StorageTestResult(isSuccess = false, message = "Error: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
            _testState.value = _testState.value + (config.id to ProviderTestState(isTesting = false, result = result))
        }
    }

    fun testDraftProvider(config: StorageConfig) {
        viewModelScope.launch {
            _dialogTestState.value = ProviderTestState(isTesting = true)
            val result = withContext(Dispatchers.IO) {
                try {
                    val provider = registry.createProvider(config)
                    provider.testConnection()
                } catch (e: Exception) {
                    StorageTestResult(isSuccess = false, message = "Error: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
            _dialogTestState.value = ProviderTestState(isTesting = false, result = result)
        }
    }

    fun saveConfig(config: StorageConfig, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val validation = config.validate()
            if (!validation.isValid) {
                _userMessage.value = validation.errors.firstOrNull() ?: "Invalid configuration"
                return@launch
            }

            try {
                vaultRepository.saveConfig(config)
                vaultRepository.setActiveProviderId(config.id)

                // Auto-enable cloud backup & trigger immediate sync of unsynced documents
                try {
                    val currentSettings = settingsRepository.settings.first()
                    settingsRepository.updateSettings(
                        currentSettings.copy(
                            cloudBackupEnabled = true,
                            autoSyncEnabled = true,
                            wifiOnlyUpload = false,
                            defaultSaveAction = SaveAction.SAVE_AND_UPLOAD
                        )
                    )
                    cloudSyncManager.triggerImmediateSync()
                } catch (_: Exception) {}

                _userMessage.value = "${config.displayName} saved & set as active cloud destination"
                onSaved()
            } catch (e: Exception) {
                _userMessage.value = "Failed to save: ${e.localizedMessage}"
            }
        }
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch {
            try {
                vaultRepository.deleteConfig(id)
                _testState.value = _testState.value - id
                _userMessage.value = "Destination deleted"
            } catch (e: Exception) {
                _userMessage.value = "Failed to delete: ${e.localizedMessage}"
            }
        }
    }

    fun setActiveProvider(id: String) {
        viewModelScope.launch {
            try {
                vaultRepository.setActiveProviderId(id)

                // Auto-enable cloud backup & trigger immediate sync
                try {
                    val currentSettings = settingsRepository.settings.first()
                    settingsRepository.updateSettings(
                        currentSettings.copy(
                            cloudBackupEnabled = true,
                            autoSyncEnabled = true,
                            wifiOnlyUpload = false,
                            defaultSaveAction = SaveAction.SAVE_AND_UPLOAD
                        )
                    )
                    cloudSyncManager.triggerImmediateSync()
                } catch (_: Exception) {}

                _userMessage.value = "Active destination updated"
            } catch (e: Exception) {
                _userMessage.value = "Failed to update active destination: ${e.localizedMessage}"
            }
        }
    }

    fun toggleProviderEnabled(config: StorageConfig) {
        viewModelScope.launch {
            val updated = when (config) {
                is StorageConfig.Telegram -> config.copy(isEnabled = !config.isEnabled)
                is StorageConfig.CloudflareR2 -> config.copy(isEnabled = !config.isEnabled)
                is StorageConfig.GoogleDrive -> config.copy(isEnabled = !config.isEnabled)
            }
            vaultRepository.saveConfig(updated)
            if (updated.isEnabled) {
                cloudSyncManager.triggerImmediateSync()
            }
        }
    }
}
