package com.example.nanobot.core.ai

import com.example.nanobot.core.database.dao.BehaviorEventDao
import com.example.nanobot.core.database.entity.BehaviorEventEntity

internal class FakeBehaviorEventDao(
    initialEvents: List<BehaviorEventEntity> = emptyList()
) : BehaviorEventDao {
    private val events = initialEvents.toMutableList()

    override suspend fun insert(event: BehaviorEventEntity) {
        events += event.copy(id = (events.size + 1).toLong())
    }

    override suspend fun getEventsSince(since: Long): List<BehaviorEventEntity> {
        return events.filter { it.timestamp > since }.sortedByDescending { it.timestamp }
    }

    override suspend fun countSince(since: Long): Int {
        return events.count { it.timestamp > since }
    }

    override suspend fun deleteOlderThan(before: Long) {
        events.removeAll { it.timestamp < before }
    }

    fun allEvents(): List<BehaviorEventEntity> = events.toList()
}
