package com.mochikanban.app.sync.net

import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.data.db.entity.LabelEntity
import com.mochikanban.app.data.repo.LabelRepository
import com.mochikanban.app.domain.GoogleCalendarColors
import com.mochikanban.app.domain.SyncState
import com.mochikanban.app.sync.engine.ColumnMapper
import com.mochikanban.app.util.ChecklistCodec
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventMapper @Inject constructor(
    private val labelRepo: LabelRepository,
) {

    suspend fun fromRemote(
        event: EventDto,
        calendarId: String,
        existing: CardEntity?,
        calendarColorHex: String? = null,
    ): CardEntity? {
        if (event.id == null) return null
        val cancelled = event.status == "cancelled"
        val recurring = event.recurringEventId != null || event.recurrence != null

        val priv = event.extendedProperties?.private.orEmpty()
        val column = ColumnMapper.fromExtended(priv[ColumnMapper.KEY_COLUMN])
        val labelName = priv[KEY_LABEL_NAME]
        val labelColor = priv[KEY_LABEL_COLOR]
        // Use the event's own Google Calendar colour (its colorId, else the
        // calendar's colour) when it carries no app-defined label.
        val eventColorHex = GoogleCalendarColors.colorForId(event.colorId) ?: calendarColorHex
        val colorLabelId = eventColorHex?.let { labelRepo.ensureColorLabel(it).id }
        val labelId = resolveLabel(labelName, labelColor) ?: existing?.labelId ?: colorLabelId

        val startMs = event.start?.dateTime?.let(::parseRfc3339)
            ?: event.start?.date?.let { parseDate(it) }
        val endMs = event.end?.dateTime?.let(::parseRfc3339)
        val duration = if (startMs != null && endMs != null) {
            ((endMs - startMs) / 60_000L).toInt().coerceAtLeast(0)
        } else existing?.durationMin

        val remoteUpdated = event.updated?.let(::parseRfc3339)

        val checklist = ChecklistCodec.fromMarkdown(event.description)
        val checklistJson = ChecklistCodec.encode(checklist) ?: existing?.checklist

        val base = existing ?: CardEntity(id = UUID.randomUUID().toString(), title = "")
        return base.copy(
            title = event.summary.orEmpty(),
            checklist = checklistJson,
            startUtc = startMs,
            durationMin = duration,
            column = column,
            labelId = labelId,
            calendarId = calendarId,
            remoteEventId = event.id,
            etag = event.etag,
            updatedAtRemote = remoteUpdated,
            readOnly = recurring,
            deletedLocal = cancelled,
            dirty = false,
            syncState = SyncState.IDLE,
        )
    }

    suspend fun toRemote(card: CardEntity): EventDto {
        val checklist = ChecklistCodec.decode(card.checklist)
        val label: LabelEntity? = card.labelId?.let { labelRepo.byId(it) }
        // Hidden colour-only labels carry an event's Google colour, not a user label.
        val isColorOnly = label?.name?.startsWith(LabelRepository.EVENT_COLOR_LABEL_PREFIX) == true
        val priv = buildMap {
            put(ColumnMapper.KEY_COLUMN, ColumnMapper.toExtended(card.column))
            if (label != null && !isColorOnly) {
                put(KEY_LABEL_NAME, label.name)
                put(KEY_LABEL_COLOR, label.colorHex)
            }
        }
        // Reflect the card's colour onto Google only when it exactly matches an
        // available Calendar event colour. Do not approximate user colours.
        val colorId = GoogleCalendarColors.exactColorIdFor(label?.colorHex)
        val start = card.startUtc?.let {
            EventDateTimeDto(dateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }
        val endMs = card.startUtc?.let { it + ((card.durationMin ?: 60) * 60_000L) }
        val end = endMs?.let {
            EventDateTimeDto(dateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }
        return EventDto(
            id = card.remoteEventId,
            summary = card.title,
            description = ChecklistCodec.toMarkdown(checklist),
            start = start,
            end = end,
            colorId = colorId,
            extendedProperties = ExtendedPropertiesDto(private = priv),
            // The app drives its own reminders; suppress Google's default popup.
            reminders = RemindersDto(useDefault = false),
        )
    }

    private suspend fun resolveLabel(name: String?, colorHex: String?): String? {
        if (name.isNullOrBlank()) return null
        val existing = labelRepo.byName(name)
        if (existing != null) return existing.id
        val color = GoogleCalendarColors.normalizeHex(colorHex) ?: "#33B679"
        return labelRepo.add(name, color).id
    }

    private fun parseRfc3339(value: String): Long? = runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.recoverCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

    // All-day events have a `date` (no time); place them at local 00:00.
    private fun parseDate(value: String): Long? = runCatching {
        java.time.LocalDate.parse(value)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }.getOrNull()

    companion object {
        const val KEY_LABEL_NAME = "mochikanban_label_name"
        const val KEY_LABEL_COLOR = "mochikanban_label_color"
    }
}
