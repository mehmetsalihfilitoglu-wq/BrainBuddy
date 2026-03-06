package com.brainbuddy.app.db

import android.content.Context
import com.brainbuddy.app.quiz.AnswerRecord
import com.brainbuddy.app.quiz.Question
import com.brainbuddy.app.db.QuestionMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Room-backed data store for quiz.
 * Single source of truth at runtime.
 */
class RoomQuizDataStore(private val context: Context) {
    private val db get() = DatabaseProvider.get(context)
    private val questionDao get() = db.questionDao()
    private val historyDao get() = db.historyDao()
    private val snapshotDao get() = db.snapshotDao()
    private val appMetaDao get() = db.appMetaDao()
    private val wrongAnswerDao get() = db.wrongAnswerDao()

    private val KEY_GLOBAL_TEST_INDEX = "global_test_index"
    private val KEY_DB_SEEDED = "db_seeded"
    private val RECENT_SEEN_LIMIT = 150

    fun getActiveQuestions(): List<Question> = runBlocking(Dispatchers.IO) {
        questionDao.getActiveQuestions().map { QuestionMapper.toQuestion(it) }
    }

    fun getQuestionsByIds(ids: List<String>): List<Question> = runBlocking(Dispatchers.IO) {
        if (ids.isEmpty()) return@runBlocking emptyList()
        questionDao.getQuestionsByIds(ids).map { QuestionMapper.toQuestion(it) }
    }

    /** Sınıf bazlı havuz (2-8). Tüm derslerden. */
    fun getQuestionsByGrade(grade: Int): List<Question> = runBlocking(Dispatchers.IO) {
        if (grade !in 2..8) return@runBlocking emptyList()
        questionDao.getByGrade(grade).map { QuestionMapper.toQuestion(it) }
    }

    /** Sınıf + ders bazlı havuz (tüm zorluklar). Relaxed difficulty fallback için. */
    fun getQuestionsByGradeSubject(grade: Int, subject: String): List<Question> = runBlocking(Dispatchers.IO) {
        if (grade !in 2..8) return@runBlocking emptyList()
        questionDao.getByGradeSubject(grade, subject).map { QuestionMapper.toQuestion(it) }
    }

    /** Sınıf + ders + zorluk bazlı havuz. difficulty: 0=EASY,1=MEDIUM,2=HARD */
    fun getQuestionsByGradeSubjectDifficulty(
        grade: Int,
        subject: String,
        difficulty: Int
    ): List<Question> = runBlocking(Dispatchers.IO) {
        if (grade !in 2..8) return@runBlocking emptyList()
        questionDao.getByGradeSubjectDifficulty(grade, subject, difficulty).map { QuestionMapper.toQuestion(it) }
    }

    /** Candidate pool for fast quiz picking (LIMIT 200, single difficulty). */
    fun getCandidatePoolByGradeSubjectDifficulty(
        grade: Int,
        subject: String,
        difficulty: Int
    ): List<QuestionCandidateRow> = runBlocking(Dispatchers.IO) {
        if (grade !in 2..8) return@runBlocking emptyList()
        questionDao.getCandidatePoolByGradeSubjectDifficulty(grade, subject, difficulty)
    }

    /** Candidate pool per subject (LIMIT 200, any difficulty). Call from Dispatchers.IO. */
    fun getCandidatePoolByGradeSubject(
        grade: Int,
        subject: String
    ): List<QuestionCandidateRow> = runBlocking(Dispatchers.IO) {
        if (grade !in 2..8) return@runBlocking emptyList()
        questionDao.getCandidatePoolByGradeSubject(grade, subject)
    }

    fun insertQuestions(entities: List<QuestionEntity>) = runBlocking(Dispatchers.IO) {
        questionDao.insertAll(entities)
    }

    /** @param userId profileId (multi-account: her şey userId bazlı) */
    fun recordAnswers(userId: String, answers: List<AnswerRecord>, testId: String?) = runBlocking(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        answers.forEach { a ->
            val h = historyDao.get(userId, a.questionId)
            val correctCount = (h?.correctCount ?: 0) + if (a.isCorrect) 1 else 0
            val wrongCount = (h?.wrongCount ?: 0) + if (a.isCorrect) 0 else 1
            val lastResult = if (a.isCorrect) QuestionHistoryEntity.RESULT_CORRECT else QuestionHistoryEntity.RESULT_WRONG
            historyDao.upsert(
                QuestionHistoryEntity(
                    userId = userId,
                    questionId = a.questionId,
                    lastResult = lastResult,
                    lastAnsweredAt = now,
                    correctCount = correctCount,
                    wrongCount = wrongCount
                )
            )
        }
    }

    fun onQuizCompleted(questionIds: List<String>) = runBlocking(Dispatchers.IO) {
        val newIndex = (appMetaDao.get(KEY_GLOBAL_TEST_INDEX)?.toIntOrNull() ?: 0) + 1
        appMetaDao.set(AppMetaEntity(KEY_GLOBAL_TEST_INDEX, newIndex.toString()))
    }

    fun getGlobalTestIndex(): Int = runBlocking(Dispatchers.IO) {
        appMetaDao.get(KEY_GLOBAL_TEST_INDEX)?.toIntOrNull() ?: 0
    }

    fun getRecentlySeenIdsForProfile(profileId: String, limit: Int = RECENT_SEEN_LIMIT): Set<String> = runBlocking(Dispatchers.IO) {
        val key = "recent_seen_$profileId"
        appMetaDao.get(key)?.let { json ->
            try {
                val arr = JSONArray(json)
                (0 until arr.length())
                    .map { arr.optString(it, "") }
                    .filter { it.isNotBlank() }
                    .take(limit)
                    .toSet()
            } catch (_: Exception) { emptySet() }
        } ?: emptySet()
    }

    fun getQuestionIdsFromLastNTests(profileId: String, n: Int): Set<String> = runBlocking(Dispatchers.IO) {
        val result = mutableSetOf<String>()
        if (n >= 1) {
            appMetaDao.get("recent_test_1_$profileId")?.let { json ->
                try {
                    JSONArray(json).let { arr -> for (i in 0 until arr.length()) result.add(arr.optString(i, "")) }
                } catch (_: Exception) { }
            }
        }
        if (n >= 2) {
            appMetaDao.get("recent_test_2_$profileId")?.let { json ->
                try {
                    JSONArray(json).let { arr -> for (i in 0 until arr.length()) result.add(arr.optString(i, "")) }
                } catch (_: Exception) { }
            }
        }
        result.filter { it.isNotBlank() }.toSet()
    }

    fun recordTestCreated(profileId: String, testId: String, questionIds: List<String>) = runBlocking(Dispatchers.IO) {
        val prev1 = appMetaDao.get("recent_test_1_$profileId") ?: "[]"
        appMetaDao.set(AppMetaEntity("recent_test_2_$profileId", prev1))
        appMetaDao.set(AppMetaEntity("recent_test_1_$profileId", JSONArray(questionIds).toString()))
    }

    fun recordSeenIdsForProfile(profileId: String, ids: List<String>) = runBlocking(Dispatchers.IO) {
        val key = "recent_seen_$profileId"
        val existing = appMetaDao.get(key)?.let { json ->
            try {
                val arr = JSONArray(json)
                (0 until arr.length())
                    .map { arr.optString(it, "") }
                    .filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()

        // New IDs should be treated as most recent. Keep unique order: new first, then older.
        val combined = (ids + existing).distinct().take(RECENT_SEEN_LIMIT)
        appMetaDao.set(AppMetaEntity(key, JSONArray(combined).toString()))
    }

    fun getWrongQuestionIds(userId: String, withinDays: Int = 7): Set<String> = runBlocking(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(withinDays.toLong())
        historyDao.getWrongIds(userId, cutoff).toSet()
    }

    fun getAllWrongIds(userId: String): Set<String> = runBlocking(Dispatchers.IO) {
        historyDao.getAllWrongIds(userId).toSet()
    }

    /** Bugün tekrar sorulacak yanlışlar sayısı (lastResult=WRONG, lastAnsweredAt <= now-3days) */
    fun getDueWrongCount(userId: String): Int = runBlocking(Dispatchers.IO) {
        val nowMinus3Days = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
        historyDao.countDueWrong(userId, nowMinus3Days)
    }

    fun isSeeded(): Boolean = runBlocking(Dispatchers.IO) {
        appMetaDao.get(KEY_DB_SEEDED) == "true"
    }

    fun ensureSeeded(): Boolean = runBlocking(Dispatchers.IO) {
        if (isSeeded()) return@runBlocking true
        DbSeeder.seedIfNeeded(context)
    }

    fun insertSnapshot(
        testId: String,
        score: Int,
        total: Int,
        questionIds: List<String>,
        userAnswers: Map<String, Int>,
        wrongIds: List<String>,
        subjectBreakdown: String? = null,
        profileId: String = "default"
    ) = runBlocking(Dispatchers.IO) {
        val answersArr = JSONArray()
        questionIds.forEach { id -> answersArr.put(userAnswers[id] ?: JSONObject.NULL) }
        snapshotDao.insert(
            TestSnapshotEntity(
                testId = testId,
                createdAt = System.currentTimeMillis(),
                score = score,
                total = total,
                profileId = profileId,
                subjectBreakdownJson = subjectBreakdown,
                questionIdsJson = JSONArray(questionIds).toString(),
                userAnswersJson = answersArr.toString(),
                wrongQuestionIdsJson = JSONArray(wrongIds).toString()
            )
        )
        if (wrongIds.isNotEmpty()) {
            wrongAnswerDao.insertAll(
                wrongIds.map { qId ->
                    WrongAnswerEntity(testId = testId, questionId = qId, isUnlocked = false, unlockedAt = null)
                }
            )
        }
    }

    fun getLastSnapshots(profileId: String = "default", limit: Int = 20): List<TestSnapshotEntity> = runBlocking(Dispatchers.IO) {
        snapshotDao.getLastSnapshots(profileId, limit)
    }

    fun getSnapshot(testId: String): TestSnapshotEntity? = runBlocking(Dispatchers.IO) {
        snapshotDao.getSnapshot(testId)
    }

    /** Count wrong answers in date range (for Reports filter). Same source as Test Detail. */
    fun getWrongCountInRange(sinceMillis: Long, profileId: String): Int = runBlocking(Dispatchers.IO) {
        wrongAnswerDao.countWrongInRange(sinceMillis, profileId)
    }

    /** List (testId, questionId) in date range for WrongAnswersReport screen. */
    fun getWrongInRange(sinceMillis: Long, profileId: String): List<WrongAnswerDao.WrongInRangeResult> = runBlocking(Dispatchers.IO) {
        wrongAnswerDao.getWrongInRange(sinceMillis, profileId)
    }
}
