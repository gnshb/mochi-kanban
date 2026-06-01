package com.mochikanban.app.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mochikanban.app.sync.auth.TokenStore
import com.mochikanban.app.sync.engine.SyncEngine
import com.mochikanban.app.widget.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
    private val widget: WidgetUpdater,
    private val tokenStore: TokenStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!tokenStore.isConfigured()) return Result.success()
        val outcome = engine.runOnce()
        widget.refresh()
        return if (outcome.isSuccess) Result.success() else Result.retry()
    }
}
