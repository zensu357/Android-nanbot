package com.example.nanobot.core.taskplan

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.nanobot.core.database.NanobotDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskStateStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database: NanobotDatabase = Room.inMemoryDatabaseBuilder(
        context,
        NanobotDatabase::class.java
    ).allowMainThreadQueries().build()
    private val store = TaskStateStore(database.taskPlanDao())

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun savesAndLoadsTaskPlanRoundtrip() = runTest {
        database.sessionDao().upsert(
            com.example.nanobot.core.database.entity.SessionEntity(
                id = "session-1",
                title = "Session",
                parentSessionId = null,
                subagentDepth = 0,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        val plan = TaskPlan(
            sessionId = "session-1",
            title = "Plan",
            originalGoal = "Goal",
            steps = listOf(
                TaskStep(index = 0, description = "First"),
                TaskStep(index = 1, description = "Second", dependsOn = listOf("dep"))
            )
        )

        store.save(plan)
        val restored = store.load(plan.id)

        assertNotNull(restored)
        assertEquals(plan.id, restored.id)
        assertEquals(2, restored.steps.size)
        assertEquals("Second", restored.steps[1].description)
    }

    @Test
    fun returnsActivePlanForSession() = runTest {
        database.sessionDao().upsert(
            com.example.nanobot.core.database.entity.SessionEntity(
                id = "session-2",
                title = "Session",
                parentSessionId = null,
                subagentDepth = 0,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        val completed = TaskPlan(
            sessionId = "session-2",
            title = "Done",
            originalGoal = "Done",
            steps = emptyList(),
            status = TaskPlanStatus.COMPLETED
        )
        val active = TaskPlan(
            sessionId = "session-2",
            title = "Active",
            originalGoal = "Goal",
            steps = listOf(TaskStep(index = 0, description = "First")),
            status = TaskPlanStatus.IN_PROGRESS
        )

        store.save(completed)
        store.save(active)

        val restored = store.activeForSession("session-2")

        assertNotNull(restored)
        assertEquals(active.id, restored.id)
    }
}
