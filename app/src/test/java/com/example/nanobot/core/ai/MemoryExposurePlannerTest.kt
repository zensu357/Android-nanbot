package com.example.nanobot.core.ai

import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryExposurePlannerTest {
    @Test
    fun plannerKeepsSummaryAndSelectsRelevantFacts() = runTest {
        val planner = MemoryExposurePlanner(
            FakeMemoryRepository(
                facts = listOf(
                    MemoryFact(
                        id = "session-fact",
                        fact = "The current task is a Kotlin Android refactor.",
                        sourceSessionId = "session-1",
                        createdAt = 1L,
                        updatedAt = 100L,
                        confidence = 0.9f
                    ),
                    MemoryFact(
                        id = "long-term-fact",
                        fact = "The user prefers concise Kotlin answers.",
                        sourceSessionId = "session-2",
                        createdAt = 2L,
                        updatedAt = 90L,
                        confidence = 0.85f
                    ),
                    MemoryFact(
                        id = "irrelevant",
                        fact = "The user likes gardening.",
                        sourceSessionId = "session-3",
                        createdAt = 3L,
                        updatedAt = 80L,
                        confidence = 0.95f
                    )
                ),
                summary = MemorySummary(
                    sessionId = "session-1",
                    summary = "Current work focuses on Android Kotlin code cleanup.",
                    updatedAt = 200L,
                    sourceMessageCount = 8,
                    confidence = 0.88f
                )
            )
        )

        val context = planner.buildContext("session-1", "Please refactor this Kotlin Android screen.")

        assertNotNull(context)
        assertTrue(context.contains("Session summary:"))
        assertTrue(context.contains("Relevant current session facts:"))
        assertTrue(context.contains("Relevant long-term facts:"))
        assertTrue(context.contains("The current task is a Kotlin Android refactor."))
        assertTrue(context.contains("The user prefers concise Kotlin answers."))
        assertFalse(context.contains("gardening"))
    }

    private class FakeMemoryRepository(
        private val facts: List<MemoryFact>,
        private val summary: MemorySummary?
    ) : MemoryRepository {
        override fun observeFacts(): Flow<List<MemoryFact>> = flowOf(facts)
        override fun observeSummaries(): Flow<List<MemorySummary>> = flowOf(listOfNotNull(summary))
        override suspend fun getFacts(): List<MemoryFact> = facts
        override suspend fun getFactsForSession(sessionId: String): List<MemoryFact> = facts.filter { it.sourceSessionId == sessionId }
        override suspend fun getAllSummaries(): List<MemorySummary> = listOfNotNull(summary)
        override suspend fun getFactsForQuery(query: String): List<MemoryFact> = facts
        override suspend fun observeSummariesSnapshot(): List<MemorySummary> = listOfNotNull(summary)
        override suspend fun getSummaryForSession(sessionId: String): MemorySummary? = summary?.takeIf { it.sessionId == sessionId }
        override suspend fun deleteFact(factId: String) = Unit
        override suspend fun deleteSummary(sessionId: String) = Unit
        override suspend fun pruneFacts(maxFacts: Int) = Unit
        override suspend fun upsertFact(fact: MemoryFact) = Unit
        override suspend fun upsertSummary(summary: MemorySummary) = Unit
    }
}
