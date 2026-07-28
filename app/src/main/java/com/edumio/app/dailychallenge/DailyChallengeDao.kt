package com.edumio.app.dailychallenge

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyChallengeDao {

    // ── Challenge ───────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChallenge(c: DailyChallengeEntity)

    /** The single challenge for this account+day (exam-agnostic). Returns it regardless of examProfile. */
    @Query("SELECT * FROM daily_challenge WHERE userId = :userId AND localDate = :localDate LIMIT 1")
    suspend fun getChallengeForDay(userId: String, localDate: String): DailyChallengeEntity?

    /** Must always be 0 or 1 — proves "exactly one Daily Challenge per account per day". */
    @Query("SELECT COUNT(*) FROM daily_challenge WHERE userId = :userId AND localDate = :localDate")
    suspend fun countChallengesForDay(userId: String, localDate: String): Int

    /** Total challenge rows for an account across all days (used by tests / diagnostics). */
    @Query("SELECT COUNT(*) FROM daily_challenge WHERE userId = :userId")
    suspend fun countChallengesForUser(userId: String): Int

    // ── Full-account export (sync snapshot + anonymous→account linking) ───────────────────────────
    @Query("SELECT * FROM daily_challenge WHERE userId = :userId")
    suspend fun getAllChallengesForUser(userId: String): List<DailyChallengeEntity>

    @Query("SELECT * FROM challenge_answer WHERE userId = :userId")
    suspend fun getAllAnswersForUser(userId: String): List<ChallengeAnswerEntity>

    @Query("SELECT * FROM user_question_state WHERE userId = :userId")
    suspend fun getAllStatesForUser(userId: String): List<UserQuestionStateEntity>

    @Query("SELECT * FROM section_deficit WHERE userId = :userId")
    suspend fun getAllDeficitsForUser(userId: String): List<SectionDeficitEntity>

    // ── Answers ─────────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnswer(a: ChallengeAnswerEntity)

    @Query("SELECT * FROM challenge_answer WHERE challengeKey = :challengeKey")
    suspend fun getAnswers(challengeKey: String): List<ChallengeAnswerEntity>

    @Query("SELECT COUNT(*) FROM challenge_answer WHERE challengeKey = :challengeKey")
    suspend fun countAnswers(challengeKey: String): Int

    // ── Question state / retirement ─────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(s: UserQuestionStateEntity)

    @Query("SELECT * FROM user_question_state WHERE userId = :userId AND questionId = :questionId LIMIT 1")
    suspend fun getState(userId: String, questionId: String): UserQuestionStateEntity?

    /** All question ids this user has ever been served for an exam (retired from the unseen pool). */
    @Query("SELECT questionId FROM user_question_state WHERE userId = :userId AND examType = :examType")
    suspend fun getSeenQuestionIds(userId: String, examType: String): List<String>

    @Query("SELECT COUNT(*) FROM user_question_state WHERE userId = :userId AND examType = :examType")
    suspend fun countSeen(userId: String, examType: String): Int

    /** Review pools (questions the user should revisit). */
    @Query("SELECT * FROM user_question_state WHERE userId = :userId AND examType = :examType AND state IN (:states)")
    suspend fun getStatesByStates(userId: String, examType: String, states: List<String>): List<UserQuestionStateEntity>

    @Query("SELECT COUNT(*) FROM user_question_state WHERE userId = :userId AND examType = :examType AND state IN (:states)")
    suspend fun countStatesByStates(userId: String, examType: String, states: List<String>): Int

    /** Cross-exam variant for the wrong-question hub's "all exams" view. */
    @Query("SELECT * FROM user_question_state WHERE userId = :userId AND state IN (:states)")
    suspend fun getStatesByStatesAllExams(userId: String, states: List<String>): List<UserQuestionStateEntity>

    /**
     * ALL scheduled reviews that are DUE now, oldest first, for one exam.
     *
     * Deliberately NOT limited here. Injection can only use a review whose section has a matching slot
     * in today's five, and that is not knowable in SQL. Capping the query at two would let two
     * ineligible-but-older reviews (e.g. Geometry on a day with no Geometry slot) hide a third, eligible
     * one and inject nothing. The cap belongs where eligibility is evaluated — see
     * DailyChallengeSelection.injectDueReviews — so the caller walks this list oldest-first and stops
     * once it has actually placed its maximum.
     *
     * Bounded in practice by the number of questions a student has answered incorrectly in one exam.
     * Read-only — no schema change.
     */
    @Query(
        "SELECT * FROM user_question_state " +
            "WHERE userId = :userId AND examType = :examType " +
            "AND state = 'NEEDS_REVISION' AND nextReviewAt <= :nowMs " +
            "ORDER BY nextReviewAt ASC"
    )
    suspend fun getDueReviews(userId: String, examType: String, nowMs: Long): List<UserQuestionStateEntity>

    /** The user's most recent recorded answer for one question (their chosen option for the solution view). */
    @Query("SELECT * FROM challenge_answer WHERE userId = :userId AND questionId = :questionId ORDER BY answeredAt DESC LIMIT 1")
    suspend fun getLatestAnswerForQuestion(userId: String, questionId: String): ChallengeAnswerEntity?

    // ── Deficit ledgers ─────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeficit(d: SectionDeficitEntity)

    @Query("SELECT * FROM section_deficit WHERE userId = :userId AND examType = :examType")
    suspend fun getDeficits(userId: String, examType: String): List<SectionDeficitEntity>

    // ── Streak ──────────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreak(s: StreakEntity)

    @Query("SELECT * FROM streak WHERE userId = :userId LIMIT 1")
    suspend fun getStreak(userId: String): StreakEntity?
}
