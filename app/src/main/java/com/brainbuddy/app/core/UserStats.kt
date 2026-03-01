package com.brainbuddy.app.core

/**
 * Overall correct/wrong/blank/total counts.
 */
data class OverallCounts(val correct: Int, val wrong: Int, val blank: Int, val total: Int)

/**
 * Aggregated user stats with X/Y counts for display.
 */
data class UserStats(
    val overallCorrect: Int,
    val overallWrong: Int,
    val overallBlank: Int,
    val overallTotal: Int,
    val topicMasteryCounts: Map<String, TopicCounts>
) {
    val overallAccuracy: Float get() = if (overallTotal > 0) 100f * overallCorrect / overallTotal else 0f
}
