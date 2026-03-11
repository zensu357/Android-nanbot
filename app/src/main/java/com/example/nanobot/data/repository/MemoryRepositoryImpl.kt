package com.example.nanobot.data.repository

import com.example.nanobot.core.database.dao.MemoryFactDao
import com.example.nanobot.core.database.dao.MemorySummaryDao
import com.example.nanobot.core.memory.MemorySearchScorer
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.data.mapper.toEntity
import com.example.nanobot.data.mapper.toModel
import com.example.nanobot.domain.repository.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class MemoryRepositoryImpl @Inject constructor(
    private val memoryFactDao: MemoryFactDao,
    private val memorySummaryDao: MemorySummaryDao
) : MemoryRepository {
    override fun observeFacts(): Flow<List<MemoryFact>> =
        memoryFactDao.observeFacts().map { facts -> facts.map { it.toModel() } }

    override fun observeSummaries(): Flow<List<MemorySummary>> =
        memorySummaryDao.observeSummaries().map { summaries -> summaries.map { it.toModel() } }

    override suspend fun getFacts(): List<MemoryFact> =
        memoryFactDao.getFacts().map { it.toModel() }

    override suspend fun getFactsForSession(sessionId: String): List<MemoryFact> =
        memoryFactDao.getFactsForSession(sessionId).map { it.toModel() }

    override suspend fun getAllSummaries(): List<MemorySummary> =
        memorySummaryDao.observeSummaries().first().map { it.toModel() }

    override suspend fun getFactsForQuery(query: String): List<MemoryFact> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return emptyList()
        return getFacts()
            .map { fact ->
                fact to MemorySearchScorer.score(
                    query = normalized,
                    text = fact.fact,
                    updatedAt = fact.updatedAt,
                    confidence = fact.confidence,
                    sourceSessionId = fact.sourceSessionId
                )
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .map { (fact, _) -> fact }
    }

    override suspend fun observeSummariesSnapshot(): List<MemorySummary> =
        memorySummaryDao.observeSummaries().first().map { it.toModel() }

    override suspend fun getSummaryForSession(sessionId: String): MemorySummary? =
        memorySummaryDao.getSummaryForSession(sessionId)?.toModel()

    override suspend fun deleteFact(factId: String) {
        memoryFactDao.deleteById(factId)
    }

    override suspend fun deleteSummary(sessionId: String) {
        memorySummaryDao.deleteBySessionId(sessionId)
    }

    override suspend fun pruneFacts(maxFacts: Int) {
        if (maxFacts > 0) {
            memoryFactDao.pruneToLatest(maxFacts)
        }
    }

    override suspend fun upsertFact(fact: MemoryFact) {
        memoryFactDao.upsert(fact.toEntity())
    }

    override suspend fun upsertSummary(summary: MemorySummary) {
        memorySummaryDao.upsert(summary.toEntity())
    }
}
