package com.mochikanban.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Time {
    fun now(): Long = System.currentTimeMillis()

    fun startOfToday(zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfToday(zone: ZoneId = ZoneId.systemDefault()): Long =
        startOfToday(zone) + 24L * 60 * 60 * 1000

    fun format(ms: Long, pattern: String = "EEE d MMM · HH:mm"): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(pattern))

    fun formatTimeOnly(ms: Long): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
}
