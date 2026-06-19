package com.mochikanban.app.widget

interface WidgetUpdater {
    /** Fire-and-forget refresh (safe from the foreground app). */
    fun refresh()

    /**
     * Awaitable refresh — use from workers/suspend code so it completes before the
     * process ends. Re-renders only when the widget's visible content actually changed
     * (so no-op calls — e.g. a Calendar sync that pulled identical data — don't redraw).
     * Pass [force] to redraw regardless (clock transitions, manual refresh, opacity).
     */
    suspend fun refreshNow(force: Boolean = false)
}
