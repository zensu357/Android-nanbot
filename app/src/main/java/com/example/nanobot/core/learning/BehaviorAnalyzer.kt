package com.example.nanobot.core.learning

import com.example.nanobot.core.database.dao.BehaviorEventDao
import com.example.nanobot.core.database.entity.BehaviorEventEntity
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BehaviorAnalyzer @Inject constructor(
    private val eventDao: BehaviorEventDao
) {
    suspend fun analyze(): UserBehaviorProfile? {
        val cutoff = System.currentTimeMillis() - ANALYSIS_WINDOW_MS
        val events = eventDao.getEventsSince(cutoff)
        if (events.size < MIN_EVENTS_FOR_ANALYSIS) return null

        return UserBehaviorProfile(
            toolPreferences = analyzeToolPreferences(events),
            commonTaskPatterns = analyzeTaskPatterns(events),
            preferredComplexity = analyzeComplexityPreference(events),
            feedbackTrends = analyzeFeedbackTrends(events),
            activeHours = analyzeActiveHours(events),
            analyzedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteOlderThan(before: Long) {
        eventDao.deleteOlderThan(before)
    }

    private fun analyzeToolPreferences(events: List<BehaviorEventEntity>): List<ToolPreference> {
        return events
            .filter { it.type == EventType.TOOL_USAGE.name }
            .groupBy { it.key }
            .map { (toolName, toolEvents) ->
                val successCount = toolEvents.count { it.metadata.contains("\"success\":true") }
                ToolPreference(
                    toolName = toolName,
                    usageCount = toolEvents.size,
                    successRate = successCount.toDouble() / toolEvents.size,
                    avgDurationMs = extractAvgDuration(toolEvents)
                )
            }
            .sortedByDescending { it.usageCount }
    }

    private fun analyzeTaskPatterns(events: List<BehaviorEventEntity>): List<TaskPattern> {
        val completions = events.filter { it.type == EventType.TASK_COMPLETION.name }
        val sequences = completions.mapNotNull { parseToolSequence(it.metadata) }

        return sequences
            .groupBy { it.take(3) }
            .filter { it.value.size >= 2 }
            .map { (prefix, group) ->
                TaskPattern(
                    toolSequence = prefix,
                    frequency = group.size,
                    avgSuccessRate = group.count { sequence -> sequence.lastOrNull() != "FAILED" }.toDouble() / group.size
                )
            }
            .sortedByDescending { it.frequency }
            .take(10)
    }

    private fun analyzeComplexityPreference(events: List<BehaviorEventEntity>): ComplexityPreference {
        val feedback = events.filter { it.type == EventType.FEEDBACK.name }
        val positiveDetailed = feedback.count {
            it.metadata.contains("POSITIVE") && it.metadata.contains("response_style\":\"detailed\"")
        }
        val positiveConcise = feedback.count {
            it.metadata.contains("POSITIVE") && it.metadata.contains("response_style\":\"concise\"")
        }

        return when {
            positiveDetailed > 0 && (positiveConcise == 0 || positiveDetailed > positiveConcise * 1.5) -> {
                ComplexityPreference.DETAILED
            }

            positiveConcise > 0 && (positiveDetailed == 0 || positiveConcise > positiveDetailed * 1.5) -> {
                ComplexityPreference.CONCISE
            }

            else -> ComplexityPreference.BALANCED
        }
    }

    private fun analyzeFeedbackTrends(events: List<BehaviorEventEntity>): FeedbackTrend {
        val feedbackEvents = events.filter { it.type == EventType.FEEDBACK.name }
        val positive = feedbackEvents.count { it.metadata.contains("POSITIVE") }
        val corrections = feedbackEvents.count { it.metadata.contains("IMPLICIT_CORRECTION") }

        return FeedbackTrend(
            positiveRate = if (feedbackEvents.isNotEmpty()) positive.toDouble() / feedbackEvents.size else 0.0,
            correctionRate = if (feedbackEvents.isNotEmpty()) corrections.toDouble() / feedbackEvents.size else 0.0,
            totalFeedbacks = feedbackEvents.size
        )
    }

    private fun analyzeActiveHours(events: List<BehaviorEventEntity>): List<Int> {
        return events
            .map { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).hour }
            .groupBy { it }
            .entries
            .sortedByDescending { it.value.size }
            .take(5)
            .map { it.key }
    }

    private fun extractAvgDuration(events: List<BehaviorEventEntity>): Long {
        val durations = events.mapNotNull {
            Regex("\\\"duration_ms\\\":(\\d+)").find(it.metadata)?.groupValues?.get(1)?.toLongOrNull()
        }
        return if (durations.isNotEmpty()) durations.average().toLong() else 0L
    }

    private fun parseToolSequence(metadata: String): List<String>? {
        val match = Regex("\\\"tool_sequence\\\":\\[([^\\]]+)]").find(metadata) ?: return null
        return match.groupValues[1]
            .split(',')
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
            .ifEmpty { null }
    }

    companion object {
        const val ANALYSIS_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        const val MIN_EVENTS_FOR_ANALYSIS = 20
    }
}
