package com.example.nanobot.domain.repository

import com.example.nanobot.core.model.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observeReminders(): Flow<List<Reminder>>
    suspend fun getReminders(): List<Reminder>
    suspend fun getReminder(id: String): Reminder?
    suspend fun getDueReminders(now: Long): List<Reminder>
    suspend fun markDelivered(id: String, deliveredAt: Long)
    suspend fun markFailed(id: String, errorMessage: String)
    suspend fun upsert(reminder: Reminder)
}
