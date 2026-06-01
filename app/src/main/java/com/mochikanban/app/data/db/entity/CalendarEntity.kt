package com.mochikanban.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendars")
data class CalendarEntity(
    /** Calendar URL (acts as both PK and remote address). */
    @PrimaryKey val id: String,
    val accountEmail: String,
    val summary: String,
    val selected: Boolean = false,
    val primary: Boolean = false,
    /** Calendar's hex colour, the default for events without their own colorId. */
    val backgroundColor: String? = null,
)
