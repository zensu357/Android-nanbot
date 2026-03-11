package com.example.nanobot.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.nanobot.core.database.dao.MessageDao
import com.example.nanobot.core.database.dao.MemoryFactDao
import com.example.nanobot.core.database.dao.MemorySummaryDao
import com.example.nanobot.core.database.dao.ReminderDao
import com.example.nanobot.core.database.dao.SessionDao
import com.example.nanobot.core.database.entity.MessageEntity
import com.example.nanobot.core.database.entity.MemoryFactEntity
import com.example.nanobot.core.database.entity.MemorySummaryEntity
import com.example.nanobot.core.database.entity.ReminderEntity
import com.example.nanobot.core.database.entity.SessionEntity

@Database(
    entities = [SessionEntity::class, MessageEntity::class, MemoryFactEntity::class, MemorySummaryEntity::class, ReminderEntity::class],
    version = 7,
    exportSchema = false
)
abstract class NanobotDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun memorySummaryDao(): MemorySummaryDao
    abstract fun reminderDao(): ReminderDao
}
