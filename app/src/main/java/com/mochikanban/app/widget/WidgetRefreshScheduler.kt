package com.mochikanban.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mochikanban.app.data.db.dao.CardDao
import com.mochikanban.app.util.Time
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetRefreshScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val cardDao: CardDao,
) {
    private val alarmManager: AlarmManager? = ctx.getSystemService(AlarmManager::class.java)

    suspend fun scheduleNext() {
        val now = Time.now()
        val nextAt = cardDao.allSnapshot()
            .asSequence()
            .mapNotNull { it.nextClockTransitionAfter(now) }
            .minOrNull()

        val pi = pendingIntent()
        if (nextAt == null) {
            alarmManager?.cancel(pi)
            return
        }
        if (!canScheduleExact()) return

        val fireAt = nextAt.coerceAtLeast(now + MIN_DELAY_MS)
        try {
            alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        } catch (_: SecurityException) {
            // The periodic WorkManager refresh remains as a coarse fallback.
        }
    }

    private fun canScheduleExact(): Boolean {
        val am = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms()
        else true
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(ctx, WidgetRefreshReceiver::class.java).apply { action = ACTION_REFRESH }
        return PendingIntent.getBroadcast(
            ctx,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val ACTION_REFRESH = "com.mochikanban.app.action.WIDGET_CLOCK_REFRESH"
        private const val REQUEST_CODE = 4213
        private const val MIN_DELAY_MS = 1_000L
    }
}
