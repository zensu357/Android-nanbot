package com.example.nanobot.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nanobot.core.database.entity.MemoryFactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryFactDao {
    @Query("SELECT * FROM memory_facts ORDER BY updatedAt DESC")
    fun observeFacts(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_facts ORDER BY updatedAt DESC")
    suspend fun getFacts(): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE sourceSessionId = :sessionId ORDER BY updatedAt DESC")
    suspend fun getFactsForSession(sessionId: String): List<MemoryFactEntity>

    @Query("DELETE FROM memory_facts WHERE id = :factId")
    suspend fun deleteById(factId: String)

    @Query("DELETE FROM memory_facts WHERE sourceSessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM memory_facts WHERE id NOT IN (SELECT id FROM memory_facts ORDER BY updatedAt DESC LIMIT :maxFacts)")
    suspend fun pruneToLatest(maxFacts: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memoryFact: MemoryFactEntity)
}
