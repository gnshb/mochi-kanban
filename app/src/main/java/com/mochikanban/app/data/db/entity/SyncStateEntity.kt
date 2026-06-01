package com.mochikanban.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val calendarId: String,
    val syncToken: String? = null,
    val lastFullSyncAt: Long? = null,
    val windowStart: Long? = null,
    val windowEnd: Long? = null,
)
