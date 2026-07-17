package com.edumio.app.quiz

/**
 * Per-topic progress snapshot.
 *
 * @param topic Topic name (e.g. "Basınç", "Fractions").
 * @param correct Total correct answers for this topic.
 * @param wrong Total wrong answers for this topic.
 * @param total Total attempts for this topic.
 * @param progress correct / total in [0f, 1f].
 */
data class TopicProgress(
    val topic: String,
    val correct: Int,
    val wrong: Int,
    val total: Int,
    val progress: Float
)

enum class TopicProgressState {
    COMPLETED,
    IMPROVING,
    WEAK
}

fun TopicProgress.state(): TopicProgressState =
    when {
        total <= 0 -> TopicProgressState.WEAK
        progress >= 0.8f -> TopicProgressState.COMPLETED
        progress >= 0.5f -> TopicProgressState.IMPROVING
        else -> TopicProgressState.WEAK
    }

