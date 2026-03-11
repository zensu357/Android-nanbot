package com.example.nanobot.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nanobot.core.database.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerAt ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY triggerAt ASC")
    suspend fun getReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminder(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE triggerAt <= :now AND status = :scheduledStatus ORDER BY triggerAt ASC")
    suspend fun getDueReminders(now: Long, scheduledStatus: String): List<ReminderEntity>

    @Query("UPDATE reminders SET status = :status, deliveredAt = :deliveredAt, errorMessage = NULL WHERE id = :id")
    suspend fun markDelivered(id: String, status: String, deliveredAt: Long)

    @Query("UPDATE reminders SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun markFailed(id: String, status: String, errorMessage: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)
}
