package com.thekubics.scanly.service.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.thekubics.scanly.domain.service.cloud.CloudStorageService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val cloudStorageService: CloudStorageService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val result = cloudStorageService.syncPendingDocuments()
            if (result.isSuccess) {
                Result.success()
            } else {
                // Cap retries so a permanently failing sync (e.g. bad credentials,
                // no provider configured, blocked file type) doesn't loop forever.
                if (runAttemptCount >= MAX_WORKER_RETRIES) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount >= MAX_WORKER_RETRIES) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        private const val MAX_WORKER_RETRIES = 5
    }
}
