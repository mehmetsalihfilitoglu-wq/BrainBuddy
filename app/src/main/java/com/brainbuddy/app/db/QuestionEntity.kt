package com.brainbuddy.app.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for questions table.
 * difficulty: 0=EASY, 1=MEDIUM, 2=HARD
 * grade: 1..7 (1 = Junior)
 */
@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["grade"], name = "index_questions_grade"),
        Index(value = ["subject"], name = "index_questions_subject"),
        Index(value = ["difficulty"], name = "index_questions_difficulty"),
        Index(value = ["isActive"], name = "index_questions_isActive"),
        Index(value = ["grade", "subject"], name = "index_questions_grade_subject"),
        Index(value = ["grade", "subject", "difficulty"], name = "index_questions_grade_subject_difficulty"),
        Index(value = ["grade", "subject", "difficulty", "isActive"], name = "index_questions_grade_subject_difficulty_active"),
        Index(value = ["stemHash"], name = "index_questions_stem_hash"),
        Index(value = ["qualityTier"], name = "index_questions_qualityTier")
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
    val questionType: String = "UNKNOWN",
    val skillsJson: String = "[]",
    val deactivationReason: String? = null,
    val version: Int = 1,
    val examType: String? = null,
    val imageAsset: String? = null,
    /** Diversity type (per subject) e.g. PROBLEM, PARAGRAPH, MAP. */
    val type: String = "UNKNOWN",
    /** Diversity skill/sub-topic, single string label. */
    val skill: String = "UNKNOWN",
    /** Normalized stem text (whitespace collapsed, trimmed, lowercased) for hashing. */
    val stemNormalized: String = "",
    /** SHA-256 hash of stemNormalized for duplicate detection. */
    val stemHash: String = "",
    val createdAt: Long = 0,
    /** Source pack identifier (e.g. grade6_mat) for traceability. */
    val sourcePack: String? = null,
    /** Source medium: "pdf", "json", "api", etc. */
    val source: String? = null,
    /** Reference within source: e.g. "doc.pdf p.42". */
    val sourceRef: String? = null,
    val publisher: String? = null,
    val year: Int? = null,
    val topic: String? = null,
    /** LGS quality score 0..100. Higher = better quality. */
    val qualityScore: Int = 0,
    /** True if question resembles new-generation style (reasoning, visuals, inference). */
    val isNewGenerationLike: Boolean = false,
    /**
     * Content quality tier from [com.brainbuddy.app.quiz.QuestionQualityClassifier] (not quiz UI difficulty).
     * EASY = shallow/trivial; MEDIUM/HARD = playable pool by default.
     */
    val qualityTier: String = "MEDIUM",
    /** 0..100 — inference, multi-step, traps, context. */
    val reasoningScore: Int = 0,
    /** 0..100 — plausibility and balance of wrong options. */
    val distractorQualityScore: Int = 0,
    /** 0..100 — stem length, structure, non-trivial context. */
    val contextComplexityScore: Int = 0,
    /** JSON array of string flags e.g. ["weak_distractors","single_step_mat"]. */
    val qualityFlagsJson: String = "[]",
    /** If set, question is excluded from playable pool without requiring isActive=0. */
    val unservableReason: String? = null,
)
