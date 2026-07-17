package com.edumio.app.core

/**
 * Per-topic or per-difficulty counts for stats display (X/Y format).
 */
data class TopicCounts(
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val total: Int
) {
    val accuracy: Float get() = if (total > 0) 100f * correct / total else 0f
}
