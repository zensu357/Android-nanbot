package com.example.nanobot.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.nanobot.core.database.entity.BehaviorEventEntity

@Dao
interface BehaviorEventDao {
    @Insert
    suspend fun insert(event: BehaviorEventEntity)

    @Query("SELECT * FROM behavior_events WHERE timestamp > :since ORDER BY timestamp DESC")
    suspend fun getEventsSince(since: Long): List<BehaviorEventEntity>

    @Query("SELECT COUNT(*) FROM behavior_events WHERE timestamp > :since")
    suspend fun countSince(since: Long): Int

    @Query("DELETE FROM behavior_events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
