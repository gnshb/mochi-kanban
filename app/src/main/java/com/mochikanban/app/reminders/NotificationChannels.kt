package com.mochikanban.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.mochikanban.app.R

object NotificationChannels {
    const val REMINDERS = "reminders"

    fun create(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return

        val attrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        nm.createNotificationChannel(
            NotificationChannel(
                REMINDERS,
                ctx.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = ctx.getString(R.string.reminder_channel_description)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 200, 200)
                setShowBadge(true)
                setBypassDnd(false)
                lockscreenVisibility = NotificationCompatLockscreenVisibility.PUBLIC
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    attrs,
                )
            }
        )
    }

    // Inline alias to avoid importing the Compat constant in core code.
    private object NotificationCompatLockscreenVisibility {
        const val PUBLIC = androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
    }
}
