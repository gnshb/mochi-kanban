package com.mochikanban.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically re-renders the widget so time-driven columns stay current as the
 * clock advances (e.g. an event crossing from To do into Doing) without waiting
 * for the next data change or sync.
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { KanbanGlanceWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "widget-refresh"
    }
}
