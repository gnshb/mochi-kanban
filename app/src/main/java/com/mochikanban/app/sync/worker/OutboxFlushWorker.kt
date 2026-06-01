package com.mochikanban.app.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mochikanban.app.sync.auth.TokenStore
import com.mochikanban.app.sync.engine.OutboxProcessor
import com.mochikanban.app.widget.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class OutboxFlushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val outboxProcessor: OutboxProcessor,
    private val widget: WidgetUpdater,
    private val tokenStore: TokenStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!tokenStore.isConfigured()) return Result.success()
        return try {
            outboxProcessor.drain()
            widget.refreshNow()
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
