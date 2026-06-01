package com.mochikanban.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mochikanban.app.data.db.entity.SyncStateEntity

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE calendarId = :calendarId LIMIT 1")
    suspend fun get(calendarId: String): SyncStateEntity?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE calendarId = :calendarId")
    suspend fun delete(calendarId: String)
}
