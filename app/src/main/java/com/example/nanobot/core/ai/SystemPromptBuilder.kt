package com.example.nanobot.core.ai

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.domain.repository.SkillRepository
import com.example.nanobot.core.tools.ToolAccessPolicy
import javax.inject.Inject

class SystemPromptBuilder @Inject constructor(
    private val promptPresetCatalog: PromptPresetCatalog,
    private val skillRepository: SkillRepository,
    private val toolAccessPolicy: ToolAccessPolicy,
    private val skillSelector: SkillSelector,
    private val skillPromptAssembler: SkillPromptAssembler,
    private val contextBudgetPlanner: ContextBudgetPlanner
) {
    suspend fun build(config: AgentConfig, memoryContext: String?, latestUserInput: String = ""): String {
        return buildWithDiagnostics(config, memoryContext, latestUserInput).prompt
    }

    suspend fun buildWithDiagnostics(
        config: AgentConfig,
        memoryContext: String?,
        latestUserInput: String = ""
    ): SystemPromptBuildResult {
        val preset = promptPresetCatalog.getById(config.presetId)
        val enabledSkills = skillRepository.getEnabledSkills(config)
        val skillPlan = skillSelector.select(enabledSkills, latestUserInput)
        val sections = contextBudgetPlanner.apply(
            config = config,
            sections = listOf(
                PlannedPromptSection(
                    section = PromptSection(
                        title = "Identity / Role",
                        body = listOf(
                            preset.identityOverride ?: preset.systemPrompt,
                            "Preset: ${preset.title}",
                            preset.description
                        )
                    ),
                    category = PromptSectionCategory.CORE,
                    priority = 100
                ),
                PlannedPromptSection(
                    section = PromptSection(
                        title = "Operating Rules",
                        body = buildList {
                            add("- Do not pretend a tool has already been executed when it has not.")
                            add("- Before changing, editing, or executing anything, first check the available context.")
                            add("- If you are uncertain, explicitly say so instead of inventing confidence.")
                            add("- If a tool can improve correctness, prefer calling the tool over fabricating its result.")
                            add("- When web searches or fetches already provide enough evidence to answer, stop using more web tools and synthesize the result.")
                            add("- Avoid repeating the same web search query or refetching the same page unless the user explicitly asks for deeper verification.")
                            add("- Keep replies direct, concise, and executable.")
                            preset.operatingRules.forEach { add("- $it") }
                        }
                    ),
                    category = PromptSectionCategory.CORE,
                    priority = 95
                ),
                PlannedPromptSection(
                    section = PromptSection(
                        title = "Preset Instructions",
                        body = buildList {
                            add(preset.systemPrompt.trim())
                            preset.behaviorNotes.forEach { add("- $it") }
                        }
                    ),
                    category = PromptSectionCategory.CORE,
                    priority = 90
                ),
                PlannedPromptSection(
                    section = skillPromptAssembler.buildCatalogSection(skillPlan),
                    category = PromptSectionCategory.SKILL_CATALOG,
                    priority = 80
                ),
                PlannedPromptSection(
                    section = skillPromptAssembler.buildExpandedSection(skillPlan, latestUserInput),
                    category = PromptSectionCategory.SKILL_EXPANDED,
                    priority = 85
                ),
                PlannedPromptSection(
                    section = PromptSection(
                        title = "Custom User Instructions",
                        body = listOf(config.systemPrompt.trim())
                    ),
                    category = PromptSectionCategory.CUSTOM,
                    priority = 75
                ),
                PlannedPromptSection(
                    section = PromptSection(
                        title = "Memory Context",
                        body = memoryContext?.lines().orEmpty().filter { it.isNotBlank() }
                    ),
                    category = PromptSectionCategory.MEMORY,
                    priority = 70
                ),
                PlannedPromptSection(
                    section = PromptSection(
                        title = "Available Capabilities Summary",
                        body = buildList {
                            add("- Can operate as an Android-native assistant with persistent sessions.")
                            add("- Can use registered app tools when tool usage is enabled.")
                            add("- ${toolAccessPolicy.describe(config)}")
                            add("- Local orchestration tools may delegate focused subtasks into isolated child runs when available.")
                            add("- Dynamic MCP tools may appear when enabled MCP servers have cached discoveries and current policy allows them.")
                            add("- Image attachments may be available to the model only when the selected provider path supports image attachments.")
                            add("- Can use stored memory summaries and facts when memory is enabled.")
                            add("- Can continue prior conversations within the current session.")
                            preset.capabilityHints.forEach { add("- $it") }
                        }
                    ),
                    category = PromptSectionCategory.CAPABILITIES,
                    priority = 60
                )
            )
        )

        val prompt = SystemPromptContent(sections).render()
        return SystemPromptBuildResult(
            prompt = prompt,
            sectionTitles = sections.map { it.title },
            catalogSkillIds = skillPlan.catalogSkills.map { it.id },
            expandedSkillIds = skillPlan.expandedSkills.map { it.id }
        )
    }
}
