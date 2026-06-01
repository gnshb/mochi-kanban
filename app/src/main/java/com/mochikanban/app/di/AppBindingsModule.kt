package com.mochikanban.app.di

import com.mochikanban.app.sync.SyncTrigger
import com.mochikanban.app.sync.WorkManagerSyncTrigger
import com.mochikanban.app.widget.GlanceWidgetUpdater
import com.mochikanban.app.widget.WidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {

    @Binds @Singleton
    abstract fun bindSyncTrigger(impl: WorkManagerSyncTrigger): SyncTrigger

    @Binds @Singleton
    abstract fun bindWidgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater
}

// GlanceWidgetUpdater is also @Inject'd directly elsewhere (Settings VM) without the interface binding.
