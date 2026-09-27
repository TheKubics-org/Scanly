package com.thekubics.scanly.service.sync

import android.content.Context
import androidx.work.*
import com.thekubics.scanly.domain.repository.SettingsRepository
import com.thekubics.scanly.util.ScanlyLogger
import com.thekubics.scanly.data.vault.StorageVaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val storageVaultRepository: StorageVaultRepository
) {
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Schedules periodic sync only after the user has enabled backup and auto-sync. */
    fun schedulePeriodicSync() {
        scope.launch {
            val settings = settingsRepository.settings.first()
            val hasActiveProvider = storageVaultRepository.getActiveProvider() != null

            val shouldCancel = !settings.cloudBackupEnabled || !settings.autoSyncEnabled || !hasActiveProvider

            if (shouldCancel) {
                ScanlyLogger.syncInfo("Periodic sync cancelled: backup disabled and no active provider")
                workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
                return@launch
            }

            val networkType = if (settings.wifiOnlyUpload) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            ScanlyLogger.syncInfo("Periodic sync scheduled — networkType=${networkType.name} wifiOnly=${settings.wifiOnlyUpload} hasProvider=$hasActiveProvider")
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        }
    }

    /** Enqueues an automatic one-time sync only when automatic backup is enabled. */
    fun triggerImmediateSync() {
        scope.launch {
            val settings = settingsRepository.settings.first()
            val hasActiveProvider = storageVaultRepository.getActiveProvider() != null

            if (!settings.cloudBackupEnabled || !settings.autoSyncEnabled || !hasActiveProvider) {
                ScanlyLogger.syncInfo("Immediate sync skipped: automatic backup is disabled or no destination is active")
                return@launch
            }

            val networkType = if (settings.wifiOnlyUpload) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(constraints)
                .build()

            ScanlyLogger.syncInfo("Immediate sync enqueued — networkType=${networkType.name}")
            workManager.enqueueUniqueWork(
                ONE_TIME_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }

    companion object {
        private const val PERIODIC_SYNC_WORK_NAME = "scanly_periodic_cloud_sync"
        private const val ONE_TIME_SYNC_WORK_NAME = "scanly_immediate_cloud_sync"
    }
}
