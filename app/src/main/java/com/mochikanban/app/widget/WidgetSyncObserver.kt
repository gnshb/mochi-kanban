package com.mochikanban.app.widget

import com.mochikanban.app.data.WidgetPrefs
import com.mochikanban.app.data.db.dao.CardDao
import com.mochikanban.app.data.db.dao.LabelDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the home-screen widget in step with the app the proper way: by observing the
 * data it renders rather than relying on every mutation site to remember to refresh.
 *
 * Room emits on any change to the cards/labels tables — add, edit, delete, move,
 * complete, snooze, or a background sync writing rows — so a single collector here
 * re-renders the widget immediately for all of them. Background workers (sync, midnight
 * cleanup) still refresh explicitly, since their process can be torn down before this
 * debounced collector runs.
 */
@Singleton
class WidgetSyncObserver @Inject constructor(
    private val cardDao: CardDao,
    private val labelDao: LabelDao,
    private val widgetPrefs: WidgetPrefs,
    private val updater: WidgetUpdater,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        combine(
            cardDao.observeAll(),
            labelDao.observe(),
            widgetPrefs.opacity,
        ) { _, _, _ -> Unit }
            // The first emission is just the current state at process start; the widget
            // already renders that on bind, so only react to subsequent changes.
            .drop(1)
            // Coalesce bursts (e.g. a sync writing many rows) into a single render.
            .debounce(DEBOUNCE_MS)
            .onEach { updater.refreshNow() }
            .launchIn(scope)
    }

    private companion object {
        const val DEBOUNCE_MS = 120L
    }
}
