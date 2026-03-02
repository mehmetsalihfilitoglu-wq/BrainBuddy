package com.brainbuddy.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {

    @Query("SELECT * FROM question_history WHERE questionId = :questionId LIMIT 1")
    suspend fun get(questionId: String): QuestionHistoryEntity?

    @Query("SELECT * FROM question_history WHERE questionId IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<QuestionHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: QuestionHistoryEntity)

    /**
     * Due & wrong: history + question for spaced repetition (8 questions).
     */
    @Query("""
        SELECT h.questionId, h.wrongTotal, h.correctTotal, h.streakCorrect,
               h.lastAnsweredAt, h.dueAt, h.seenCount, h.lastSeenTestIndex, h.lastWasWrong,
               q.subject, q.text, q.optionsJson, q.correctIndex,
               q.levelGroup, q.gradeTag, q.hint
        FROM question_history h
        INNER JOIN questions q ON h.questionId = q.id
        WHERE h.dueAt <= :now AND h.lastWasWrong = 1 AND q.isActive = 1
        ORDER BY h.wrongTotal DESC, h.lastAnsweredAt ASC
    """)
    suspend fun getDueWrong(now: Long): List<HistoryJoinedQuestion>

    /**
     * Fresh candidate IDs: never seen OR (lastSeenTestIndex gap >= minTestGap) OR (lastAnsweredAt old).
     * Excludes dueWrong IDs. Picker fetches full questions via QuestionDao.getQuestionsByIds.
     */
    @Query("""
        SELECT q.id FROM questions q
        LEFT JOIN question_history h ON h.questionId = q.id
        WHERE q.isActive = 1
          AND q.id NOT IN (:excludeIds)
          AND (
            h.questionId IS NULL
            OR (:globalTestIndex - COALESCE(h.lastSeenTestIndex, 0) >= :minTestGap)
            OR (COALESCE(h.lastAnsweredAt, 0) <= :cutoffMs)
          )
    """)
    suspend fun getFreshCandidateIds(
        excludeIds: List<String>,
        globalTestIndex: Int,
        minTestGap: Int,
        cutoffMs: Long
    ): List<String>

    @Query("SELECT questionId FROM question_history WHERE lastWasWrong = 1 AND lastAnsweredAt >= :cutoffMs")
    suspend fun getWrongIds(cutoffMs: Long): List<String>

    @Query("SELECT questionId FROM question_history WHERE wrongTotal > 0")
    suspend fun getAllWrongIds(): List<String>
}
