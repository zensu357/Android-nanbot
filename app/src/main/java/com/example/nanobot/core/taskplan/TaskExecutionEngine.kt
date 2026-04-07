package com.example.nanobot.core.taskplan

import com.example.nanobot.core.ai.AgentTurnRunner
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.subagent.AggregationStrategy
import com.example.nanobot.core.subagent.AgentRole
import com.example.nanobot.core.subagent.ParallelDispatcher
import com.example.nanobot.core.subagent.SubtaskSpec
import com.example.nanobot.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope

@Singleton
class TaskExecutionEngine @Inject constructor(
    private val agentTurnRunnerProvider: Provider<AgentTurnRunner>,
    private val parallelDispatcher: ParallelDispatcher,
    private val taskStateStore: TaskStateStore,
    private val sessionRepository: SessionRepository
) {
    private val _progress = MutableStateFlow<TaskProgress?>(null)
    val progress: StateFlow<TaskProgress?> = _progress.asStateFlow()

    suspend fun execute(
        plan: TaskPlan,
        config: AgentConfig,
        runContext: AgentRunContext,
        onStepComplete: suspend (TaskStep) -> Unit = {}
    ): TaskPlan {
        if (plan.status == TaskPlanStatus.CANCELLED || plan.status == TaskPlanStatus.COMPLETED) {
            return plan
        }

        var currentPlan = plan.copy(status = TaskPlanStatus.IN_PROGRESS, updatedAt = System.currentTimeMillis())
        taskStateStore.save(currentPlan)
        val steps = currentPlan.steps.toMutableList()
        val stepBudget = executionStepBudget(config)
        var processedSteps = 0

        for (index in steps.indices) {
            if (processedSteps >= stepBudget) {
                val suspendedPlan = currentPlan.copy(
                    steps = steps.toList(),
                    status = TaskPlanStatus.IN_PROGRESS,
                    updatedAt = System.currentTimeMillis()
                )
                taskStateStore.save(suspendedPlan)
                _progress.value = null
                return suspendedPlan
            }

            val step = steps[index]
            if (step.status == StepStatus.COMPLETED || step.status == StepStatus.SKIPPED) {
                continue
            }

            val parallelGroup = collectParallelGroup(index, steps)
            if (parallelGroup.size > 1) {
                val remainingBudget = stepBudget - processedSteps
                val limitedGroup = parallelGroup.take(remainingBudget)
                val executedGroup = executeParallelGroup(limitedGroup, steps, config, runContext)
                executedGroup.forEach { (stepIndex, executedStep) ->
                    steps[stepIndex] = executedStep
                    onStepComplete(executedStep)
                }
                processedSteps += executedGroup.size
                currentPlan = currentPlan.copy(steps = steps.toList(), updatedAt = System.currentTimeMillis())
                taskStateStore.save(currentPlan)

                val failedStep = executedGroup.firstOrNull { (_, executedStep) -> executedStep.status == StepStatus.FAILED }
                if (failedStep != null) {
                    val failedPlan = currentPlan.copy(
                        status = TaskPlanStatus.FAILED,
                        updatedAt = System.currentTimeMillis()
                    )
                    taskStateStore.save(failedPlan)
                    _progress.value = null
                    return failedPlan
                }
                continue
            }

            if (!dependenciesMet(step, steps)) {
                steps[index] = step.copy(
                    status = StepStatus.SKIPPED,
                    errorMessage = "Dependencies not met.",
                    completedAt = System.currentTimeMillis()
                )
                currentPlan = currentPlan.copy(steps = steps.toList(), updatedAt = System.currentTimeMillis())
                taskStateStore.save(currentPlan)
                processedSteps += 1
                continue
            }

            _progress.value = TaskProgress(
                planId = currentPlan.id,
                currentStepIndex = index,
                totalSteps = steps.size,
                currentStepDescription = step.description
            )
            val executed = executeStep(step, steps, config, runContext)
            steps[index] = executed
            onStepComplete(executed)
            currentPlan = currentPlan.copy(steps = steps.toList(), updatedAt = System.currentTimeMillis())
            taskStateStore.save(currentPlan)
            processedSteps += 1

            if (executed.status == StepStatus.FAILED) {
                val failedPlan = currentPlan.copy(
                    status = TaskPlanStatus.FAILED,
                    updatedAt = System.currentTimeMillis()
                )
                taskStateStore.save(failedPlan)
                _progress.value = null
                return failedPlan
            }
        }

        val finalStatus = if (steps.all { it.status == StepStatus.COMPLETED || it.status == StepStatus.SKIPPED }) {
            TaskPlanStatus.COMPLETED
        } else {
            TaskPlanStatus.FAILED
        }
        currentPlan = currentPlan.copy(
            steps = steps.toList(),
            status = finalStatus,
            updatedAt = System.currentTimeMillis()
        )
        taskStateStore.save(currentPlan)
        _progress.value = null
        return currentPlan
    }

    private suspend fun executeStep(
        step: TaskStep,
        allSteps: List<TaskStep>,
        config: AgentConfig,
        runContext: AgentRunContext
    ): TaskStep {
        val startedAt = step.startedAt ?: System.currentTimeMillis()
        var retryCount = step.retryCount
        var previousError = step.errorMessage

        while (true) {
            val prompt = buildStepContext(step, allSteps, previousError)
            try {
                val resultText = if (step.delegatable && runContext.canDelegate()) {
                    val delegated = parallelDispatcher.dispatchAll(
                        subtasks = listOf(
                            SubtaskSpec(
                                task = prompt,
                                title = "Step ${step.index + 1}",
                                role = AgentRole.GENERAL
                            )
                        ),
                        config = config,
                        parentContext = runContext,
                        strategy = AggregationStrategy.BEST_OF_N
                    )
                    check(delegated.success) { delegated.mergedSummary }
                    delegated.mergedSummary
                } else {
                    val history = sessionRepository.getHistoryForModel(runContext.sessionId)
                    val result = agentTurnRunnerProvider.get().runTurn(
                        sessionId = runContext.sessionId,
                        history = history,
                        userInput = prompt,
                        attachments = emptyList<Attachment>(),
                        config = config,
                        runContext = runContext,
                        onProgress = {}
                    )
                    summarizeResult(result.newMessages, result.finalResponse?.content)
                }

                return step.copy(
                    status = StepStatus.COMPLETED,
                    result = resultText.take(1_000),
                    errorMessage = null,
                    retryCount = retryCount,
                    startedAt = startedAt,
                    completedAt = System.currentTimeMillis()
                )
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (throwable: Throwable) {
                previousError = throwable.message ?: "Step execution failed."
                if (retryCount >= step.maxRetries) {
                    return step.copy(
                        status = StepStatus.FAILED,
                        errorMessage = previousError,
                        retryCount = retryCount,
                        startedAt = startedAt,
                        completedAt = System.currentTimeMillis()
                    )
                }
                retryCount += 1
            }
        }
    }

    private fun buildStepContext(
        step: TaskStep,
        allSteps: List<TaskStep>,
        previousError: String?
    ): String {
        val priorResults = allSteps
            .filter { it.status == StepStatus.COMPLETED && it.index < step.index }
            .mapNotNull { completed -> completed.result?.take(200)?.takeIf { it.isNotBlank() }?.let { result -> completed to result } }
            .joinToString("\n") { (completed, result) ->
                "- Step ${completed.index + 1}: $result"
            }

        return buildString {
            appendLine("## Current Task Step")
            appendLine(step.description)
            if (priorResults.isNotBlank()) {
                appendLine()
                appendLine("## Prior Step Results")
                appendLine(priorResults)
            }
            if (!previousError.isNullOrBlank()) {
                appendLine()
                appendLine("## Retry Guidance")
                appendLine("The previous attempt failed with: $previousError")
                appendLine("Adjust the execution strategy and complete the step.")
            }
        }.trimEnd()
    }

    private fun dependenciesMet(step: TaskStep, allSteps: List<TaskStep>): Boolean {
        return step.dependsOn.all { dependencyId ->
            allSteps.firstOrNull { it.id == dependencyId }?.status == StepStatus.COMPLETED
        }
    }

    private fun collectParallelGroup(startIndex: Int, steps: List<TaskStep>): List<Int> {
        val first = steps[startIndex]
        if (!first.delegatable || first.status != StepStatus.PENDING) return emptyList()
        if (!dependenciesMet(first, steps)) return emptyList()

        val dependencyKey = first.dependsOn.sorted()
        val group = mutableListOf<Int>()
        for (index in startIndex until steps.size) {
            val candidate = steps[index]
            if (candidate.status != StepStatus.PENDING) break
            if (!candidate.delegatable) break
            if (!dependenciesMet(candidate, steps)) break
            if (candidate.dependsOn.sorted() != dependencyKey) break
            group += index
        }
        return group
    }

    private suspend fun executeParallelGroup(
        indices: List<Int>,
        allSteps: List<TaskStep>,
        config: AgentConfig,
        runContext: AgentRunContext
    ): List<Pair<Int, TaskStep>> = supervisorScope {
        indices.map { index ->
            async {
                _progress.value = TaskProgress(
                    planId = runContext.sessionId,
                    currentStepIndex = index,
                    totalSteps = allSteps.size,
                    currentStepDescription = allSteps[index].description
                )
                index to executeStep(allSteps[index], allSteps, config, runContext)
            }
        }.map { it.await() }
    }

    private fun summarizeResult(messages: List<ChatMessage>, finalResponse: String?): String {
        finalResponse?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return messages
            .asReversed()
            .firstOrNull { it.role == MessageRole.ASSISTANT && !it.content.isNullOrBlank() }
            ?.content
            ?.trim()
            ?: "The step finished without producing a text result."
    }

    private fun executionStepBudget(config: AgentConfig): Int {
        return (config.maxToolIterations / 2).coerceIn(1, 3)
    }
}
