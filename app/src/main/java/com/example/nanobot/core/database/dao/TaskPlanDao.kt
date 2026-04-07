package com.example.nanobot.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.nanobot.core.database.entity.TaskPlanEntity

@Dao
interface TaskPlanDao {
    @Upsert
    suspend fun upsert(entity: TaskPlanEntity)

    @Query("SELECT * FROM task_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskPlanEntity?

    @Query("SELECT * FROM task_plans WHERE session_id = :sessionId ORDER BY created_at DESC")
    suspend fun getBySession(sessionId: String): List<TaskPlanEntity>

    @Query("SELECT * FROM task_plans WHERE session_id = :sessionId AND status IN ('PENDING', 'IN_PROGRESS') ORDER BY updated_at DESC LIMIT 1")
    suspend fun getActiveBySession(sessionId: String): TaskPlanEntity?
}
