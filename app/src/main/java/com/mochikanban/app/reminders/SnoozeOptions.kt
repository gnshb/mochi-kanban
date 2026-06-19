package com.mochikanban.app.reminders

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The snooze choices shared by the reminder-notification dialog ([SnoozeActivity])
 * and the in-app overdue dialog. Each option resolves to an absolute target time so
 * the reminder lands exactly where intended — 30 min is left as-is, while the longer
 * snoozes (and "tomorrow") snap to the nearest whole hour for tidy reminder times.
 */
object SnoozeOptions {

    /** [targetUtc] computes the absolute snooze time, evaluated fresh at tap time. */
    data class Option(val label: String, val targetUtc: () -> Long)

    fun all(zone: ZoneId = ZoneId.systemDefault()): List<Option> = listOf(
        Option("30 minutes") { ZonedDateTime.now(zone).plusMinutes(30).toEpochMs() },
        Option("1 hour") { ZonedDateTime.now(zone).plusHours(1).snapToHour().toEpochMs() },
        Option("3 hours") { ZonedDateTime.now(zone).plusHours(3).snapToHour().toEpochMs() },
        Option("Tomorrow (same time)") { ZonedDateTime.now(zone).plusDays(1).snapToHour().toEpochMs() },
    )

    /** Round to the nearest whole hour — :30 and later round up. */
    private fun ZonedDateTime.snapToHour(): ZonedDateTime {
        val floor = truncatedTo(ChronoUnit.HOURS)
        return if (minute >= 30) floor.plusHours(1) else floor
    }

    private fun ZonedDateTime.toEpochMs(): Long = toInstant().toEpochMilli()
}
