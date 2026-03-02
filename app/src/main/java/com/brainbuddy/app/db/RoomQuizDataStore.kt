package com.brainbuddy.app.db

import android.content.Context
import com.brainbuddy.app.quiz.AnswerRecord
import com.brainbuddy.app.quiz.Question
import com.brainbuddy.app.db.QuestionMapper
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

    private val KEY_GLOBAL_TEST_INDEX = "global_test_index"
    private val KEY_DB_SEEDED = "db_seeded"

    fun getActiveQuestions(): List<Question> = runBlocking {
        questionDao.getActiveQuestions().map { QuestionMapper.toQuestion(it) }
    }

    fun getQuestionsByIds(ids: List<String>): List<Question> = runBlocking {
        if (ids.isEmpty()) return@runBlocking emptyList()
        questionDao.getQuestionsByIds(ids).map { QuestionMapper.toQuestion(it) }
    }

    fun insertQuestions(entities: List<QuestionEntity>) = runBlocking {
        questionDao.insertAll(entities)
    }

    fun recordAnswers(answers: List<AnswerRecord>, testId: String?) = runBlocking {
        val now = System.currentTimeMillis()
        answers.forEach { a ->
            val h = historyDao.get(a.questionId)
            val wrongTotal = (h?.wrongTotal ?: 0) + if (a.isCorrect) 0 else 1
            val correctTotal = (h?.correctTotal ?: 0) + if (a.isCorrect) 1 else 0
            val streakCorrect = if (a.isCorrect) (h?.streakCorrect ?: 0) + 1 else 0
            val lastWasWrong = !a.isCorrect
            val dueAt = if (a.isCorrect) {
                val interval = when (streakCorrect) {
                    1 -> TimeUnit.HOURS.toMillis(12)
                    2 -> TimeUnit.DAYS.toMillis(2)
                    3 -> TimeUnit.DAYS.toMillis(7)
                    else -> TimeUnit.DAYS.toMillis(14)
                }
                now + interval
            } else {
                now
            }
            historyDao.upsert(
                QuestionHistoryEntity(
                    questionId = a.questionId,
                    wrongTotal = wrongTotal,
                    correctTotal = correctTotal,
                    streakCorrect = streakCorrect,
                    lastAnsweredAt = now,
                    dueAt = dueAt,
                    seenCount = (h?.seenCount ?: 0) + 1,
                    lastSeenTestIndex = h?.lastSeenTestIndex ?: 0,
                    lastWasWrong = lastWasWrong
                )
            )
        }
    }

    fun onQuizCompleted(questionIds: List<String>) = runBlocking {
        val newIndex = (appMetaDao.get(KEY_GLOBAL_TEST_INDEX)?.toIntOrNull() ?: 0) + 1
        appMetaDao.set(AppMetaEntity(KEY_GLOBAL_TEST_INDEX, newIndex.toString()))
        questionIds.forEach { id ->
            val h = historyDao.get(id) ?: return@forEach
            historyDao.upsert(h.copy(lastSeenTestIndex = newIndex))
        }
    }

    fun getGlobalTestIndex(): Int = runBlocking {
        appMetaDao.get(KEY_GLOBAL_TEST_INDEX)?.toIntOrNull() ?: 0
    }

    fun getRecentlySeenIdsForProfile(profileId: String, limit: Int = 100): Set<String> = runBlocking {
        val key = "recent_seen_$profileId"
        appMetaDao.get(key)?.let { json ->
            try {
                (0 until JSONArray(json).length()).map { JSONArray(json).optString(it, "") }
                    .filter { it.isNotBlank() }.take(limit).toSet()
            } catch (_: Exception) { emptySet() }
        } ?: emptySet()
    }

    fun getQuestionIdsFromLastNTests(profileId: String, n: Int): Set<String> = runBlocking {
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

    fun recordTestCreated(profileId: String, testId: String, questionIds: List<String>) = runBlocking {
        val prev1 = appMetaDao.get("recent_test_1_$profileId") ?: "[]"
        appMetaDao.set(AppMetaEntity("recent_test_2_$profileId", prev1))
        appMetaDao.set(AppMetaEntity("recent_test_1_$profileId", JSONArray(questionIds).toString()))
    }

    fun recordSeenIdsForProfile(profileId: String, ids: List<String>) = runBlocking {
        val key = "recent_seen_$profileId"
        val existing = appMetaDao.get(key)?.let { json ->
            try { (0 until JSONArray(json).length()).map { JSONArray(json).optString(it, "") }.filter { it.isNotBlank() }.toMutableList() }
            catch (_: Exception) { mutableListOf() }
        } ?: mutableListOf()
        val combined = ids.toMutableList()
        existing.forEach { if (it !in combined) combined.add(0, it) }
        appMetaDao.set(AppMetaEntity(key, JSONArray(combined.take(100)).toString()))
    }

    fun getWrongQuestionIds(withinDays: Int = 7): Set<String> = runBlocking {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(withinDays.toLong())
        historyDao.getWrongIds(cutoff).toSet()
    }

    fun getAllWrongIds(): Set<String> = runBlocking {
        historyDao.getAllWrongIds().toSet()
    }

    fun isSeeded(): Boolean = runBlocking {
        appMetaDao.get(KEY_DB_SEEDED) == "true"
    }

    fun ensureSeeded(): Boolean = runBlocking {
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
    ) = runBlocking {
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
    }

    fun getLastSnapshots(profileId: String = "default", limit: Int = 20): List<TestSnapshotEntity> = runBlocking {
        snapshotDao.getLastSnapshots(profileId, limit)
    }

    fun getSnapshot(testId: String): TestSnapshotEntity? = runBlocking {
        snapshotDao.getSnapshot(testId)
    }
}
