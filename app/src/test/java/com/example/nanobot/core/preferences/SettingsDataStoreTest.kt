package com.example.nanobot.core.preferences

import androidx.test.core.app.ApplicationProvider
import com.example.nanobot.core.model.AgentConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsDataStoreTest {
    @Test
    fun savesAndReadsEnabledSkillIds() = runTest {
        val dataStore = SettingsDataStore(ApplicationProvider.getApplicationContext())
        val config = AgentConfig(enabledSkillIds = listOf("coding_editor", "planner_mode"))

        dataStore.save(config)

        val restored = dataStore.configFlow.first()
        assertEquals(listOf("coding_editor", "planner_mode"), restored.enabledSkillIds)
    }

    @Test
    fun savesAndReadsVisualMemoryFlag() = runTest {
        val dataStore = SettingsDataStore(ApplicationProvider.getApplicationContext())
        val config = AgentConfig(enableVisualMemory = true)

        dataStore.save(config)

        val restored = dataStore.configFlow.first()
        assertEquals(true, restored.enableVisualMemory)
    }

    @Test
    fun savesAndReadsSubagentLimits() = runTest {
        val dataStore = SettingsDataStore(ApplicationProvider.getApplicationContext())
        val config = AgentConfig(maxSubagentDepth = 5, maxParallelSubagents = 2)

        dataStore.save(config)

        val restored = dataStore.configFlow.first()
        assertEquals(5, restored.maxSubagentDepth)
        assertEquals(2, restored.maxParallelSubagents)
    }

    @Test
    fun savesAndReadsPhase3LearningFlags() = runTest {
        val dataStore = SettingsDataStore(ApplicationProvider.getApplicationContext())
        val config = AgentConfig(enableTaskPlanning = false, enableBehaviorLearning = false)

        dataStore.save(config)

        val restored = dataStore.configFlow.first()
        assertEquals(false, restored.enableTaskPlanning)
        assertEquals(false, restored.enableBehaviorLearning)
    }
}
