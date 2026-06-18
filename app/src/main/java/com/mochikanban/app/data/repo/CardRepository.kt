package com.mochikanban.app.data.repo

import com.mochikanban.app.data.db.dao.CardDao
import com.mochikanban.app.data.db.dao.OutboxDao
import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.data.db.entity.OutboxEntity
import com.mochikanban.app.domain.Column
import com.mochikanban.app.domain.OpType
import com.mochikanban.app.domain.SyncState
import com.mochikanban.app.reminders.ReminderScheduler
import com.mochikanban.app.sync.SyncTrigger
import com.mochikanban.app.util.Time
import com.mochikanban.app.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class BoardColumnSnapshot(
    val now: Long,
    val columns: Map<Column, List<CardEntity>>,
)

@Singleton
class CardRepository @Inject constructor(
    private val cardDao: CardDao,
    private val outboxDao: OutboxDao,
    private val reminders: ReminderScheduler,
    private val widget: WidgetUpdater,
    private val sync: SyncTrigger,
) {

    fun observeCards(): Flow<List<CardEntity>> = cardDao.observeAll()

    suspend fun getCard(id: String): CardEntity? = cardDao.byId(id)

    /**
     * Buckets cards into columns by [CardEntity.effectiveColumn] (clock-driven for
     * dated cards) and orders each column chronologically: by start time, undated
     * cards last by manual position. A lightweight clock snapshot keeps time-driven
     * card states current even when no database row changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeByColumn(): Flow<BoardColumnSnapshot> =
        cardDao.observeAll().flatMapLatest { all ->
            flow {
                while (currentCoroutineContext().isActive) {
                    val now = Time.now()
                    emit(BoardColumnSnapshot(now = now, columns = bucketByColumn(all, now)))
                    delay(nextClockRefreshDelay(all, now))
                }
            }
        }

    suspend fun upsert(card: CardEntity, fromSync: Boolean = false, deferFlush: Boolean = false) {
        val toSave = if (fromSync) card else card.copy(
            updatedAtLocal = Time.now(),
            dirty = true,
            syncState = SyncState.PENDING_PUSH,
        )
        cardDao.upsert(toSave)
        if (!fromSync) {
            // One pending push per card — collapse repeated edits so we never queue
            // two CREATEs and end up with duplicate calendar events.
            outboxDao.deleteByCard(toSave.id)
            outboxDao.enqueue(
                OutboxEntity(
                    cardId = toSave.id,
                    opType = if (card.remoteEventId == null) OpType.CREATE else OpType.UPDATE,
                    payloadJson = "",
                )
            )
            if (deferFlush) sync.requestFlushDelayed() else sync.requestFlush()
        }
        reminders.reschedule(toSave)
        widget.refreshNow()
    }

    suspend fun create(card: CardEntity): CardEntity {
        val withId = if (card.id.isBlank()) card.copy(id = UUID.randomUUID().toString()) else card
        val positioned = if (withId.position == 0.0) {
            val tail = cardDao.byColumn(withId.column).lastOrNull()?.position ?: 0.0
            withId.copy(position = tail + 1.0)
        } else withId
        upsert(positioned)
        return positioned
    }

    suspend fun delete(id: String) {
        val card = cardDao.byId(id) ?: return
        cardDao.markDeleted(id)
        if (card.remoteEventId != null) {
            outboxDao.enqueue(
                OutboxEntity(cardId = id, opType = OpType.DELETE, payloadJson = "")
            )
            sync.requestFlush()
        } else {
            cardDao.hardDelete(id)
            outboxDao.deleteByCard(id)
        }
        reminders.cancel(id)
        widget.refreshNow()
    }

    suspend fun setLabel(cardId: String, labelId: String?) {
        val current = cardDao.byId(cardId) ?: return
        if (current.labelId == labelId) return
        upsert(current.copy(labelId = labelId))
    }

    suspend fun moveCard(cardId: String, toColumn: Column, toIndex: Int) {
        val current = cardDao.byId(cardId) ?: return
        if (current.startUtc == null) {
            // Undated cards are placed manually.
            val others = cardDao.byColumn(toColumn).filter { it.id != cardId }
            val newPos = fractionalPosition(others, toIndex)
            upsert(current.copy(column = toColumn, position = newPos))
            return
        }
        // Dated cards: shift the event's time so the clock places it in the target
        // column. The original time is remembered (so a drag back to To do reverts),
        // and the push to Google is deferred a few minutes so quick drags can undo.
        val now = Time.now()
        val durMs = (current.durationMin ?: 60).coerceAtLeast(1) * 60_000L
        val withOriginal =
            if (current.originalStartUtc == null)
                current.copy(originalStartUtc = current.startUtc, originalDurationMin = current.durationMin)
            else current
        val updated = when (toColumn) {
            Column.DOING -> withOriginal.copy(startUtc = now, column = Column.DOING)
            Column.DONE -> withOriginal.copy(startUtc = now - durMs, column = Column.DONE)
            Column.TODO -> when {
                current.originalStartUtc != null -> current.copy(
                    startUtc = current.originalStartUtc,
                    durationMin = current.originalDurationMin,
                    originalStartUtc = null,
                    originalDurationMin = null,
                    column = Column.TODO,
                )
                current.startUtc <= now -> current.copy(startUtc = now + 60 * 60_000L, column = Column.TODO)
                else -> current.copy(column = Column.TODO)
            }
        }
        if (updated != current) upsert(updated, deferFlush = true)
    }

    /** Marks a card done; for dated cards this ends the event now (so it stays in Done). */
    suspend fun completeCard(cardId: String) {
        val c = cardDao.byId(cardId) ?: return
        val updated = if (c.startUtc != null) {
            val durMs = (c.durationMin ?: 60).coerceAtLeast(1) * 60_000L
            val withOriginal =
                if (c.originalStartUtc == null) c.copy(originalStartUtc = c.startUtc, originalDurationMin = c.durationMin)
                else c
            withOriginal.copy(column = Column.DONE, startUtc = Time.now() - durMs)
        } else {
            c.copy(column = Column.DONE)
        }
        upsert(updated)
    }

    /** Removes all currently-Done cards locally (used by the midnight cleanup). */
    suspend fun clearDone() {
        val now = Time.now()
        cardDao.allSnapshot()
            .filter { it.effectiveColumn(now) == Column.DONE }
            .forEach { cardDao.hardDelete(it.id) }
        widget.refreshNow()
    }

    /**
     * Snooze: move the event's start (and reminder) [minutes] into the future so it
     * returns to To do. Undated cards just get their reminder pushed out.
     */
    suspend fun snooze(cardId: String, minutes: Int) {
        val c = cardDao.byId(cardId) ?: return
        val target = Time.now() + minutes * 60_000L
        val updated = if (c.startUtc != null) {
            c.copy(
                startUtc = target,
                reminderAtUtc = target,
                column = Column.TODO,
                originalStartUtc = null,
                originalDurationMin = null,
            )
        } else {
            c.copy(reminderAtUtc = target)
        }
        upsert(updated)
    }

    private fun fractionalPosition(siblings: List<CardEntity>, index: Int): Double {
        val sorted = siblings.sortedBy { it.position }
        val prev = sorted.getOrNull(index - 1)?.position
        val next = sorted.getOrNull(index)?.position
        return when {
            prev == null && next == null -> 1.0
            prev == null && next != null -> next - 1.0
            prev != null && next == null -> prev + 1.0
            else -> (prev!! + next!!) / 2.0
        }
    }

    private fun bucketByColumn(all: List<CardEntity>, now: Long): Map<Column, List<CardEntity>> =
        Column.values().associateWith { col ->
            val cards = all.filter { it.effectiveColumn(now) == col }
            when (col) {
                Column.TODO -> cards.sortedWith(
                    compareBy<CardEntity> { it.todoSortBucket(now) }
                        .thenBy { it.startUtc ?: Long.MAX_VALUE }
                        .thenBy { it.position },
                )
                else -> cards.sortedWith(compareBy({ it.startUtc ?: Long.MAX_VALUE }, { it.position }))
            }
        }

    private fun nextClockRefreshDelay(cards: List<CardEntity>, now: Long): Long {
        val next = cards.asSequence()
            .mapNotNull { it.nextClockTransitionAfter(now) }
            .minOrNull()
        val targetDelay = next?.let { it - now } ?: MAX_CLOCK_REFRESH_MS
        return targetDelay.coerceIn(MIN_CLOCK_REFRESH_MS, MAX_CLOCK_REFRESH_MS)
    }

    private companion object {
        const val MIN_CLOCK_REFRESH_MS = 1_000L
        const val MAX_CLOCK_REFRESH_MS = 60_000L
    }
}
