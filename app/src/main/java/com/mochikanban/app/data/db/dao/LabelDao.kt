package com.mochikanban.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mochikanban.app.data.db.entity.LabelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY sortOrder ASC, name ASC")
    fun observe(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels ORDER BY sortOrder ASC, name ASC")
    suspend fun all(): List<LabelEntity>

    @Query("SELECT * FROM labels WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): LabelEntity?

    @Query("SELECT * FROM labels WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun byName(name: String): LabelEntity?

    @Upsert
    suspend fun upsert(label: LabelEntity)

    @Upsert
    suspend fun upsertAll(labels: List<LabelEntity>)

    @Delete
    suspend fun delete(label: LabelEntity)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun deleteById(id: String)
}
