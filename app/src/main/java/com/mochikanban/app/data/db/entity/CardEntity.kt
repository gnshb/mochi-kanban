package com.mochikanban.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochikanban.app.domain.Card
import com.mochikanban.app.domain.Column
import com.mochikanban.app.domain.SyncState

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
    /**
     * For dated calendar cards the board column is driven by the clock rather than
     * stored placement: upcoming → TODO, in-progress → DOING, finished → DONE.
     * Undated cards (e.g. quick-add without a date) keep their manual [column].
     */
    fun effectiveColumn(now: Long): Column {
        val start = startUtc ?: return column
        val end = start + (durationMin ?: 60).coerceAtLeast(1) * 60_000L
        return when {
            now < start -> Column.TODO
            now < end -> Column.DOING
            else -> Column.DONE
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
}
