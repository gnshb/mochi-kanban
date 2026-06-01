package com.mochikanban.app.sync.net

import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.data.db.entity.LabelEntity
import com.mochikanban.app.data.repo.LabelRepository
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
        val eventColorHex = EVENT_COLORS[event.colorId] ?: calendarColorHex
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
        // Reflect the card's colour onto the Google event via the nearest colorId.
        val colorId = label?.colorHex?.let(::nearestColorId)
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

    /**
     * Nearest Google event colorId for a hex string. Uses the "redmean" weighted
     * distance, which tracks human colour perception far better than plain RGB.
     */
    private fun nearestColorId(hex: String): String? {
        val target = parseRgb(hex) ?: return null
        return EVENT_COLORS.minByOrNull { (_, candidate) ->
            parseRgb(candidate)?.let { redmeanDistance(target, it) } ?: Long.MAX_VALUE
        }?.key
    }

    private fun redmeanDistance(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Long {
        val rMean = (a.first + b.first) / 2
        val dr = (a.first - b.first).toLong()
        val dg = (a.second - b.second).toLong()
        val db = (a.third - b.third).toLong()
        return (((512 + rMean) * dr * dr) shr 8) + 4 * dg * dg + (((767 - rMean) * db * db) shr 8)
    }

    private fun parseRgb(hex: String): Triple<Int, Int, Int>? {
        val h = hex.removePrefix("#")
        if (h.length != 6) return null
        return runCatching {
            Triple(
                h.substring(0, 2).toInt(16),
                h.substring(2, 4).toInt(16),
                h.substring(4, 6).toInt(16),
            )
        }.getOrNull()
    }

    private suspend fun resolveLabel(name: String?, colorHex: String?): String? {
        if (name.isNullOrBlank()) return null
        val existing = labelRepo.byName(name)
        if (existing != null) return existing.id
        val color = colorHex?.takeIf { it.isNotBlank() } ?: "#86E7BF"
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

        /** Google Calendar event colour palette (colorId → hex). */
        val EVENT_COLORS: Map<String, String> = mapOf(
            "1" to "#7986CB", // Lavender
            "2" to "#33B679", // Sage
            "3" to "#8E24AA", // Grape
            "4" to "#E67C73", // Flamingo
            "5" to "#F6BF26", // Banana
            "6" to "#F4511E", // Tangerine
            "7" to "#039BE5", // Peacock
            "8" to "#616161", // Graphite
            "9" to "#3F51B5", // Blueberry
            "10" to "#0B8043", // Basil
            "11" to "#D50000", // Tomato
        )
    }
}
