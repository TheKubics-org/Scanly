package com.docscanner.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.docscanner.app.service.notification.NotificationService
import com.docscanner.app.service.sync.CloudSyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DocScannerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var notificationService: NotificationService

    @Inject
    lateinit var cloudSyncManager: CloudSyncManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() {
            val builder = Configuration.Builder()
            if (::workerFactory.isInitialized) {
                builder.setWorkerFactory(workerFactory)
            }
            return builder.build()
        }

    override fun onCreate() {
        super.onCreate()
        
        notificationService.createNotificationChannels()
        cloudSyncManager.schedulePeriodicSync()
    }
}
