package com.example.nanobot.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.nanobot.core.database.NanobotDatabase
import com.example.nanobot.core.database.entity.MemoryFactEntity
import com.example.nanobot.core.database.entity.MemorySummaryEntity
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.preferences.SessionSelectionStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionRepositoryImplTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database: NanobotDatabase = Room.inMemoryDatabaseBuilder(
        context,
        NanobotDatabase::class.java
    ).allowMainThreadQueries().build()
    private val selectionStore = SessionSelectionStore(context)
    private val repository = SessionRepositoryImpl(
        database = database,
        sessionDao = database.sessionDao(),
        messageDao = database.messageDao(),
        memoryFactDao = database.memoryFactDao(),
        memorySummaryDao = database.memorySummaryDao(),
        sessionSelectionStore = selectionStore
    )

    @AfterTest
    fun tearDown() = runTest {
        selectionStore.setSelectedSessionId(null)
        database.close()
    }

    @Test
    fun deleteSessionRemovesAssociatedDataAndFallsBackSelection() = runTest {
        val targetSession = repository.createSession(title = "Delete Me", makeCurrent = true)
        val fallbackSession = repository.createSession(title = "Keep Me", makeCurrent = false)
        repository.saveMessage(
            ChatMessage(
                sessionId = targetSession.id,
                role = MessageRole.USER,
                content = "Hello"
            )
        )
        database.memoryFactDao().upsert(
            MemoryFactEntity(
                id = "fact-1",
                fact = "Remember this",
                sourceSessionId = targetSession.id,
                createdAt = 1L,
                updatedAt = 2L
            )
        )
        database.memorySummaryDao().upsert(
            MemorySummaryEntity(
                sessionId = targetSession.id,
                summary = "Summary",
                updatedAt = 3L,
                sourceMessageCount = 1
            )
        )

        repository.deleteSession(targetSession.id)

        assertTrue(repository.observeSessionsSnapshot().none { it.id == targetSession.id })
        assertTrue(repository.getMessages(targetSession.id).isEmpty())
        assertTrue(database.memoryFactDao().getFactsForSession(targetSession.id).isEmpty())
        assertNull(database.memorySummaryDao().getSummaryForSession(targetSession.id))
        assertEquals(fallbackSession.id, repository.observeCurrentSession().first()?.id)
    }

    @Test
    fun deleteLastSessionClearsSelection() = runTest {
        val targetSession = repository.createSession(title = "Only Session", makeCurrent = true)

        repository.deleteSession(targetSession.id)

        assertTrue(repository.observeSessionsSnapshot().isEmpty())
        assertNull(repository.observeCurrentSession().first())
        assertNull(selectionStore.selectedSessionId.first())
    }

    @Test
    fun deleteSessionAlsoRemovesChildSessions() = runTest {
        val parentSession = repository.createSession(title = "Parent", makeCurrent = true)
        val childSession = repository.createSession(
            title = "Child",
            makeCurrent = false,
            parentSessionId = parentSession.id,
            subagentDepth = 1
        )

        repository.saveMessage(
            ChatMessage(
                sessionId = childSession.id,
                role = MessageRole.ASSISTANT,
                content = "Child message"
            )
        )

        repository.deleteSession(parentSession.id)

        assertTrue(repository.observeSessionsSnapshot().none { it.id == parentSession.id || it.id == childSession.id })
        assertTrue(repository.getMessages(childSession.id).isEmpty())
    }

    @Test
    fun deleteSessionsOlderThanRemovesOldParentsAndTheirChildren() = runTest {
        val now = System.currentTimeMillis()
        val freshSession = repository.upsertSession(
            ChatSession(
                id = "fresh-session",
                title = "Fresh",
                updatedAt = now
            ),
            makeCurrent = true
        )
        val oldParent = repository.upsertSession(
            ChatSession(
                id = "old-parent",
                title = "Old Parent",
                updatedAt = now - 10_000
            )
        )
        val oldChild = repository.upsertSession(
            ChatSession(
                id = "old-child",
                title = "Old Child",
                parentSessionId = oldParent.id,
                subagentDepth = 1,
                updatedAt = now
            )
        )

        repository.deleteSessionsOlderThan(now - 5_000)

        val remainingSessionIds = repository.observeSessionsSnapshot().map { it.id }.toSet()
        assertEquals(setOf(freshSession.id), remainingSessionIds)
        assertNull(database.sessionDao().getSessionById(oldParent.id))
        assertNull(database.sessionDao().getSessionById(oldChild.id))
        assertEquals(freshSession.id, repository.observeCurrentSession().first()?.id)
    }
}
