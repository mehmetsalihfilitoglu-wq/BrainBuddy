package com.brainbuddy.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for test_snapshots table.
 * Stores test result for replay and history.
 */
@Entity(tableName = "test_snapshots")
data class TestSnapshotEntity(
    @PrimaryKey
    val testId: String,
    val createdAt: Long,
    val score: Int,
    val total: Int,
    val subjectBreakdownJson: String? = null,
    val questionIdsJson: String,
    val userAnswersJson: String,
    val wrongQuestionIdsJson: String? = null
)
