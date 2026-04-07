package com.example.nanobot.core.subagent

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.SubagentResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

@Singleton
class ParallelDispatcher @Inject constructor(
    private val subagentCoordinator: SubagentCoordinator,
    private val resultAggregator: ResultAggregator
) {
    suspend fun dispatchAll(
        subtasks: List<SubtaskSpec>,
        config: AgentConfig,
        parentContext: AgentRunContext,
        strategy: AggregationStrategy = AggregationStrategy.MERGE_ALL,
        timeoutMs: Long = DEFAULT_SUBTASK_TIMEOUT_MS
    ): ParallelResult {
        if (!parentContext.canDelegate()) {
            return ParallelResult.depthExceeded()
        }
        if (!parentContext.canParallel(subtasks.size)) {
            return ParallelResult.parallelLimitExceeded(parentContext.maxParallelSubagents)
        }

        val results = coroutineScope {
            subtasks.map { spec ->
                async {
                    try {
                        withTimeout(timeoutMs) {
                            subagentCoordinator.delegate(
                                task = spec.task,
                                title = spec.title,
                                role = spec.role,
                                config = config,
                                runContext = parentContext
                            )
                        }
                    } catch (_: TimeoutCancellationException) {
                        SubagentResult.timeout(
                            parentSessionId = parentContext.sessionId,
                            subagentDepth = parentContext.subagentDepth + 1,
                            label = spec.title ?: spec.task.take(50)
                        )
                    } catch (cancellationException: CancellationException) {
                        throw cancellationException
                    } catch (exception: Exception) {
                        SubagentResult.failed(
                            parentSessionId = parentContext.sessionId,
                            subagentDepth = parentContext.subagentDepth + 1,
                            label = spec.title ?: spec.task.take(50),
                            reason = exception.message
                        )
                    }
                }
            }.awaitAll()
        }

        return resultAggregator.aggregate(subtasks, results, strategy)
    }

    private companion object {
        const val DEFAULT_SUBTASK_TIMEOUT_MS = 120_000L
    }
}

data class SubtaskSpec(
    val task: String,
    val title: String? = null,
    val role: AgentRole = AgentRole.GENERAL,
    val priority: Int = 50
)

enum class AgentRole(val systemPromptFragment: String) {
    GENERAL("You are a general-purpose assistant."),
    RESEARCHER("You are a research specialist. Focus on gathering and synthesizing information."),
    CODER("You are a coding specialist. Focus on writing, reviewing, and debugging code."),
    ANALYST("You are a data analyst. Focus on analyzing information and extracting practical insights."),
    REVIEWER("You are a quality reviewer. Focus on validating results, finding issues, and calling out risks.")
}

enum class AggregationStrategy {
    MERGE_ALL,
    BEST_OF_N,
    VOTE_CONSENSUS
}

data class ParallelResult(
    val success: Boolean,
    val mergedSummary: String,
    val individualResults: List<SubagentResult>,
    val failedCount: Int = 0
) {
    companion object {
        fun depthExceeded(): ParallelResult {
            return ParallelResult(
                success = false,
                mergedSummary = "Subagent delegation is blocked because the maximum subagent depth has been reached.",
                individualResults = emptyList()
            )
        }

        fun parallelLimitExceeded(maxParallelSubagents: Int): ParallelResult {
            return ParallelResult(
                success = false,
                mergedSummary = "Parallel delegation exceeds the current limit of $maxParallelSubagents subtasks.",
                individualResults = emptyList()
            )
        }
    }
}
