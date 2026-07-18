package com.edumio.app.dailychallenge

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-user learning state for a single question. Daily Challenge draws ONLY from NEVER_SEEN. */
enum class QuestionLearnState { NEVER_SEEN, SEEN_ONCE, CORRECT, INCORRECT_ONCE, INCORRECT_MULTIPLE, NEEDS_REVISION, FORGOTTEN, MASTERED }

/** Daily Challenge lifecycle. */
enum class ChallengeStatus { AVAILABLE, IN_PROGRESS, COMPLETED, EXPIRED }

/** Independent review queues (MASTERED/CORRECT are not review-eligible). */
enum class ReviewQueue { INCORRECT, NEEDS_REVISION, FORGOTTEN }

/**
 * EXACTLY ONE Daily Challenge per (user, local calendar day) — NOT per exam. Once a row exists for a
 * day it is immutable and is ALWAYS returned; switching exams, reopening, or process death never
 * generate a second one. [examProfile] records which exam profile the 5 questions were drawn from at
 * generation time, but it is deliberately NOT part of the primary key, so the challenge is returned
 * regardless of the currently-active exam.
 *
 * Persisted contract (matches the product spec): userId, localDate, challengeId, examProfile,
 * questionIdsCsv (= orderedQuestionIds), currentIndex, answers (see [ChallengeAnswerEntity]),
 * completedAt, createdAt.
 */
@Entity(tableName = "daily_challenge", primaryKeys = ["userId", "localDate"])
data class DailyChallengeEntity(
    val userId: String,
    val localDate: String, // YYYY-MM-DD in the user's timezone
    /** Stable, deterministic id for this challenge: "userId:localDate". Never changes once generated. */
    val challengeId: String,
    /** Exam profile the 5 questions were generated from — recorded, but NOT part of the key. */
    val examProfile: String,
    val status: String,
    /** The 5 retired question ids, in fixed order. Immutable once generated (orderedQuestionIds). */
    val questionIdsCsv: String,
    /** Resume cursor: number of questions answered so far (kept == count of persisted answers). */
    val currentIndex: Int = 0,
    val allocationCsv: String, // section:n|section:n for audit
    val createdAt: Long = 0,
    val startedAt: Long = 0,
    val completedAt: Long = 0,
    val expiresAt: Long = 0,
    val score: Int = 0,
    val shortage: String = "", // records any section shortage encountered at generation
)

/** One row per answered Daily-Challenge question. challengeKey = userId:localDate (exam-agnostic). */
@Entity(tableName = "challenge_answer", primaryKeys = ["challengeKey", "questionId"])
data class ChallengeAnswerEntity(
    val challengeKey: String,
    val questionId: String,
    val userId: String,
    val chosenIndex: Int,
    val isCorrect: Boolean,
    val timeMs: Long,
    val answeredAt: Long,
)

/** Per-user per-question learning state. Absence of a row == NEVER_SEEN. */
@Entity(
    tableName = "user_question_state",
    primaryKeys = ["userId", "questionId"],
    indices = [Index(value = ["userId", "state"]), Index(value = ["userId", "examType", "section"])],
)
data class UserQuestionStateEntity(
    val userId: String,
    val questionId: String,
    val examType: String,
    val section: String,
    val topic: String,
    val state: String,
    val timesSeen: Int = 0,
    val timesCorrect: Int = 0,
    val timesIncorrect: Int = 0,
    val consecutiveCorrect: Int = 0, // spaced-repetition streak toward MASTERED
    val firstSeenAt: Long = 0,
    val lastSeenAt: Long = 0,
    val nextReviewAt: Long = 0,
    val masteredAt: Long = 0,
)

/** Persisted cumulative-deficit ledger per (user, exam, section) — survives restarts so convergence holds. */
@Entity(tableName = "section_deficit", primaryKeys = ["userId", "examType", "section"])
data class SectionDeficitEntity(
    val userId: String,
    val examType: String,
    val section: String, // top-level section, or "parent/sub" for sub-section ledgers
    val expectedToDate: Double = 0.0,
    val actualToDate: Double = 0.0,
)

/** Per-user streak. */
@Entity(tableName = "streak")
data class StreakEntity(
    @PrimaryKey val userId: String,
    val current: Int = 0,
    val longest: Int = 0,
    val lastCompletedLocalDate: String = "",
    val milestonesCsv: String = "",
)
