package com.docscanner.app.service.sync

import android.content.Context
import androidx.work.*
import com.docscanner.app.domain.repository.SettingsRepository
import com.docscanner.app.util.ScanlyLogger
import com.scanly.data.vault.StorageVaultRepository
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
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Schedules periodic background sync (every 1 hour).
     *
     * Cancellation logic (fixed): sync is cancelled ONLY if BOTH cloudBackupEnabled
     * AND autoSyncEnabled are explicitly disabled AND no active provider is configured.
     * Previously used || which caused sync to cancel whenever either flag was false.
     */
    fun schedulePeriodicSync() {
        scope.launch {
            val settings = settingsRepository.settings.first()
            val hasActiveProvider = storageVaultRepository.getActiveProvider() != null

            // Cancel only if both toggles are off AND no provider is active
            val shouldCancel = !settings.cloudBackupEnabled
                    && !settings.autoSyncEnabled
                    && !hasActiveProvider

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

    /**
     * Enqueues an immediate one-time sync.
     * Does NOT check cloudBackupEnabled — if a provider is configured and document
     * is queued, we always try to upload immediately.
     */
    fun triggerImmediateSync() {
        scope.launch {
            val settings = settingsRepository.settings.first()
            val hasActiveProvider = storageVaultRepository.getActiveProvider() != null

            if (!hasActiveProvider) {
                ScanlyLogger.syncWarn("Immediate sync skipped: no active storage provider configured")
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
