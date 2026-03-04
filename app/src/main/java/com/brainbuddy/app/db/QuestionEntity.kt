package com.brainbuddy.app.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for questions table.
 * Single source of truth - JSON only seeds on first install.
 * difficulty: 0=EASY, 1=MEDIUM, 2=HARD, 3=VERY_HARD
 * grade: 1=Junior, 2..8=sınıf
 */
@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["grade", "subject", "difficulty"])
    ]
)
data class QuestionEntity(
    @PrimaryKey
    val id: String,
    val subject: String,
    val difficulty: Int,
    /** Sınıf (1=Junior, 2..8). Havuz filtreleme ve index için. */
    val grade: Int = 0,
    val text: String,
    val optionsJson: String,
    val correctIndex: Int,
    val tagsJson: String? = null,
    val isActive: Boolean = true,
    val version: Int = 1,
    val updatedAt: Long,
    val levelGroup: String? = null,
    val gradeTag: String? = null,
    val hint: String? = null,
    val imageAsset: String? = null,
    val examType: String? = null
)
