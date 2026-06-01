package com.mochikanban.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.mochikanban.app.data.repo.CardRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class CompleteCardAction : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun cardRepository(): CardRepository
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val cardId = parameters[KEY_CARD_ID] ?: return
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, Entry::class.java)
            .cardRepository()
        // Complete immediately (ends the event now → Done, flushes to Google) and
        // re-render. No artificial delay — that only made the tap feel laggy.
        repo.completeCard(cardId)
        KanbanGlanceWidget().update(context, glanceId)
    }

    companion object {
        val KEY_CARD_ID = ActionParameters.Key<String>("cardId")
    }
}
