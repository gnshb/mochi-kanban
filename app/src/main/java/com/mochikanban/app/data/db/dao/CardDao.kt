package com.mochikanban.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.domain.Column
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards WHERE deletedLocal = 0 ORDER BY position ASC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE remoteEventId = :remoteId LIMIT 1")
    suspend fun byRemoteId(remoteId: String): CardEntity?

    @Query("""
        SELECT * FROM cards
        WHERE deletedLocal = 0
          AND (
                (startUtc IS NOT NULL AND startUtc BETWEEN :fromMs AND :toMs)
             OR (startUtc IS NULL AND column = 'TODO')
          )
        ORDER BY column ASC, position ASC
    """)
    fun observeTodayWindow(fromMs: Long, toMs: Long): Flow<List<CardEntity>>

    @Query("""
        SELECT * FROM cards
        WHERE deletedLocal = 0
          AND (
                (startUtc IS NOT NULL AND startUtc BETWEEN :fromMs AND :toMs)
             OR (startUtc IS NULL AND column = 'TODO')
          )
        ORDER BY column ASC, position ASC
    """)
    suspend fun todayWindowSnapshot(fromMs: Long, toMs: Long): List<CardEntity>

    @Query("SELECT * FROM cards WHERE column = :col AND deletedLocal = 0 ORDER BY position ASC")
    suspend fun byColumn(col: Column): List<CardEntity>

    @Query("SELECT * FROM cards WHERE deletedLocal = 0")
    suspend fun allSnapshot(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE dirty = 1 AND deletedLocal = 0")
    suspend fun dirty(): List<CardEntity>

    @Upsert
    suspend fun upsert(card: CardEntity)

    @Upsert
    suspend fun upsertAll(cards: List<CardEntity>)

    @Update
    suspend fun update(card: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE cards SET deletedLocal = 1, dirty = 1, updatedAtLocal = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long = System.currentTimeMillis())
}
