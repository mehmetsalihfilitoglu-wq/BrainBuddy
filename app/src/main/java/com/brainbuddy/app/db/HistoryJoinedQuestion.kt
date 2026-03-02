package com.brainbuddy.app.db

/**
 * Result of history JOIN question for adaptive picker.
 * Room maps columns from raw query to constructor params.
 */
data class HistoryJoinedQuestion(
    val questionId: String,
    val wrongTotal: Int,
    val correctTotal: Int,
    val streakCorrect: Int,
    val lastAnsweredAt: Long,
    val dueAt: Long,
    val seenCount: Int,
    val lastSeenTestIndex: Int,
    val lastWasWrong: Boolean,
    val subject: String,
    val text: String,
    val optionsJson: String,
    val correctIndex: Int,
    val levelGroup: String?,
    val gradeTag: String?,
    val hint: String?
)
