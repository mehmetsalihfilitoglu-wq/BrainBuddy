package com.brainbuddy.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for question_history table.
 * Spaced repetition: dueAt, lastWasWrong, streakCorrect.
 */
@Entity(tableName = "question_history")
data class QuestionHistoryEntity(
    @PrimaryKey
    val questionId: String,
    val wrongTotal: Int,
    val correctTotal: Int,
    val streakCorrect: Int,
    val lastAnsweredAt: Long,
    val dueAt: Long,
    val seenCount: Int,
    val lastSeenTestIndex: Int,
    val lastWasWrong: Boolean
)
