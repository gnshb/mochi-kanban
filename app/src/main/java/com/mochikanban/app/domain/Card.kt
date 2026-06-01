package com.mochikanban.app.domain

data class Card(
    val id: String,
    val title: String,
    val startUtc: Long? = null,
    val durationMin: Int? = null,
    val column: Column = Column.TODO,
    val position: Double = 0.0,
    val labelId: String? = null,
    val reminderAtUtc: Long? = null,
    val readOnly: Boolean = false,
    val calendarId: String? = null,
    val remoteEventId: String? = null,
)
