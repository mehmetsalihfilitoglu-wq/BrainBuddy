package com.mioacademy.app.db

import androidx.room.Entity

/**
 * Room entity for question_history table.
 * Spaced repetition: userId + questionId, lastResult, correctCount, wrongCount.
 * Multi-account: her şey userId bazlı.
 */
@Entity(
    tableName = "question_history",
    primaryKeys = ["userId", "questionId"]
)
data class QuestionHistoryEntity(
    val userId: String,
    val questionId: String,
    /** 1=CORRECT, 0=WRONG */
    val lastResult: Int,
    val lastAnsweredAt: Long,
    val correctCount: Int = 0,
    val wrongCount: Int = 0
) {
    companion object {
        const val RESULT_CORRECT = 1
        const val RESULT_WRONG = 0
    }
}
