package com.mochikanban.app.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mochikanban.app.data.repo.CardRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the Complete / Snooze actions on a reminder notification. */
class ReminderActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun cardRepository(): CardRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val cardId = intent.getStringExtra(EXTRA_CARD_ID) ?: return
        val action = intent.action ?: return
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, Deps::class.java)
            .cardRepository()

        // Dismiss the notification immediately.
        context.getSystemService(NotificationManager::class.java)?.cancel(cardId.hashCode())

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_COMPLETE -> repo.completeCard(cardId)
                    ACTION_SNOOZE -> repo.snooze(cardId, SNOOZE_MINUTES)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.mochikanban.app.action.COMPLETE"
        const val ACTION_SNOOZE = "com.mochikanban.app.action.SNOOZE"
        const val EXTRA_CARD_ID = "cardId"
        const val SNOOZE_MINUTES = 15
    }
}
