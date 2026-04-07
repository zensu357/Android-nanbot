package com.example.nanobot.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.nanobot.core.database.dao.CustomSkillDao
import com.example.nanobot.core.database.dao.BehaviorEventDao
import com.example.nanobot.core.database.dao.MessageDao
import com.example.nanobot.core.database.dao.MemoryFactDao
import com.example.nanobot.core.database.dao.MemorySummaryDao
import com.example.nanobot.core.database.dao.ReminderDao
import com.example.nanobot.core.database.dao.SessionDao
import com.example.nanobot.core.database.dao.TaskPlanDao
import com.example.nanobot.core.database.entity.CustomSkillEntity
import com.example.nanobot.core.database.entity.BehaviorEventEntity
import com.example.nanobot.core.database.entity.MessageEntity
import com.example.nanobot.core.database.entity.MemoryFactEntity
import com.example.nanobot.core.database.entity.MemorySummaryEntity
import com.example.nanobot.core.database.entity.ReminderEntity
import com.example.nanobot.core.database.entity.SessionEntity
import com.example.nanobot.core.database.entity.TaskPlanEntity

@Database(
    entities = [SessionEntity::class, MessageEntity::class, MemoryFactEntity::class, MemorySummaryEntity::class, ReminderEntity::class, CustomSkillEntity::class, TaskPlanEntity::class, BehaviorEventEntity::class],
    version = 13,
    exportSchema = false
)
abstract class NanobotDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun memorySummaryDao(): MemorySummaryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun customSkillDao(): CustomSkillDao
    abstract fun taskPlanDao(): TaskPlanDao
    abstract fun behaviorEventDao(): BehaviorEventDao
}
