package com.example.nanobot.core.tools

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.core.tools.impl.MemoryLookupTool
import com.example.nanobot.domain.repository.MemoryRepository
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MemoryLookupToolTest {
    @Test
    fun prefersCurrentSessionEntriesInLookupResults() = runTest {
        val repository = FakeMemoryRepository(
            facts = listOf(
                MemoryFact(
                    id = "fact-1",
                    fact = "The user prefers Kotlin for Android projects.",
                    sourceSessionId = "session-1",
                    createdAt = 1L,
                    updatedAt = 100L,
                    confidence = 0.92f
                ),
                MemoryFact(
                    id = "fact-2",
                    fact = "The user mentioned Kotlin in another session.",
                    sourceSessionId = "session-2",
                    createdAt = 1L,
                    updatedAt = 90L,
                    confidence = 0.58f
                )
            ),
            summaries = listOf(
                MemorySummary(
                    sessionId = "session-1",
                    summary = "Current session focuses on Kotlin Android work.",
                    updatedAt = 100L,
                    sourceMessageCount = 8,
                    confidence = 0.87f
                )
            )
        )
        val tool = MemoryLookupTool(repository)

        val result = tool.execute(
            arguments = buildJsonObject { put("query", "Kotlin Android") },
            config = AgentConfig(enableMemory = true),
            runContext = AgentRunContext.root(sessionId = "session-1", maxSubagentDepth = 1)
        )

        assertTrue(result.contains("Current session summaries"))
        assertTrue(result.contains("Current session facts"))
        assertTrue(result.contains("Other matching facts"))
        assertTrue(result.contains("confidence 87%"))
        assertTrue(result.contains("evidence: none"))
    }

    private class FakeMemoryRepository(
        private val facts: List<MemoryFact>,
        private val summaries: List<MemorySummary>
    ) : MemoryRepository {
        override fun observeFacts(): Flow<List<MemoryFact>> = flowOf(facts)

        override fun observeSummaries(): Flow<List<MemorySummary>> = flowOf(summaries)

        override suspend fun getFacts(): List<MemoryFact> = facts.sortedByDescending { it.updatedAt }

        override suspend fun getFactsForSession(sessionId: String): List<MemoryFact> = facts.filter { it.sourceSessionId == sessionId }

        override suspend fun getAllSummaries(): List<MemorySummary> = summaries

        override suspend fun getFactsForQuery(query: String): List<MemoryFact> = getFacts().filter { it.fact.contains("Kotlin", ignoreCase = true) }

        override suspend fun observeSummariesSnapshot(): List<MemorySummary> = summaries

        override suspend fun getSummaryForSession(sessionId: String): MemorySummary? = summaries.firstOrNull { it.sessionId == sessionId }

        override suspend fun deleteFact(factId: String) = Unit

        override suspend fun deleteSummary(sessionId: String) = Unit

        override suspend fun pruneFacts(maxFacts: Int) = Unit

        override suspend fun upsertFact(fact: MemoryFact) = Unit

        override suspend fun upsertSummary(summary: MemorySummary) = Unit
    }
}
