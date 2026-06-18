package com.mochikanban.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mochikanban.app.data.repo.LabelRepository
import com.mochikanban.app.reminders.MidnightCleanupWorker
import com.mochikanban.app.reminders.NotificationChannels
import com.mochikanban.app.sync.WorkManagerSyncTrigger
import com.mochikanban.app.widget.WidgetRefreshWorker
import com.mochikanban.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class KanbanApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncTrigger: WorkManagerSyncTrigger
    @Inject lateinit var labelRepo: LabelRepository
    @Inject lateinit var widgetRefreshScheduler: WidgetRefreshScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        syncTrigger.ensurePeriodicSyncScheduled()
        scheduleWidgetRefresh()
        scheduleMidnightCleanup()
        appScope.launch { labelRepo.ensureDefaults() }
        appScope.launch { widgetRefreshScheduler.scheduleNext() }
    }

    /** Coarse fallback; exact one-shot widget refreshes are scheduled at task boundaries. */
    private fun scheduleWidgetRefresh() {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WidgetRefreshWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Clear the Done column daily, first run aligned to the next midnight. */
    private fun scheduleMidnightCleanup() {
        val now = ZonedDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(now.zone)
        val initialDelay = Duration.between(now, nextMidnight).toMillis()
        val request = PeriodicWorkRequestBuilder<MidnightCleanupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MidnightCleanupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
