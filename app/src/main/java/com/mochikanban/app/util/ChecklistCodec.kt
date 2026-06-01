package com.mochikanban.app.util

import com.mochikanban.app.domain.Checklist
import com.mochikanban.app.domain.ChecklistItem
import kotlinx.serialization.json.Json
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

object ChecklistCodec {
    fun encode(checklist: Checklist?): String? =
        if (checklist == null || checklist.isEmpty) null
        else json.encodeToString(Checklist.serializer(), checklist)

    fun decode(raw: String?): Checklist {
        if (raw.isNullOrBlank()) return Checklist()
        return runCatching { json.decodeFromString(Checklist.serializer(), raw) }
            .getOrElse { Checklist() }
    }

    /** Roundtrips checklist <-> iCal-friendly markdown lines like "- [x] item". */
    fun toMarkdown(checklist: Checklist): String? {
        if (checklist.isEmpty) return null
        return checklist.items.joinToString("\n") {
            "- [${if (it.done) "x" else " "}] ${it.text}"
        }
    }

    fun fromMarkdown(text: String?): Checklist {
        if (text.isNullOrBlank()) return Checklist()
        val items = text.lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                val match = Regex("""^-\s*\[(x|X| )]\s*(.*)$""").matchEntire(line)
                    ?: return@mapNotNull null
                val done = match.groupValues[1].equals("x", ignoreCase = true)
                val body = match.groupValues[2]
                ChecklistItem(id = UUID.randomUUID().toString(), text = body, done = done)
            }
            .toList()
        return Checklist(items)
    }
}
