package com.brainbuddy.app.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for questions table.
 * difficulty: 1=MED, 2=HARD, 3=VERY_HARD
 * grade: 2..8 (Junior ayrı tutulur)
 */
@Entity(
    tableName = "questions",
    indices = [
        Index(
            value = ["grade", "subject"],
            name = "index_questions_grade_subject"
        )
    ]
)
data class QuestionEntity(
    @PrimaryKey
    val id: String,
    val grade: Int,
    val subject: String,
    val difficulty: Int,
    val questionText: String,
    val optionsJson: String,
    val answerIndex: Int,
    val explanation: String? = null,
    val isActive: Boolean = true,
    val version: Int = 1,
    val examType: String? = null,
    val imageAsset: String? = null
)
