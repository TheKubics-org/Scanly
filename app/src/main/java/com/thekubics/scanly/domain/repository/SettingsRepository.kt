package com.thekubics.scanly.domain.repository

import com.thekubics.scanly.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining operations for managing application settings.
 */
interface SettingsRepository {

    /**
     * A flow emitting the current user settings.
     */
    val settings: Flow<UserSettings>

    /**
     * Updates the user settings.
     */
    suspend fun updateSettings(settings: UserSettings)

    /**
     * Retrieves a flow emitting only the current theme preference.
     */
    fun getTheme(): Flow<UserSettings.ThemeMode>

    /**
     * Retrieves a flow emitting whether the app lock feature is enabled.
     */
    fun isAppLockEnabled(): Flow<Boolean>
}
