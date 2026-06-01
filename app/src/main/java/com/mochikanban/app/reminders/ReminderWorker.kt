package com.mochikanban.app.reminders

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mochikanban.app.MainActivity
import com.mochikanban.app.R
import com.mochikanban.app.data.repo.CardRepository
import com.mochikanban.app.util.Time
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: CardRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cardId = inputData.getString(KEY_CARD_ID) ?: return Result.failure()
        val card = repo.getCard(cardId) ?: return Result.success()
        if (card.column == com.mochikanban.app.domain.Column.DONE || card.deletedLocal) {
            return Result.success()
        }

        val ctx = applicationContext
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("cardId", cardId)
        }
        val pi = PendingIntent.getActivity(
            ctx, cardId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val subtitle = card.startUtc?.let { "Starts at ${Time.formatTimeOnly(it)}" }
            ?: "Reminder"

        val completePi = actionIntent(ctx, ReminderActionReceiver.ACTION_COMPLETE, cardId)
        val snoozeIntent = Intent(ctx, SnoozeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(SnoozeActivity.EXTRA_CARD_ID, cardId)
        }
        val snoozePi = PendingIntent.getActivity(
            ctx, ("snooze$cardId").hashCode(), snoozeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(ctx, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(android.graphics.Color.parseColor("#86E7BF"))
            .setContentTitle(card.title)
            .setContentText(subtitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(0, "Complete", completePi)
            .addAction(0, "Snooze", snoozePi)
            .build()

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm?.notify(cardId.hashCode(), notif)
        // A firing reminder usually coincides with a To do → Doing transition.
        runCatching { com.mochikanban.app.widget.KanbanGlanceWidget().updateAll(ctx) }
        return Result.success()
    }

    private fun actionIntent(ctx: Context, action: String, cardId: String): PendingIntent {
        val intent = Intent(ctx, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderActionReceiver.EXTRA_CARD_ID, cardId)
        }
        return PendingIntent.getBroadcast(
            ctx, (action + cardId).hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val KEY_CARD_ID = "cardId"
    }
}
