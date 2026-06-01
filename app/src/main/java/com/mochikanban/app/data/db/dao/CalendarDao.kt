package com.mochikanban.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mochikanban.app.data.db.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendars ORDER BY accountEmail ASC, `primary` DESC, summary ASC")
    fun observe(): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars WHERE selected = 1")
    suspend fun selected(): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): CalendarEntity?

    @Query("SELECT * FROM calendars WHERE accountEmail = :email")
    suspend fun forAccount(email: String): List<CalendarEntity>

    @Upsert
    suspend fun upsertAll(calendars: List<CalendarEntity>)

    @Query("UPDATE calendars SET selected = :selected WHERE id = :id")
    suspend fun setSelected(id: String, selected: Boolean)

    @Query("DELETE FROM calendars WHERE accountEmail = :email")
    suspend fun clearForAccount(email: String)

    @Query("DELETE FROM calendars")
    suspend fun clear()
}
