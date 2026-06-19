package com.mochikanban.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.mochikanban.app.sync.SyncTrigger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Tapping the mochi manually refreshes: pull from Google Calendar and redraw now. */
class RefreshWidgetAction : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun syncTrigger(): SyncTrigger
        fun widgetUpdater(): WidgetUpdater
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val entry = EntryPointAccessors
            .fromApplication(context.applicationContext, Entry::class.java)
        // Kick off a Calendar pull (lands via WidgetSyncObserver when it writes rows),
        // and redraw immediately so the tap feels responsive.
        entry.syncTrigger().requestFullSync()
        entry.widgetUpdater().refreshNow(force = true)
    }
}
