package com.mochikanban.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mochikanban.app.data.db.dao.CalendarDao
import com.mochikanban.app.data.db.dao.CardDao
import com.mochikanban.app.data.db.dao.LabelDao
import com.mochikanban.app.data.db.dao.OutboxDao
import com.mochikanban.app.data.db.dao.SyncStateDao
import com.mochikanban.app.data.db.entity.CalendarEntity
import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.data.db.entity.LabelEntity
import com.mochikanban.app.data.db.entity.OutboxEntity
import com.mochikanban.app.data.db.entity.SyncStateEntity

@Database(
    entities = [
        CardEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
        CalendarEntity::class,
        LabelEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class KanbanDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun calendarDao(): CalendarDao
    abstract fun labelDao(): LabelDao
}
