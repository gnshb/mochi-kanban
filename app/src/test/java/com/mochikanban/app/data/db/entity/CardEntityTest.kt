package com.mochikanban.app.data.db.entity

import com.mochikanban.app.domain.Column
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardEntityTest {
    @Test
    fun timedCardBecomesActionRequiredTodoAfterEnd() {
        val start = 10_000L
        val card = card(startUtc = start, durationMin = 30)
        val afterEnd = start + 31 * 60_000L

        assertEquals(Column.TODO, card.effectiveColumn(afterEnd))
        assertTrue(card.isActionRequired(afterEnd))
        assertEquals(0, card.todoSortBucket(afterEnd))
    }

    @Test
    fun manualDoneStaysDoneAfterScheduledEnd() {
        val start = 10_000L
        val card = card(startUtc = start, durationMin = 30, column = Column.DONE)
        val afterEnd = start + 31 * 60_000L

        assertEquals(Column.DONE, card.effectiveColumn(afterEnd))
        assertFalse(card.isActionRequired(afterEnd))
    }

    @Test
    fun leadWindowStartsOneHourBeforeTaskStart() {
        val start = 10_000_000L
        val card = card(startUtc = start, durationMin = 30)

        assertFalse(card.isAttentionWindow(start - CardEntity.ATTENTION_LEAD_MS - 1))
        assertTrue(card.isAttentionWindow(start - CardEntity.ATTENTION_LEAD_MS))
        assertTrue(card.isAttentionWindow(start + 10_000L))
    }

    @Test
    fun scheduledCardBeforeTodayIsStale() {
        val card = card(startUtc = 10_000L, durationMin = 30)
        val dayStart = card.endUtc()!! + 1

        assertTrue(card.isScheduledBefore(dayStart))
        assertFalse(card.isScheduledBefore(card.endUtc()!!))
    }

    @Test
    fun detectsLegacyAllDayImportShape() {
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

        assertTrue(
            card(startUtc = midnight, durationMin = null, remoteEventId = "remote")
                .isLikelyLegacyAllDayImport(zone),
        )
        assertFalse(
            card(startUtc = midnight, durationMin = 60, remoteEventId = "remote")
                .isLikelyLegacyAllDayImport(zone),
        )
    }

    @Test
    fun localMidnightCardsAreNotLegacyAllDayImports() {
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

        assertFalse(card(startUtc = midnight, durationMin = null).isLikelyLegacyAllDayImport(zone))
    }

    private fun card(
        startUtc: Long,
        durationMin: Int?,
        column: Column = Column.TODO,
        remoteEventId: String? = null,
    ): CardEntity = CardEntity(
        id = "card",
        title = "Task",
        startUtc = startUtc,
        durationMin = durationMin,
        column = column,
        remoteEventId = remoteEventId,
    )
}
