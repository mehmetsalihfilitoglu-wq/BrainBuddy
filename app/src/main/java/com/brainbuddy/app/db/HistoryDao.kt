package com.brainbuddy.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {

    @Query("SELECT * FROM question_history WHERE userId = :userId AND questionId = :questionId LIMIT 1")
    suspend fun get(userId: String, questionId: String): QuestionHistoryEntity?

    @Query("SELECT * FROM question_history WHERE userId = :userId AND questionId IN (:ids)")
    suspend fun getByIds(userId: String, ids: List<String>): List<QuestionHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: QuestionHistoryEntity)

    /**
     * Due wrong: lastResult=WRONG and lastAnsweredAt <= nowMinus3Days.
     * Returns questionIds for given subject, ordered by lastAnsweredAt ASC.
     */
    @Query("""
        SELECT h.questionId FROM question_history h
        INNER JOIN questions q ON q.id = h.questionId AND q.isActive = 1
        WHERE h.userId = :userId
          AND q.subject = :subject
          AND h.lastResult = 0
          AND h.lastAnsweredAt <= :nowMinus3Days
        ORDER BY h.lastAnsweredAt ASC
        LIMIT :limit
    """)
    suspend fun getDueWrongQuestionIds(
        userId: String,
        subject: String,
        nowMinus3Days: Long,
        limit: Int
    ): List<String>

    /**
     * Recently correct: rows where lastResult=CORRECT for cooldown filtering.
     * Returns full rows; caller filters by cooldownDays(correctCount) in Kotlin.
     */
    @Query("""
        SELECT h.* FROM question_history h
        INNER JOIN questions q ON q.id = h.questionId AND q.isActive = 1
        WHERE h.userId = :userId
          AND q.subject = :subject
          AND h.lastResult = 1
    """)
    suspend fun getRecentlyCorrectForSubject(
        userId: String,
        subject: String
    ): List<QuestionHistoryEntity>

    /**
     * New / not in history: questions for subject that have no history row for userId.
     * Exclude given IDs. Fetches with LIMIT, caller shuffles in memory (avoids ORDER BY RANDOM()).
     */
    @Query("""
        SELECT q.id FROM questions q
        LEFT JOIN question_history h ON h.userId = :userId AND h.questionId = q.id
        WHERE q.isActive = 1
          AND q.subject = :subject
          AND h.questionId IS NULL
          AND q.id NOT IN (:excludeIds)
        ORDER BY q.id
        LIMIT :limit
    """)
    suspend fun getNewQuestionIds(
        userId: String,
        subject: String,
        excludeIds: List<String>,
        limit: Int
    ): List<String>

    /**
     * Not recently correct: has history but lastResult=WRONG OR lastAnsweredAt old enough.
     * Used when we need to exclude only "recently correct" (cooldown).
     * Caller passes excludeIds = recentlyCorrectIds (filtered by cooldown in Kotlin).
     * Fetches with LIMIT, caller shuffles in memory (avoids ORDER BY RANDOM()).
     */
    @Query("""
        SELECT q.id FROM questions q
        INNER JOIN question_history h ON h.userId = :userId AND h.questionId = q.id
        WHERE q.isActive = 1
          AND q.subject = :subject
          AND q.id NOT IN (:excludeIds)
        ORDER BY q.id
        LIMIT :limit
    """)
    suspend fun getNotRecentlyCorrectQuestionIds(
        userId: String,
        subject: String,
        excludeIds: List<String>,
        limit: Int
    ): List<String>

    /** Count due wrong for parent panel. */
    @Query("""
        SELECT COUNT(*) FROM question_history h
        INNER JOIN questions q ON q.id = h.questionId AND q.isActive = 1
        WHERE h.userId = :userId
          AND h.lastResult = 0
          AND h.lastAnsweredAt <= :nowMinus3Days
    """)
    suspend fun countDueWrong(userId: String, nowMinus3Days: Long): Int

    @Query("SELECT questionId FROM question_history WHERE userId = :userId AND lastResult = 0 AND lastAnsweredAt >= :cutoffMs")
    suspend fun getWrongIds(userId: String, cutoffMs: Long): List<String>

    @Query("SELECT questionId FROM question_history WHERE userId = :userId AND wrongCount > 0")
    suspend fun getAllWrongIds(userId: String): List<String>
}
