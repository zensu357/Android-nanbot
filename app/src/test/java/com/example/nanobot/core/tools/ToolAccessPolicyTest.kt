package com.example.nanobot.core.tools

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class ToolAccessPolicyTest {
    private val validator = ToolValidator()
    private val accessPolicy = ToolAccessPolicy()
    private val registry = ToolRegistry(validator, accessPolicy).apply {
        register(FakeTool("delegate_task", ToolAccessCategory.LOCAL_ORCHESTRATION))
        register(FakeTool("device_time", ToolAccessCategory.LOCAL_READ_ONLY))
        register(FakeTool("session_snapshot", ToolAccessCategory.LOCAL_READ_ONLY))
        register(FakeTool("memory_lookup", ToolAccessCategory.LOCAL_READ_ONLY))
        register(FakeTool("list_workspace", ToolAccessCategory.WORKSPACE_READ_ONLY))
        register(FakeTool("read_file", ToolAccessCategory.WORKSPACE_READ_ONLY))
        register(FakeTool("search_workspace", ToolAccessCategory.WORKSPACE_READ_ONLY))
        register(FakeTool("web_fetch", ToolAccessCategory.EXTERNAL_READ_ONLY))
        register(FakeTool("web_search", ToolAccessCategory.EXTERNAL_READ_ONLY))
        register(FakeTool("mcp.github.issue_search", ToolAccessCategory.EXTERNAL_READ_ONLY))
        register(FakeTool("notify_user", ToolAccessCategory.LOCAL_SIDE_EFFECT))
        register(FakeTool("schedule_reminder", ToolAccessCategory.LOCAL_SIDE_EFFECT))
        register(FakeTool("read_current_ui", ToolAccessCategory.LOCAL_READ_ONLY, ToolExposure.HIDDEN_UNLOCKABLE))
    }

    @Test
    fun restrictedModeFiltersVisibleTools() = runTest {
        val config = AgentConfig(enableTools = true, restrictToWorkspace = true)

        val visibleNames = registry.visibleTools(config).map { it.name }

        assertEquals(
            listOf(
                "delegate_task",
                "device_time",
                "list_workspace",
                "memory_lookup",
                "read_file",
                "search_workspace",
                "session_snapshot"
            ),
            visibleNames
        )
    }

    @Test
    fun restrictedModeRejectsBlockedExecution() = runTest {
        val config = AgentConfig(enableTools = true, restrictToWorkspace = true)

        val result = registry.execute("web_fetch", buildJsonObject { }, config)

        assertTrue(result.contains("workspace-restricted mode policy"))
    }

    @Test
    fun restrictedModeRejectsMcpToolExecution() = runTest {
        val config = AgentConfig(enableTools = true, restrictToWorkspace = true)

        val result = registry.execute("mcp.github.issue_search", buildJsonObject { }, config)

        assertTrue(result.contains("workspace-restricted mode policy"))
    }

    @Test
    fun restrictedModeAllowsLocalOrchestrationExecution() = runTest {
        val config = AgentConfig(enableTools = true, restrictToWorkspace = true)

        val result = registry.execute("delegate_task", buildJsonObject { }, config)

        assertEquals("executed:delegate_task", result)
    }

    @Test
    fun unrestrictedModeShowsExternalReadOnlyTools() = runTest {
        val config = AgentConfig(enableTools = true, restrictToWorkspace = false)

        val visibleNames = registry.visibleTools(config).map { it.name }

        assertTrue("delegate_task" in visibleNames)
        assertTrue("web_fetch" in visibleNames)
        assertTrue("web_search" in visibleNames)
    }

    @Test
    fun activeSkillAllowedToolsFurtherRestrictVisibleAndExecutableTools() = runTest {
        val config = AgentConfig(enableTools = true, restrictToWorkspace = false)
        val runContext = AgentRunContext.root("session-1").copy(
            allowedToolNames = setOf("read_file", "list_workspace")
        )

        val visibleNames = registry.visibleTools(config, runContext).map { it.name }
        val blockedResult = registry.execute("web_fetch", buildJsonObject { }, config, runContext)
        val allowedResult = registry.execute("read_file", buildJsonObject { }, config, runContext)

        assertEquals(listOf("list_workspace", "read_file"), visibleNames)
        assertTrue(blockedResult.contains("currently activated skill policy"))
        assertEquals("executed:read_file", allowedResult)
    }

    @Test
    fun hiddenUnlockableToolsStayInvisibleUntilUnlocked() = runTest {
        val config = AgentConfig(enableTools = true, restrictToWorkspace = false)

        val visibleNames = registry.visibleTools(config).map { it.name }
        val blockedResult = registry.execute("read_current_ui", buildJsonObject { }, config)

        assertTrue("read_current_ui" !in visibleNames)
        assertTrue(blockedResult.contains("hidden until unlocked"))
    }

    @Test
    fun hiddenUnlockableToolsBecomeVisibleWhenRunContextUnlocksThem() = runTest {
        val config = AgentConfig(enableTools = true, restrictToWorkspace = false)
        val runContext = AgentRunContext.root("session-1").copy(
            unlockedToolNames = setOf("read_current_ui")
        )

        val visibleNames = registry.visibleTools(config, runContext).map { it.name }
        val result = registry.execute("read_current_ui", buildJsonObject { }, config, runContext)

        assertTrue("read_current_ui" in visibleNames)
        assertEquals("executed:read_current_ui", result)
    }

    private class FakeTool(
        override val name: String,
        override val accessCategory: ToolAccessCategory,
        override val exposure: ToolExposure = ToolExposure.DEFAULT_VISIBLE
    ) : AgentTool {
        override val description: String = name
        override val parametersSchema: JsonObject = buildJsonObject { }

        override suspend fun execute(arguments: JsonObject, config: AgentConfig, runContext: AgentRunContext): String {
            return "executed:$name"
        }
    }
}
