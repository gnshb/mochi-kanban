package com.mochikanban.app.sync.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventListResponse(
    val items: List<EventDto> = emptyList(),
    val nextPageToken: String? = null,
    val nextSyncToken: String? = null,
)

@Serializable
data class CalendarListResponse(
    val items: List<CalendarListEntryDto> = emptyList(),
)

@Serializable
data class CalendarListEntryDto(
    val id: String,
    val summary: String,
    val primary: Boolean? = null,
    /** Calendar's display colour as a hex string, e.g. "#9fe1e7". */
    val backgroundColor: String? = null,
)

@Serializable
data class EventDto(
    val id: String? = null,
    val etag: String? = null,
    val status: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val start: EventDateTimeDto? = null,
    val end: EventDateTimeDto? = null,
    val updated: String? = null,
    /** Google event colour index ("1".."11"); null means the calendar's colour. */
    val colorId: String? = null,
    @SerialName("recurringEventId")
    val recurringEventId: String? = null,
    val recurrence: List<String>? = null,
    val extendedProperties: ExtendedPropertiesDto? = null,
    val reminders: RemindersDto? = null,
)

@Serializable
data class RemindersDto(
    val useDefault: Boolean,
    val overrides: List<ReminderOverrideDto> = emptyList(),
)

@Serializable
data class ReminderOverrideDto(
    val method: String,
    val minutes: Int,
)

@Serializable
data class EventDateTimeDto(
    val date: String? = null,
    val dateTime: String? = null,
    val timeZone: String? = null,
)

@Serializable
data class ExtendedPropertiesDto(
    val private: Map<String, String> = emptyMap(),
)
