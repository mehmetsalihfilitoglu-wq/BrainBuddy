package com.mioacademy.app.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-question unlock state for wrong answers in Test Detail.
 * Links testId + questionId to isUnlocked / unlockedAt.
 */
@Entity(
    tableName = "wrong_answers",
    primaryKeys = ["testId", "questionId"],
    indices = [Index(value = ["testId"])]
)
data class WrongAnswerEntity(
    val testId: String,
    val questionId: String,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
)
