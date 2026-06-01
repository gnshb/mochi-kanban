package com.mochikanban.app.data.repo

import com.mochikanban.app.data.db.dao.LabelDao
import com.mochikanban.app.data.db.entity.LabelEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabelRepository @Inject constructor(
    private val labelDao: LabelDao,
) {
    fun observe(): Flow<List<LabelEntity>> = labelDao.observe()
    suspend fun all(): List<LabelEntity> = labelDao.all()
    suspend fun byId(id: String): LabelEntity? = labelDao.byId(id)
    suspend fun byName(name: String): LabelEntity? = labelDao.byName(name)

    suspend fun ensureDefaults() {
        if (labelDao.all().isNotEmpty()) return
        val seeds = listOf(
            LabelEntity(id = UUID.randomUUID().toString(), name = "Calm", colorHex = "#86E7BF", sortOrder = 0),
            LabelEntity(id = UUID.randomUUID().toString(), name = "Heart", colorHex = "#FFB4C4", sortOrder = 1),
            LabelEntity(id = UUID.randomUUID().toString(), name = "Focus", colorHex = "#B6D0FF", sortOrder = 2),
            LabelEntity(id = UUID.randomUUID().toString(), name = "Hustle", colorHex = "#FFD988", sortOrder = 3),
            LabelEntity(id = UUID.randomUUID().toString(), name = "Dream", colorHex = "#D4BBFF", sortOrder = 4),
            LabelEntity(id = UUID.randomUUID().toString(), name = "Urgent", colorHex = "#FFB39E", sortOrder = 5),
        )
        labelDao.upsertAll(seeds)
    }

    suspend fun add(name: String, colorHex: String): LabelEntity {
        val nextOrder = (all().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val label = LabelEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Label" },
            colorHex = colorHex,
            sortOrder = nextOrder,
        )
        labelDao.upsert(label)
        return label
    }

    suspend fun update(label: LabelEntity) = labelDao.upsert(label)

    suspend fun delete(id: String) = labelDao.deleteById(id)

    /**
     * A hidden label that carries a calendar event's own colour. Deduped by colour
     * (one label per distinct hex) and named with [EVENT_COLOR_LABEL_PREFIX] so it's
     * filtered out of the user-facing palette.
     */
    suspend fun ensureColorLabel(colorHex: String): LabelEntity {
        val name = EVENT_COLOR_LABEL_PREFIX + colorHex.lowercase()
        byName(name)?.let { return it }
        return add(name, colorHex)
    }

    companion object {
        const val EVENT_COLOR_LABEL_PREFIX = "evtcolor:"
    }
}
