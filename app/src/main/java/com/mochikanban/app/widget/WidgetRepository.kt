package com.mochikanban.app.widget

import com.mochikanban.app.data.db.dao.CardDao
import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.util.Time
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetRepository @Inject constructor(
    private val cardDao: CardDao,
) {
    suspend fun todayCards(): List<CardEntity> =
        cardDao.todayWindowSnapshot(Time.startOfToday(), Time.endOfToday())

    /** All non-deleted cards; the widget buckets/sorts them by clock time itself. */
    suspend fun allCards(): List<CardEntity> = cardDao.allSnapshot()
}
