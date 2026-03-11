package com.example.nanobot.data.repository

import com.example.nanobot.core.database.dao.MessageDao
import com.example.nanobot.core.database.dao.MemoryFactDao
import com.example.nanobot.core.database.dao.MemorySummaryDao
import com.example.nanobot.core.database.dao.SessionDao
import com.example.nanobot.core.database.NanobotDatabase
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.preferences.SessionSelectionStore
import com.example.nanobot.data.mapper.toEntity
import com.example.nanobot.data.mapper.toModel
import com.example.nanobot.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val database: NanobotDatabase,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val memoryFactDao: MemoryFactDao,
    private val memorySummaryDao: MemorySummaryDao,
    private val sessionSelectionStore: SessionSelectionStore
) : SessionRepository {
    override fun observeCurrentSession(): Flow<ChatSession?> = combine(
        sessionDao.observeSessions(),
        sessionSelectionStore.selectedSessionId
    ) { sessions, selectedId ->
        val mapped = sessions.map { it.toModel() }
        when {
            mapped.isEmpty() -> null
            selectedId.isNullOrBlank() -> mapped.first()
            else -> mapped.firstOrNull { it.id == selectedId } ?: mapped.first()
        }
    }

    override fun observeSessions(): Flow<List<ChatSession>> = sessionDao.observeSessions()
        .map { sessions -> sessions.map { it.toModel() } }

    override suspend fun observeSessionsSnapshot(): List<ChatSession> =
        sessionDao.observeSessions().first().map { it.toModel() }

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        messageDao.observeMessages(sessionId).map { messages -> messages.map { it.toModel() } }

    override suspend fun getOrCreateCurrentSession(): ChatSession {
        val selectedId = sessionSelectionStore.selectedSessionId.first()
        val existing = when {
            !selectedId.isNullOrBlank() -> sessionDao.getSessionById(selectedId)?.toModel()
            else -> sessionDao.getLatestSession()?.toModel()
        }
        if (existing != null) {
            return existing
        }

        val session = ChatSession(title = "New Chat")
        sessionDao.upsert(session.toEntity())
        sessionSelectionStore.setSelectedSessionId(session.id)
        return session
    }

    override suspend fun getSessionByTitle(title: String): ChatSession? {
        return sessionDao.getSessionByTitle(title)?.toModel()
    }

    override suspend fun createSession(
        title: String,
        makeCurrent: Boolean,
        parentSessionId: String?,
        subagentDepth: Int
    ): ChatSession {
        val session = ChatSession(
            title = title.ifBlank { "New Chat" },
            parentSessionId = parentSessionId,
            subagentDepth = subagentDepth
        )
        sessionDao.upsert(session.toEntity())
        if (makeCurrent) {
            sessionSelectionStore.setSelectedSessionId(session.id)
        }
        return session
    }

    override suspend fun upsertSession(session: ChatSession, makeCurrent: Boolean): ChatSession {
        sessionDao.upsert(session.toEntity())
        if (makeCurrent) {
            sessionSelectionStore.setSelectedSessionId(session.id)
        }
        return session
    }

    override suspend fun selectSession(sessionId: String) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionSelectionStore.setSelectedSessionId(session.id)
    }

    override suspend fun deleteSession(sessionId: String) {
        val sessions = sessionDao.observeSessions().first()
        val deleteIds = collectDeleteIds(rootSessionId = sessionId, sessions = sessions)
        deleteSessions(deleteIds = deleteIds, allSessions = sessions)
    }

    override suspend fun deleteSessionsOlderThan(cutoffMillis: Long) {
        val sessions = sessionDao.observeSessions().first()
        val rootIds = sessions
            .filter { it.updatedAt < cutoffMillis }
            .map { it.id }
            .toSet()
        if (rootIds.isEmpty()) return

        val deleteIds = collectDeleteIds(rootSessionIds = rootIds, sessions = sessions)
        deleteSessions(deleteIds = deleteIds, allSessions = sessions)
    }

    override suspend fun getMessages(sessionId: String): List<ChatMessage> {
        return messageDao.getMessages(sessionId).map { it.toModel() }
    }

    override suspend fun getHistoryForModel(sessionId: String, maxMessages: Int): List<ChatMessage> {
        val messages = getMessages(sessionId)
        val sliced = messages.takeLast(maxMessages)
        val firstUserIndex = sliced.indexOfFirst { it.role == com.example.nanobot.core.model.MessageRole.USER }
        return if (firstUserIndex == -1) {
            sliced
        } else {
            sliced.drop(firstUserIndex)
        }
    }

    override suspend fun saveMessage(message: ChatMessage) {
        messageDao.insert(message.toEntity())
    }

    override suspend fun touchSession(session: ChatSession, makeCurrent: Boolean) {
        upsertSession(
            session.copy(updatedAt = System.currentTimeMillis()),
            makeCurrent = makeCurrent
        )
    }

    private suspend fun deleteSessions(
        deleteIds: Set<String>,
        allSessions: List<com.example.nanobot.core.database.entity.SessionEntity>
    ) {
        if (deleteIds.isEmpty()) return

        val selectedId = sessionSelectionStore.selectedSessionId.first()
        val fallbackSessionId = allSessions
            .firstOrNull { it.id !in deleteIds }
            ?.id

        database.withTransaction {
            deleteIds.forEach { sessionId ->
                memoryFactDao.deleteBySessionId(sessionId)
                memorySummaryDao.deleteBySessionId(sessionId)
                sessionDao.deleteById(sessionId)
            }
        }

        if (selectedId in deleteIds) {
            sessionSelectionStore.setSelectedSessionId(fallbackSessionId)
        }
    }

    private fun collectDeleteIds(
        rootSessionId: String,
        sessions: List<com.example.nanobot.core.database.entity.SessionEntity>
    ): Set<String> = collectDeleteIds(setOf(rootSessionId), sessions)

    private fun collectDeleteIds(
        rootSessionIds: Set<String>,
        sessions: List<com.example.nanobot.core.database.entity.SessionEntity>
    ): Set<String> {
        if (rootSessionIds.isEmpty()) return emptySet()

        val childrenByParentId = sessions
            .filter { !it.parentSessionId.isNullOrBlank() }
            .groupBy { it.parentSessionId }

        val pendingIds = ArrayDeque(rootSessionIds)
        val deleteIds = linkedSetOf<String>()
        while (pendingIds.isNotEmpty()) {
            val sessionId = pendingIds.removeFirst()
            if (!deleteIds.add(sessionId)) continue
            childrenByParentId[sessionId].orEmpty().forEach { child ->
                pendingIds.addLast(child.id)
            }
        }
        return deleteIds
    }
}
