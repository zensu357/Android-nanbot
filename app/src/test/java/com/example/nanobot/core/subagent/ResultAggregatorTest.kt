package com.example.nanobot.core.subagent

import com.example.nanobot.core.model.SubagentResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultAggregatorTest {
    private val aggregator = ResultAggregator()

    @Test
    fun mergeAllSortsByPriorityAndCountsFailures() {
        val specs = listOf(
            SubtaskSpec(task = "low", title = "Low", priority = 10),
            SubtaskSpec(task = "high", title = "High", priority = 90)
        )
        val results = listOf(
            SubagentResult(parentSessionId = "parent", subagentDepth = 1, summary = "low summary"),
            SubagentResult.failed("parent", 1, "High", "boom")
        )

        val aggregated = aggregator.aggregate(specs, results, AggregationStrategy.MERGE_ALL)

        assertTrue(aggregated.success)
        assertEquals(1, aggregated.failedCount)
        assertTrue(aggregated.mergedSummary.indexOf("### High [failed]") < aggregated.mergedSummary.indexOf("### Low [completed]"))
    }

    @Test
    fun bestOfNPrefersLongestSuccessfulSummary() {
        val specs = listOf(
            SubtaskSpec(task = "short"),
            SubtaskSpec(task = "long")
        )
        val results = listOf(
            SubagentResult(parentSessionId = "parent", subagentDepth = 1, summary = "short"),
            SubagentResult(parentSessionId = "parent", subagentDepth = 1, summary = "this is the longest successful summary")
        )

        val aggregated = aggregator.aggregate(specs, results, AggregationStrategy.BEST_OF_N)

        assertTrue(aggregated.success)
        assertEquals("this is the longest successful summary", aggregated.mergedSummary)
    }

    @Test
    fun voteConsensusFailsWhenAllSubtasksFail() {
        val specs = listOf(SubtaskSpec(task = "one"), SubtaskSpec(task = "two"))
        val results = listOf(
            SubagentResult.failed("parent", 1, "one", "boom"),
            SubagentResult.timeout("parent", 1, "two")
        )

        val aggregated = aggregator.aggregate(specs, results, AggregationStrategy.VOTE_CONSENSUS)

        assertFalse(aggregated.success)
        assertEquals(2, aggregated.failedCount)
        assertEquals("Insufficient results for consensus.", aggregated.mergedSummary)
    }
}
