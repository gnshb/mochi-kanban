package com.mochikanban.app.data.db.entity

import com.mochikanban.app.domain.Column
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

    private fun card(
        startUtc: Long,
        durationMin: Int,
        column: Column = Column.TODO,
    ): CardEntity = CardEntity(
        id = "card",
        title = "Task",
        startUtc = startUtc,
        durationMin = durationMin,
        column = column,
    )
}
