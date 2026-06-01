package com.mochikanban.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochikanban.app.domain.OpType

@Entity(
    tableName = "outbox",
    indices = [Index("cardId"), Index("attemptCount")],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val opType: OpType,
    val payloadJson: String,
    val attemptCount: Int = 0,
    val lastTriedAt: Long? = null,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
