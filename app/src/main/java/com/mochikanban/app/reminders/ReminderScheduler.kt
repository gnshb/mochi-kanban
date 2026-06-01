package com.mochikanban.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.domain.Column
import com.mochikanban.app.util.Time
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules a single notification per card at [CardEntity.reminderAtUtc].
 *
 * Preferred path: [AlarmManager.setExactAndAllowWhileIdle] (Doze-friendly, fires
 * within a second of the wall clock). Falls back to WorkManager if the user has
 * not granted SCHEDULE_EXACT_ALARM.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val workManager = WorkManager.getInstance(ctx)
    private val alarmManager: AlarmManager? = ctx.getSystemService(AlarmManager::class.java)

    fun reschedule(card: CardEntity) {
        cancel(card.id)
        if (card.deletedLocal) return
        if (card.column == Column.DONE) return
        val fireAt = card.reminderAtUtc ?: return
        if (fireAt - Time.now() <= 0) return

        if (canScheduleExact()) {
            scheduleExactAlarm(card.id, fireAt)
        } else {
            scheduleWorkRequest(card.id, fireAt - Time.now())
        }
    }

    fun cancel(cardId: String) {
        workManager.cancelAllWorkByTag(tagFor(cardId))
        alarmManager?.cancel(alarmPendingIntent(cardId))
    }

    private fun canScheduleExact(): Boolean {
        val am = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms()
        else true
    }

    private fun scheduleWorkRequest(cardId: String, delayMs: Long) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(tagFor(cardId))
            .setInputData(Data.Builder().putString(ReminderWorker.KEY_CARD_ID, cardId).build())
            .build()
        workManager.enqueue(req)
    }

    private fun scheduleExactAlarm(cardId: String, fireAt: Long) {
        val am = alarmManager ?: return
        try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                fireAt,
                alarmPendingIntent(cardId),
            )
        } catch (_: SecurityException) {
            scheduleWorkRequest(cardId, fireAt - Time.now())
        }
    }

    private fun alarmPendingIntent(cardId: String): PendingIntent {
        val intent = Intent(ctx, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION
            putExtra(ReminderAlarmReceiver.EXTRA_CARD_ID, cardId)
        }
        return PendingIntent.getBroadcast(
            ctx, cardId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun tagFor(cardId: String) = "reminder_$cardId"
}
