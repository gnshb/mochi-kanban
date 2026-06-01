package com.mochikanban.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.mochikanban.app.data.db.entity.OutboxEntity

@Dao
interface OutboxDao {
    @Insert
    suspend fun enqueue(entry: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE attemptCount < :maxAttempts ORDER BY id ASC")
    suspend fun pending(maxAttempts: Int = 5): List<OutboxEntity>

    @Update
    suspend fun update(entry: OutboxEntity)

    @Delete
    suspend fun delete(entry: OutboxEntity)

    @Query("DELETE FROM outbox WHERE cardId = :cardId")
    suspend fun deleteByCard(cardId: String)
}
