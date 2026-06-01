package com.mochikanban.app.data.repo

import com.mochikanban.app.data.db.dao.CalendarDao
import com.mochikanban.app.data.db.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarDao: CalendarDao,
) {
    fun observe(): Flow<List<CalendarEntity>> = calendarDao.observe()
    suspend fun selected(): List<CalendarEntity> = calendarDao.selected()
    suspend fun upsertAll(calendars: List<CalendarEntity>) = calendarDao.upsertAll(calendars)
    suspend fun setSelected(id: String, selected: Boolean) = calendarDao.setSelected(id, selected)
    suspend fun clear() = calendarDao.clear()
    suspend fun clearForAccount(email: String) = calendarDao.clearForAccount(email)
}
