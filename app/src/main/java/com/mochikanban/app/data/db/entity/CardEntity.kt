package com.mochikanban.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochikanban.app.domain.Card
import com.mochikanban.app.domain.Column
import com.mochikanban.app.domain.SyncState
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@Entity(
    tableName = "cards",
    indices = [Index("column"), Index("remoteEventId"), Index("calendarId"), Index("labelId")],
)
data class CardEntity(
    @PrimaryKey val id: String,
    val title: String,

    /** kotlinx.serialization-encoded [com.mochikanban.app.domain.Checklist]. */
    val checklist: String? = null,

    val startUtc: Long? = null,
    val durationMin: Int? = null,

    /** The event's time before a drag to Doing/Done shifted it, so a drag back to To do reverts. */
    val originalStartUtc: Long? = null,
    val originalDurationMin: Int? = null,

    val column: Column = Column.TODO,
    val position: Double = 0.0,

    /** References [LabelEntity.id]; nullable means "no label". */
    val labelId: String? = null,

    /** Absolute moment to fire the reminder, in epoch millis (UTC). */
    val reminderAtUtc: Long? = null,
    val workRequestId: String? = null,
    val readOnly: Boolean = false,

    val calendarId: String? = null,
    val remoteEventId: String? = null,
    val etag: String? = null,

    val updatedAtLocal: Long = System.currentTimeMillis(),
    val updatedAtRemote: Long? = null,

    val dirty: Boolean = false,
    val deletedLocal: Boolean = false,
    val syncState: SyncState = SyncState.IDLE,
) {
    fun endUtc(): Long? =
        startUtc?.let { it + (durationMin ?: 60).coerceAtLeast(1) * 60_000L }

    fun isAttentionWindow(now: Long): Boolean {
        val start = startUtc ?: return false
        val end = endUtc() ?: return false
        return column != Column.DONE && now >= start - ATTENTION_LEAD_MS && now < end
    }

    fun isActionRequired(now: Long): Boolean {
        val end = endUtc() ?: return false
        return column != Column.DONE && now >= end
    }

    fun isScheduledBefore(dayStartUtc: Long): Boolean {
        val end = endUtc() ?: return false
        return end < dayStartUtc
    }

    fun isLikelyLegacyAllDayImport(zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val start = startUtc ?: return false
        return remoteEventId != null &&
            durationMin == null &&
            Instant.ofEpochMilli(start).atZone(zone).toLocalTime() == LocalTime.MIDNIGHT
    }

    fun todoSortBucket(now: Long): Int = when {
        isActionRequired(now) -> 0
        isAttentionWindow(now) -> 1
        else -> 2
    }

    fun nextClockTransitionAfter(now: Long): Long? {
        if (column == Column.DONE) return null
        val start = startUtc ?: return null
        val end = endUtc() ?: return null
        return listOf(start - ATTENTION_LEAD_MS, start, end)
            .filter { it > now }
            .minOrNull()
    }

    /**
     * Dated cards still move into Doing while their event is active, but reaching
     * the scheduled end no longer completes the task. It returns to To do as an
     * action-required card until the user finishes or snoozes it.
     */
    fun effectiveColumn(now: Long): Column {
        if (column == Column.DONE) return Column.DONE
        val start = startUtc ?: return column
        val end = start + (durationMin ?: 60).coerceAtLeast(1) * 60_000L
        return when {
            now < start -> Column.TODO
            now < end -> Column.DOING
            else -> Column.TODO
        }
    }

    fun toDomain(): Card = Card(
        id = id,
        title = title,
        startUtc = startUtc,
        durationMin = durationMin,
        column = column,
        position = position,
        labelId = labelId,
        readOnly = readOnly,
        calendarId = calendarId,
        remoteEventId = remoteEventId,
    )

    companion object {
        const val ATTENTION_LEAD_MS = 60L * 60 * 1000
    }
}
