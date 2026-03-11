package com.example.nanobot.domain.repository

import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeCurrentSession(): Flow<ChatSession?>
    fun observeSessions(): Flow<List<ChatSession>>
    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>
    suspend fun observeSessionsSnapshot(): List<ChatSession>
    suspend fun getOrCreateCurrentSession(): ChatSession
    suspend fun getSessionByTitle(title: String): ChatSession?
    suspend fun createSession(
        title: String = "New Chat",
        makeCurrent: Boolean = true,
        parentSessionId: String? = null,
        subagentDepth: Int = 0
    ): ChatSession
    suspend fun upsertSession(session: ChatSession, makeCurrent: Boolean = false): ChatSession
    suspend fun selectSession(sessionId: String)
    suspend fun deleteSession(sessionId: String)
    suspend fun deleteSessionsOlderThan(cutoffMillis: Long)
    suspend fun getMessages(sessionId: String): List<ChatMessage>
    suspend fun getHistoryForModel(sessionId: String, maxMessages: Int = 500): List<ChatMessage>
    suspend fun saveMessage(message: ChatMessage)
    suspend fun touchSession(session: ChatSession, makeCurrent: Boolean = true)
}
