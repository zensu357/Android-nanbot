package com.example.nanobot.core.subagent

import com.example.nanobot.core.model.SubagentResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResultAggregator @Inject constructor() {
    fun aggregate(
        specs: List<SubtaskSpec>,
        results: List<SubagentResult>,
        strategy: AggregationStrategy
    ): ParallelResult {
        val paired = specs.zip(results)
        val failedCount = results.count { !it.success }

        return when (strategy) {
            AggregationStrategy.MERGE_ALL -> mergeAll(paired, failedCount)
            AggregationStrategy.BEST_OF_N -> bestOfN(paired, failedCount)
            AggregationStrategy.VOTE_CONSENSUS -> voteConsensus(paired, failedCount)
        }
    }

    private fun mergeAll(
        paired: List<Pair<SubtaskSpec, SubagentResult>>,
        failedCount: Int
    ): ParallelResult {
        val sections = paired
            .sortedByDescending { it.first.priority }
            .mapIndexed { index, (spec, result) ->
                val label = spec.title ?: "Subtask ${index + 1}"
                val status = if (result.success) "completed" else "failed"
                "### $label [$status]\n${result.summary}"
            }

        return ParallelResult(
            success = failedCount < paired.size,
            mergedSummary = sections.joinToString("\n\n"),
            individualResults = paired.map { it.second },
            failedCount = failedCount
        )
    }

    private fun bestOfN(
        paired: List<Pair<SubtaskSpec, SubagentResult>>,
        failedCount: Int
    ): ParallelResult {
        val best = paired
            .filter { it.second.success }
            .maxByOrNull { it.second.summary.length }

        return ParallelResult(
            success = best != null,
            mergedSummary = best?.second?.summary ?: "All subtasks failed.",
            individualResults = paired.map { it.second },
            failedCount = failedCount
        )
    }

    private fun voteConsensus(
        paired: List<Pair<SubtaskSpec, SubagentResult>>,
        failedCount: Int
    ): ParallelResult {
        val successful = paired.filter { it.second.success }
        val mergedSummary = if (successful.size >= 2) {
            buildString {
                appendLine("## Consensus Review (${successful.size} agents)")
                appendLine()
                append(
                    successful.mapIndexed { index, (spec, result) ->
                        "**Agent ${index + 1}** (${spec.role.name}):\n${result.summary}"
                    }.joinToString("\n\n---\n\n")
                )
            }.trimEnd()
        } else {
            successful.firstOrNull()?.second?.summary ?: "Insufficient results for consensus."
        }

        return ParallelResult(
            success = successful.isNotEmpty(),
            mergedSummary = mergedSummary,
            individualResults = paired.map { it.second },
            failedCount = failedCount
        )
    }
}
