package com.mochikanban.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Hex color including leading '#', e.g. "#86E7BF". */
    val colorHex: String,
    val sortOrder: Int,
)
