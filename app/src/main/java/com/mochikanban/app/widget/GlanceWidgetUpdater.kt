package com.mochikanban.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.mochikanban.app.data.WidgetPrefs
import com.mochikanban.app.data.db.dao.CardDao
import com.mochikanban.app.data.db.dao.LabelDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val scheduler: WidgetRefreshScheduler,
    private val cardDao: CardDao,
    private val labelDao: LabelDao,
    private val widgetPrefs: WidgetPrefs,
) : WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val requestLock = Any()
    private var refreshWorkerActive = false
    private var refreshQueued = false

    // Signature of the last content we actually rendered, so repeat calls that would
    // draw the same pixels (e.g. a Calendar sync that pulled nothing new) are skipped.
    @Volatile private var lastSignature: Int? = null

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

    override suspend fun refreshNow(force: Boolean) {
        refreshMutex.withLock {
            val signature = runCatching { contentSignature() }.getOrNull()
            // Skip the redraw when nothing the widget shows has changed. The clock
            // scheduler still runs so time-driven transitions stay armed.
            if (!force && signature != null && signature == lastSignature) {
                scheduler.scheduleNext()
                return@withLock
            }
            runCatching { renderWidgets() }
            lastSignature = signature
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

    /**
     * A hash over everything the widget renders: card fields that drive the list, the
     * label colours they reference, and the background opacity. Time-derived state
     * (glows, column transitions) is intentionally excluded — those redraws come from
     * the clock scheduler with [force] = true, not from data changes.
     */
    private suspend fun contentSignature(): Int {
        val cards = cardDao.allSnapshot()
            .sortedBy { it.id }
            .joinToString("|") { c ->
                listOf(
                    c.id, c.title, c.startUtc, c.durationMin, c.column,
                    c.labelId, c.originalStartUtc, c.position, c.deletedLocal,
                ).joinToString(",")
            }
        val labels = labelDao.all()
            .sortedBy { it.id }
            .joinToString("|") { "${it.id}:${it.colorHex}" }
        val opacity = runCatching { widgetPrefs.opacitySnapshot() }.getOrDefault(0.9f)
        return listOf(opacity, cards, labels).hashCode()
    }
}
