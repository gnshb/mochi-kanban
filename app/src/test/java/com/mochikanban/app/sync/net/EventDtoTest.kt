package com.mochikanban.app.sync.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDtoTest {
    @Test
    fun detectsAllDayEvents() {
        assertTrue(
            EventDto(start = EventDateTimeDto(date = "2026-06-18")).isAllDay(),
        )
        assertFalse(
            EventDto(start = EventDateTimeDto(dateTime = "2026-06-18T10:00:00Z")).isAllDay(),
        )
    }
}
