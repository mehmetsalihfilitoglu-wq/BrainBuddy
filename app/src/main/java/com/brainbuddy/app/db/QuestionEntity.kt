package com.brainbuddy.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for questions table.
 * Single source of truth - JSON only seeds on first install.
 */
@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey
    val id: String,
    val subject: String,
    val difficulty: Int,
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
