package com.mochikanban.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val cardId = intent.getStringExtra(EXTRA_CARD_ID) ?: return
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(Data.Builder().putString(ReminderWorker.KEY_CARD_ID, cardId).build())
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }

    companion object {
        const val ACTION = "com.mochikanban.app.action.REMINDER"
        const val EXTRA_CARD_ID = "cardId"
    }
}
