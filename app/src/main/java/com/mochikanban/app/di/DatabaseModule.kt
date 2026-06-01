package com.mochikanban.app.di

import android.content.Context
import androidx.room.Room
import com.mochikanban.app.data.db.KanbanDatabase
import com.mochikanban.app.data.db.dao.CalendarDao
import com.mochikanban.app.data.db.dao.CardDao
import com.mochikanban.app.data.db.dao.LabelDao
import com.mochikanban.app.data.db.dao.OutboxDao
import com.mochikanban.app.data.db.dao.SyncStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): KanbanDatabase =
        Room.databaseBuilder(ctx, KanbanDatabase::class.java, "mochi-kanban.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideCardDao(db: KanbanDatabase): CardDao = db.cardDao()
    @Provides fun provideOutboxDao(db: KanbanDatabase): OutboxDao = db.outboxDao()
    @Provides fun provideSyncStateDao(db: KanbanDatabase): SyncStateDao = db.syncStateDao()
    @Provides fun provideCalendarDao(db: KanbanDatabase): CalendarDao = db.calendarDao()
    @Provides fun provideLabelDao(db: KanbanDatabase): LabelDao = db.labelDao()
}
