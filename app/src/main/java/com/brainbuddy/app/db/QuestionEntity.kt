package com.brainbuddy.app.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for questions table.
 * difficulty: 0=EASY, 1=MEDIUM, 2=HARD
 * grade: 2..8 (Junior ayrı tutulur)
 */
@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["grade"], name = "index_questions_grade"),
        Index(value = ["subject"], name = "index_questions_subject"),
        Index(value = ["difficulty"], name = "index_questions_difficulty"),
        Index(value = ["isActive"], name = "index_questions_isActive"),
        Index(value = ["normalizedStemHash"], name = "index_questions_normalized_stem_hash"),
        Index(value = ["grade", "subject"], name = "index_questions_grade_subject"),
        Index(value = ["grade", "subject", "difficulty"], name = "index_questions_grade_subject_difficulty")
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
    val questionType: String? = null,
    val skillsJson: String? = null,
    val deactivationReason: String? = null,
    val version: Int = 1,
    val examType: String? = null,
    val imageAsset: String? = null,
    /** Diversity type (per subject) e.g. PROBLEM, PARAGRAPH, MAP. */
    val type: String = "UNKNOWN",
    /** Diversity skill/sub-topic, single string label. */
    val skill: String = "UNKNOWN",
    /** Hash of normalized stem for duplicate detection at scale. */
    val normalizedStemHash: String = "",
    val createdAt: Long = 0,
    /** Source pack identifier (e.g. grade6_mat) for traceability. */
    val sourcePack: String? = null
)
