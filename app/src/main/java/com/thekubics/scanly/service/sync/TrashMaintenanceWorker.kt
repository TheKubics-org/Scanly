package com.thekubics.scanly.service.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.thekubics.scanly.domain.repository.DocumentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Permanently removes documents that have sat in Trash past the retention window.
 */
@HiltWorker
class TrashMaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val documentRepository: DocumentRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            documentRepository.purgeOldTrash()
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }
}
