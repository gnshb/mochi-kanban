package com.mochikanban.app.widget

interface WidgetUpdater {
    /** Fire-and-forget refresh (safe from the foreground app). */
    fun refresh()

    /** Awaitable refresh — use from workers/suspend code so it completes before the process ends. */
    suspend fun refreshNow()
}
