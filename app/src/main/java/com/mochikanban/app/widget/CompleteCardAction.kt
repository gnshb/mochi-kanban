package com.mochikanban.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.mochikanban.app.data.WidgetPrefs
import com.mochikanban.app.data.repo.CardRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

class CompleteCardAction : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun cardRepository(): CardRepository
        fun widgetPrefs(): WidgetPrefs
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val cardId = parameters[KEY_CARD_ID] ?: return
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, Entry::class.java)
        val repo = entry.cardRepository()
        val prefs = entry.widgetPrefs()
        val widget = KanbanGlanceWidget()

        // 1. Flash the check on this card.
        prefs.setCompleting(cardId)
        widget.update(context, glanceId)
        delay(450)

        // 2. Complete it (ends the event now → moves to Done, flushes to Google).
        prefs.setCompleting(null)
        repo.completeCard(cardId)

        // 3. Re-render so the finished card drops off the To do list.
        widget.update(context, glanceId)
    }

    companion object {
        val KEY_CARD_ID = ActionParameters.Key<String>("cardId")
    }
}
