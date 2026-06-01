package com.mochikanban.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mochikanban.app.sync.SyncStatus
import com.mochikanban.app.sync.worker.CalendarSyncWorker
import com.mochikanban.app.sync.worker.OutboxFlushWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncTrigger @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val status: SyncStatus,
) : SyncTrigger {

    private val wm get() = WorkManager.getInstance(ctx)

    override fun requestFlush() {
        val req = OneTimeWorkRequestBuilder<OutboxFlushWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        wm.enqueueUniqueWork(WORK_FLUSH, ExistingWorkPolicy.REPLACE, req)
    }

    override fun requestFlushDelayed() {
        // Same unique name + REPLACE => each drag resets the timer (debounce). The
        // outbox reads the card's latest state at flush time, so a revert within the
        // window nets out to no remote change.
        val req = OneTimeWorkRequestBuilder<OutboxFlushWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(FLUSH_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniqueWork(WORK_FLUSH, ExistingWorkPolicy.REPLACE, req)
    }

    override fun requestFullSync() {
        status.startSync()
        val req = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniqueWork(WORK_SYNC_NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun ensurePeriodicSyncScheduled() {
        val req = PeriodicWorkRequestBuilder<CalendarSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    companion object {
        const val FLUSH_DELAY_MINUTES = 3L
        const val WORK_FLUSH = "outbox_flush"
        const val WORK_SYNC_NOW = "calendar_sync_now"
        const val WORK_SYNC_PERIODIC = "calendar_sync_periodic"
    }
}
