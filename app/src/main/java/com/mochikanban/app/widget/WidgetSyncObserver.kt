package com.mochikanban.app.widget

import com.mochikanban.app.data.db.dao.CardDao
import com.mochikanban.app.data.db.dao.LabelDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the home-screen widget in step with the app the proper way: by observing the
 * data it renders rather than relying on every mutation site to remember to refresh.
 *
 * Room emits on any change to the cards/labels tables — add, edit, delete, move,
 * complete, snooze — so adding a card updates the widget at once, without waiting for a
 * Calendar sync. [WidgetUpdater.refreshNow] skips the redraw when the visible content is
 * unchanged, so a foreground sync that pulled nothing new won't make the widget flicker.
 */
@Singleton
class WidgetSyncObserver @Inject constructor(
    private val cardDao: CardDao,
    private val labelDao: LabelDao,
    private val updater: WidgetUpdater,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        combine(cardDao.observeAll(), labelDao.observe()) { _, _ -> Unit }
            // Coalesce the burst of writes a single edit makes (card + outbox rows).
            .debounce(DEBOUNCE_MS)
            .onEach { updater.refreshNow() }
            .launchIn(scope)
    }

    private companion object {
        const val DEBOUNCE_MS = 100L
    }
}
