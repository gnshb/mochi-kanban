package com.mochikanban.app.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Lightweight TickTick-style natural-language date/time recogniser. Detects
 * phrases like "tomorrow 15:00", "next mon 3pm", "in 2 days", "tonight" inside a
 * task title, returns the resolved start time and the title with the phrase removed.
 */
object NaturalDateParser {

    data class Result(val startUtc: Long, val cleanedTitle: String)

    private val WEEKDAYS = linkedMapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )

    private val TIME_RE = Regex(
        """\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b|\b(?:at\s+)?(\d{1,2}):(\d{2})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val IN_RE = Regex(
        """\bin\s+(\d{1,3})\s+(days?|weeks?|hours?|min(?:ute)?s?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val NEXT_WEEKDAY_RE = Regex(
        """\bnext\s+(${WEEKDAYS.keys.joinToString("|")})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val WEEKDAY_RE = Regex(
        """\b(${WEEKDAYS.keys.joinToString("|")})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TODAY_RE = Regex("""\b(today|tonight)\b""", RegexOption.IGNORE_CASE)
    private val TOMORROW_RE = Regex("""\b(tomorrow|tmrw|tmr)\b""", RegexOption.IGNORE_CASE)

    private val MONTHS = linkedMapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9,
        "october" to 10, "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12,
    )
    private val monthAlt = MONTHS.keys.joinToString("|")
    // "5 Jun", "5th June 2026"
    private val DAY_MONTH_RE = Regex(
        """\b(\d{1,2})(?:st|nd|rd|th)?\s+($monthAlt)\b(?:,?\s*(\d{4}))?""",
        RegexOption.IGNORE_CASE,
    )
    // "Jun 5", "June 5th, 2026"
    private val MONTH_DAY_RE = Regex(
        """\b($monthAlt)\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s*(\d{4}))?""",
        RegexOption.IGNORE_CASE,
    )
    // "5/6", "6-5-2026" — day/month[/year] (day-first locale).
    private val NUMERIC_RE = Regex("""\b(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""")

    fun parse(
        title: String,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMs: Long = System.currentTimeMillis(),
    ): Result? {
        if (title.isBlank()) return null
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val today = now.toLocalDate()
        val ranges = mutableListOf<IntRange>()

        var date: LocalDate? = null
        var time: LocalTime? = null
        var evening = false

        // Time of day.
        TIME_RE.find(title)?.let { m ->
            val g = m.groupValues
            time = if (g[1].isNotEmpty()) {
                var h = g[1].toInt() % 12
                if (g[3].equals("pm", true)) h += 12
                safeTime(h, g[2].toIntOrNull() ?: 0)
            } else {
                safeTime(g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0)
            }
            if (time != null) ranges += m.range
        }

        // Date — first matching phrase wins.
        IN_RE.find(title)?.let { m ->
            val n = m.groupValues[1].toLong()
            val unit = m.groupValues[2].lowercase()
            date = when {
                unit.startsWith("day") -> today.plusDays(n)
                unit.startsWith("week") -> today.plusWeeks(n)
                unit.startsWith("hour") -> { time = now.plusHours(n).toLocalTime(); today.plusDays(if (now.toLocalTime().plusHours(n).isBefore(now.toLocalTime())) 1 else 0) }
                else -> { time = now.plusMinutes(n).toLocalTime(); today }
            }
            ranges += m.range
        }
        if (date == null) {
            DAY_MONTH_RE.find(title)?.let { m ->
                resolveDate(m.groupValues[1].toInt(), MONTHS[m.groupValues[2].lowercase()] ?: 0, m.groupValues[3], today)
                    ?.let { date = it; ranges += m.range }
            }
        }
        if (date == null) {
            MONTH_DAY_RE.find(title)?.let { m ->
                resolveDate(m.groupValues[2].toInt(), MONTHS[m.groupValues[1].lowercase()] ?: 0, m.groupValues[3], today)
                    ?.let { date = it; ranges += m.range }
            }
        }
        if (date == null) {
            NUMERIC_RE.find(title)?.let { m ->
                resolveDate(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0, m.groupValues[3], today)
                    ?.let { date = it; ranges += m.range }
            }
        }
        if (date == null) {
            NEXT_WEEKDAY_RE.find(title)?.let { m ->
                val dow = WEEKDAYS[m.groupValues[1].lowercase()]!!
                date = today.with(TemporalAdjusters.next(dow))
                ranges += m.range
            }
        }
        if (date == null) {
            TOMORROW_RE.find(title)?.let { m -> date = today.plusDays(1); ranges += m.range }
        }
        if (date == null) {
            TODAY_RE.find(title)?.let { m ->
                date = today
                if (m.value.equals("tonight", true)) evening = true
                ranges += m.range
            }
        }
        if (date == null) {
            WEEKDAY_RE.find(title)?.let { m ->
                val dow = WEEKDAYS[m.groupValues[1].lowercase()]!!
                date = today.with(TemporalAdjusters.nextOrSame(dow))
                ranges += m.range
            }
        }

        // Need at least one of date/time to have matched.
        if (date == null && time == null) return null

        val resolvedTime = time ?: if (evening) LocalTime.of(19, 0) else LocalTime.of(9, 0)
        var resolvedDate = date ?: today
        // A bare time already past today rolls to tomorrow.
        if (date == null && resolvedDate.atTime(resolvedTime).isBefore(now.toLocalDateTime())) {
            resolvedDate = today.plusDays(1)
        }

        val startUtc = resolvedDate.atTime(resolvedTime).atZone(zone).toInstant().toEpochMilli()
        return Result(startUtc, stripRanges(title, ranges))
    }

    private fun safeTime(h: Int, m: Int): LocalTime? =
        if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null

    private fun resolveDate(day: Int, month: Int, yearStr: String, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        return runCatching {
            if (yearStr.isNotEmpty()) {
                val y = yearStr.toInt().let { if (it < 100) 2000 + it else it }
                LocalDate.of(y, month, day)
            } else {
                // No year: pick this year, rolling to next if the date already passed.
                LocalDate.of(today.year, month, day).let { if (it.isBefore(today)) it.plusYears(1) else it }
            }
        }.getOrNull()
    }

    private fun stripRanges(title: String, ranges: List<IntRange>): String {
        var result = title
        ranges.sortedByDescending { it.first }.forEach { r ->
            if (r.first in result.indices && r.last < result.length) {
                result = result.removeRange(r.first, r.last + 1)
            }
        }
        return result.replace(Regex("""\s{2,}"""), " ").trim()
    }
}
