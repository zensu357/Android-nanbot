package com.example.nanobot.data.repository

import com.example.nanobot.core.database.dao.ReminderDao
import com.example.nanobot.core.model.Reminder
import com.example.nanobot.core.model.ReminderStatus
import com.example.nanobot.data.mapper.toEntity
import com.example.nanobot.data.mapper.toModel
import com.example.nanobot.domain.repository.ReminderRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao
) : ReminderRepository {
    override fun observeReminders(): Flow<List<Reminder>> =
        reminderDao.observeReminders().map { reminders -> reminders.map { it.toModel() } }

    override suspend fun getReminders(): List<Reminder> =
        reminderDao.getReminders().map { it.toModel() }

    override suspend fun getReminder(id: String): Reminder? =
        reminderDao.getReminder(id)?.toModel()

    override suspend fun getDueReminders(now: Long): List<Reminder> =
        reminderDao.getDueReminders(now, ReminderStatus.SCHEDULED).map { it.toModel() }

    override suspend fun markDelivered(id: String, deliveredAt: Long) {
        reminderDao.markDelivered(id, ReminderStatus.DELIVERED, deliveredAt)
    }

    override suspend fun markFailed(id: String, errorMessage: String) {
        reminderDao.markFailed(id, ReminderStatus.FAILED, errorMessage)
    }

    override suspend fun upsert(reminder: Reminder) {
        reminderDao.upsert(reminder.toEntity())
    }
}
