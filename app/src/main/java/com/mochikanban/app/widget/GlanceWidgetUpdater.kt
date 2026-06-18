package com.mochikanban.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val scheduler: WidgetRefreshScheduler,
) : WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun refresh() {
        scope.launch { refreshNow() }
    }

    override suspend fun refreshNow() {
        runCatching {
            KanbanGlanceWidget().updateAll(ctx)
        }
        scheduler.scheduleNext()
    }
}
