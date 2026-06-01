package com.mochikanban.app.sync.engine

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object SyncWindow {
    fun start(now: Long = System.currentTimeMillis()): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(now - 7L * 86_400_000), ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun end(now: Long = System.currentTimeMillis()): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(now + 90L * 86_400_000), ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
