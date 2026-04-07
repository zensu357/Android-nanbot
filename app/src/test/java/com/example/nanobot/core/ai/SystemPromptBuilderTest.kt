package com.example.nanobot.core.ai

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.learning.StrategyOptimizer
import com.example.nanobot.core.tools.ToolAccessPolicy
import com.example.nanobot.testutil.FakeSkillRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemPromptBuilderTest {
    @Test
    fun includesPresetAndCustomInstructions() {
        val builder = SystemPromptBuilder(
            PromptPresetCatalog(),
            FakeSkillRepository(),
            ToolAccessPolicy(),
            SkillSelector(),
            SkillPromptAssembler(),
            ContextBudgetPlanner(),
            StrategyOptimizer(com.example.nanobot.core.learning.BehaviorAnalyzer(FakeBehaviorEventDao()))
        )

        val prompt = kotlinx.coroutines.runBlocking {
            builder.build(
                AgentConfig(
                    presetId = "builder_mode",
                    systemPrompt = "Always explain the implementation plan before coding."
                ),
                memoryContext = null
            )
        }

        assertTrue(prompt.contains("Preset: Builder Mode"))
        assertTrue(prompt.contains("## Preset Instructions"))
        assertTrue(prompt.contains("## Custom User Instructions"))
        assertTrue(prompt.contains("Always explain the implementation plan before coding."))
        assertTrue(prompt.contains("stop using more web tools and synthesize the result"))
    }

    @Test
    fun trimsOversizedMemoryAndCustomSectionsWithinBudget() {
        val builder = SystemPromptBuilder(
            PromptPresetCatalog(),
            FakeSkillRepository(),
            ToolAccessPolicy(),
            SkillSelector(),
            SkillPromptAssembler(),
            ContextBudgetPlanner(),
            StrategyOptimizer(com.example.nanobot.core.learning.BehaviorAnalyzer(FakeBehaviorEventDao()))
        )

        val prompt = kotlinx.coroutines.runBlocking {
            builder.build(
                AgentConfig(
                    maxTokens = 256,
                    systemPrompt = "S".repeat(1200)
                ),
                memoryContext = "M".repeat(1400),
                latestUserInput = "Help me with this task."
            )
        }

        assertTrue(prompt.contains("## Custom User Instructions"))
        assertTrue(prompt.contains("## Memory Context"))
        assertTrue(prompt.contains("..."))
        assertFalse(prompt.length > 2600)
    }

    @Test
    fun includesVisualOperationProtocolForVisionCapablePhoneControlRuns() {
        val builder = SystemPromptBuilder(
            PromptPresetCatalog(),
            FakeSkillRepository(),
            ToolAccessPolicy(),
            SkillSelector(),
            SkillPromptAssembler(),
            ContextBudgetPlanner(),
            StrategyOptimizer(com.example.nanobot.core.learning.BehaviorAnalyzer(FakeBehaviorEventDao()))
        )

        val prompt = kotlinx.coroutines.runBlocking {
            builder.build(
                config = AgentConfig(),
                memoryContext = null,
                runContext = AgentRunContext.root(
                    sessionId = "session-1",
                    supportsVision = true,
                    unlockedToolNames = setOf("take_screenshot")
                )
            )
        }

        assertTrue(prompt.contains("## Visual Operation Protocol"))
        assertTrue(prompt.contains("When performing phone control tasks with visual capability:"))
    }

    @Test
    fun injectsLearnedPreferencesWhenBehaviorLearningIsEnabled() {
        val now = System.currentTimeMillis()
        val behaviorDao = FakeBehaviorEventDao(
            initialEvents = buildList {
                repeat(12) { index ->
                    add(
                        com.example.nanobot.core.database.entity.BehaviorEventEntity(
                            type = "TOOL_USAGE",
                            key = "write_file",
                            sessionId = "session-1",
                            metadata = "{\"success\":true,\"duration_ms\":${100 + index}}",
                            timestamp = now - index
                        )
                    )
                }
                repeat(4) { index ->
                    add(
                        com.example.nanobot.core.database.entity.BehaviorEventEntity(
                            type = "FEEDBACK",
                            key = "POSITIVE",
                            sessionId = "session-1",
                            metadata = "{\"signal\":\"POSITIVE\",\"response_style\":\"detailed\"}",
                            timestamp = now - 100 - index
                        )
                    )
                }
                repeat(4) { index ->
                    add(
                        com.example.nanobot.core.database.entity.BehaviorEventEntity(
                            type = "TASK_COMPLETION",
                            key = "refactor",
                            sessionId = "session-1",
                            metadata = "{\"tool_sequence\":[\"read_file\",\"write_file\"],\"success\":true}",
                            timestamp = now - 200 - index
                        )
                    )
                }
            }
        )
        val builder = SystemPromptBuilder(
            PromptPresetCatalog(),
            FakeSkillRepository(),
            ToolAccessPolicy(),
            SkillSelector(),
            SkillPromptAssembler(),
            ContextBudgetPlanner(),
            StrategyOptimizer(com.example.nanobot.core.learning.BehaviorAnalyzer(behaviorDao))
        )

        val prompt = kotlinx.coroutines.runBlocking {
            builder.build(
                AgentConfig(enableBehaviorLearning = true),
                memoryContext = null
            )
        }

        assertTrue(prompt.contains("## Learned Preferences"))
        assertTrue(prompt.contains("Frequently Used Tools"))
        assertTrue(prompt.contains("write_file"))
    }
}
