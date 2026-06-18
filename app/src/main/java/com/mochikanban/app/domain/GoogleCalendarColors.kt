package com.mochikanban.app.domain

object GoogleCalendarColors {
    const val DEFAULT_EVENT_COLOR_ID = "7"

    val eventColors: Map<String, String> = linkedMapOf(
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

    val eventPalette: List<String> = eventColors.values.toList()
    val defaultEventColor: String = eventColors.getValue(DEFAULT_EVENT_COLOR_ID)

    fun colorForId(colorId: String?): String? =
        colorId?.let { eventColors[it] }

    fun exactColorIdFor(hex: String?): String? {
        val normalized = normalizeHex(hex) ?: return null
        return eventColors.entries.firstOrNull { (_, color) ->
            normalizeHex(color) == normalized
        }?.key
    }

    fun normalizeHex(hex: String?): String? {
        val h = hex?.trim()?.removePrefix("#") ?: return null
        if (h.length != 6 || h.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
        return "#${h.uppercase()}"
    }
}
