package com.example.nanobot.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentRunContextTest {
    @Test
    fun canParallelAllowsPositiveCountsWithinLimitWhenDelegationIsAvailable() {
        val context = AgentRunContext.root(
            sessionId = "session-1",
            maxSubagentDepth = 3,
            maxParallelSubagents = 4
        )

        assertTrue(context.canParallel(1))
        assertTrue(context.canParallel(4))
    }

    @Test
    fun canParallelRejectsZeroAndCountsAboveLimit() {
        val context = AgentRunContext.root(
            sessionId = "session-1",
            maxSubagentDepth = 3,
            maxParallelSubagents = 4
        )

        assertFalse(context.canParallel(0))
        assertFalse(context.canParallel(5))
    }

    @Test
    fun canParallelRejectsWhenDepthLimitIsReached() {
        val context = AgentRunContext(
            sessionId = "session-1",
            subagentDepth = 3,
            maxSubagentDepth = 3,
            maxParallelSubagents = 4
        )

        assertFalse(context.canParallel(1))
        assertFalse(context.canParallel(4))
    }
}
