package com.mochikanban.app.data.repo

import com.mochikanban.app.data.db.dao.LabelDao
import com.mochikanban.app.data.db.entity.LabelEntity
import com.mochikanban.app.domain.GoogleCalendarColors
import com.mochikanban.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabelRepository @Inject constructor(
    private val labelDao: LabelDao,
    private val widget: WidgetUpdater,
) {
    fun observe(): Flow<List<LabelEntity>> = labelDao.observe()
    fun observeVisible(): Flow<List<LabelEntity>> = labelDao.observe().map { visibleLabels(it) }
    suspend fun all(): List<LabelEntity> = labelDao.all()
    suspend fun visible(): List<LabelEntity> = visibleLabels(labelDao.all())
    suspend fun byId(id: String): LabelEntity? = labelDao.byId(id)
    suspend fun byName(name: String): LabelEntity? = labelDao.byName(name)

    suspend fun ensureDefaults() {
        val current = labelDao.all()
        if (current.isNotEmpty()) {
            migrateLegacyDefaultColors(current)
            return
        }
        val seeds = listOf(
            LabelEntity(id = UUID.randomUUID().toString(), name = "Calm", colorHex = "#33B679", sortOrder = 0),
            LabelEntity(id = UUID.randomUUID().toString(), name = "Heart", colorHex = "#E67C73", sortOrder = 1),
            LabelEntity(id = UUID.randomUUID().toString(), name = "Focus", colorHex = "#3F51B5", sortOrder = 2),
            LabelEntity(id = UUID.randomUUID().toString(), name = "Hustle", colorHex = "#F6BF26", sortOrder = 3),
            LabelEntity(id = UUID.randomUUID().toString(), name = "Dream", colorHex = "#8E24AA", sortOrder = 4),
            LabelEntity(id = UUID.randomUUID().toString(), name = "Urgent", colorHex = "#D50000", sortOrder = 5),
        )
        labelDao.upsertAll(seeds)
    }

    suspend fun add(name: String, colorHex: String, refreshWidget: Boolean = true): LabelEntity {
        val nextOrder = (all().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val label = LabelEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Label" },
            colorHex = colorHex,
            sortOrder = nextOrder,
        )
        labelDao.upsert(label)
        if (refreshWidget) widget.refresh()
        return label
    }

    suspend fun update(label: LabelEntity) {
        labelDao.upsert(label)
        widget.refresh()
    }

    suspend fun delete(id: String) {
        labelDao.deleteById(id)
        widget.refresh()
    }

    /**
     * A hidden label that carries a calendar event's own colour. Deduped by colour
     * (one label per distinct hex) and named with [EVENT_COLOR_LABEL_PREFIX] so it's
     * filtered out of the user-facing palette.
     */
    suspend fun ensureColorLabel(colorHex: String): LabelEntity {
        val normalized = GoogleCalendarColors.normalizeHex(colorHex) ?: colorHex
        val name = EVENT_COLOR_LABEL_PREFIX + normalized.lowercase()
        byName(name)?.let { return it }
        return add(name, normalized, refreshWidget = false)
    }

    private fun visibleLabels(labels: List<LabelEntity>): List<LabelEntity> =
        labels
            .filterNot { it.name.startsWith(EVENT_COLOR_LABEL_PREFIX) }
            .distinctBy { it.name.trim().lowercase() }

    private suspend fun migrateLegacyDefaultColors(labels: List<LabelEntity>) {
        val updates = labels.mapNotNull { label ->
            val replacement = LEGACY_DEFAULT_COLOR_MIGRATIONS[label.name] ?: return@mapNotNull null
            if (!label.colorHex.equals(replacement.first, ignoreCase = true)) return@mapNotNull null
            label.copy(colorHex = replacement.second)
        }
        if (updates.isNotEmpty()) labelDao.upsertAll(updates)
    }

    companion object {
        const val EVENT_COLOR_LABEL_PREFIX = "evtcolor:"

        private val LEGACY_DEFAULT_COLOR_MIGRATIONS = mapOf(
            "Calm" to ("#86E7BF" to "#33B679"),
            "Heart" to ("#FFB4C4" to "#E67C73"),
            "Focus" to ("#B6D0FF" to "#3F51B5"),
            "Hustle" to ("#FFD988" to "#F6BF26"),
            "Dream" to ("#D4BBFF" to "#8E24AA"),
            "Urgent" to ("#FFB39E" to "#D50000"),
        )
    }
}
