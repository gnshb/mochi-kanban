package com.mochikanban.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val scheduler: WidgetRefreshScheduler,
) : WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val requestLock = Any()
    private var refreshWorkerActive = false
    private var refreshQueued = false

    override fun refresh() {
        synchronized(requestLock) {
            refreshQueued = true
            if (refreshWorkerActive) return
            refreshWorkerActive = true
        }
        scope.launch {
            while (true) {
                synchronized(requestLock) {
                    if (!refreshQueued) {
                        refreshWorkerActive = false
                        return@launch
                    }
                    refreshQueued = false
                }
                runCatching { refreshNow() }
            }
        }
    }

    override suspend fun refreshNow() {
        refreshMutex.withLock {
            runCatching { renderWidgets() }
            delay(FOLLOW_UP_RENDER_DELAY_MS)
            runCatching { renderWidgets() }
            scheduler.scheduleNext()
        }
    }

    private suspend fun renderWidgets() {
        val widget = KanbanGlanceWidget()
        widget.updateAll(ctx)

        val manager = GlanceAppWidgetManager(ctx)
        manager.getGlanceIds(KanbanGlanceWidget::class.java).forEach { id ->
            widget.update(ctx, id)
        }
    }

    private companion object {
        const val FOLLOW_UP_RENDER_DELAY_MS = 250L
    }
}
