package com.example.nanobot.core.taskplan

import com.example.nanobot.core.database.dao.TaskPlanDao
import com.example.nanobot.data.mapper.toEntity
import com.example.nanobot.data.mapper.toModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskStateStore @Inject constructor(
    private val taskPlanDao: TaskPlanDao
) {
    suspend fun save(plan: TaskPlan) {
        taskPlanDao.upsert(plan.toEntity())
    }

    suspend fun load(planId: String): TaskPlan? {
        return taskPlanDao.getById(planId)?.toModel()
    }

    suspend fun loadBySession(sessionId: String): List<TaskPlan> {
        return taskPlanDao.getBySession(sessionId).map { it.toModel() }
    }

    suspend fun activeForSession(sessionId: String): TaskPlan? {
        return taskPlanDao.getActiveBySession(sessionId)?.toModel()
    }
}
