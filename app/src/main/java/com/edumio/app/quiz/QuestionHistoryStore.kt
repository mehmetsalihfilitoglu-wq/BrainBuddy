package com.edumio.app.quiz

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Soru geçmişi (G1 spec):
 * - wrongCountTotal, correctCountTotal
 * - lastAnsweredAt, lastResultWasWrong
 * - dueAt (tekrar sorulma zamanı)
 * - seenCount, lastSeenInTestId
 *
 * Spaced repetition (doğru cevaplarda):
 * - ilk doğru: +12 saat
 * - üst üste 2 doğru: +2 gün
 * - üst üste 3+ doğru: +7 gün
 */
class QuestionHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class HistoryEntry(
        val questionId: String,
        val lastSeenAt: Long,
        val timesCorrect: Int,
        val timesWrong: Int,
        val lastResult: String, // "correct" | "wrong"
        // G1/G2 extended fields
        val wrongCountTotal: Int,
        val correctCountTotal: Int,
        val lastAnsweredAt: Long,
        val lastResultWasWrong: Boolean,
        val dueAt: Long,
        val seenCount: Int,
        val lastSeenInTestId: String?,
        val lastSeenTestIndex: Int,  // G2: son kaçıncı testte çıktı (globalTestIndex)
        val consecutiveCorrectCount: Int
    )

    fun getHistory(questionId: String): HistoryEntry? {
        val json = prefs.getString("q_$questionId", null) ?: return null
        return parseEntry(questionId, json)
    }

    private fun parseEntry(questionId: String, json: String): HistoryEntry? = try {
        val o = JSONObject(json)
        val lastResult = o.optString("lastResult", "")
        HistoryEntry(
            questionId = questionId,
            lastSeenAt = o.optLong("lastSeenAt", 0L),
            timesCorrect = o.optInt("timesCorrect", 0),
            timesWrong = o.optInt("timesWrong", 0),
            lastResult = lastResult,
            wrongCountTotal = o.optInt("wrongCountTotal", o.optInt("timesWrong", 0)),
            correctCountTotal = o.optInt("correctCountTotal", o.optInt("timesCorrect", 0)),
            lastAnsweredAt = o.optLong("lastAnsweredAt", o.optLong("lastSeenAt", 0L)),
            lastResultWasWrong = o.optBoolean("lastResultWasWrong", lastResult == "wrong"),
            dueAt = o.optLong("dueAt", 0L),
            seenCount = o.optInt("seenCount", 0),
            lastSeenInTestId = o.optString("lastSeenInTestId", "").takeIf { it.isNotBlank() },
            lastSeenTestIndex = o.optInt("lastSeenTestIndex", 0),
            consecutiveCorrectCount = o.optInt("consecutiveCorrectCount", 0)
        )
    } catch (_: Exception) { null }

    /** G1: Yanlış -> dueAt=now, hemen aday. Doğru -> dueAt=now+interval (spaced repetition) */
    fun recordAnswer(questionId: String, correct: Boolean, testId: String? = null) {
        val h = getHistory(questionId) ?: defaultEntry(questionId)
        val now = System.currentTimeMillis()
        val wrongCountTotal = h.wrongCountTotal + if (correct) 0 else 1
        val correctCountTotal = h.correctCountTotal + if (correct) 1 else 0
        val lastResultWasWrong = !correct
        val consecutiveCorrectCount = if (correct) h.consecutiveCorrectCount + 1 else 0

        val dueAt = if (correct) {
            val intervalMs = when (consecutiveCorrectCount) {
                1 -> TimeUnit.HOURS.toMillis(12)
                2 -> TimeUnit.DAYS.toMillis(2)
                3 -> TimeUnit.DAYS.toMillis(7)
                else -> TimeUnit.DAYS.toMillis(14)  // streakCorrect >= 4
            }
            now + intervalMs
        } else {
            now // G4: yanlış olunca hemen aday (bir sonraki testte dueWrongPool'a girsin)
        }

        val newEntry = HistoryEntry(
            questionId = questionId,
            lastSeenAt = now,
            timesCorrect = correctCountTotal.coerceIn(0, MAX_COUNT_CAP),
            timesWrong = wrongCountTotal.coerceIn(0, MAX_COUNT_CAP),
            lastResult = if (correct) "correct" else "wrong",
            wrongCountTotal = wrongCountTotal.coerceIn(0, MAX_COUNT_CAP),
            correctCountTotal = correctCountTotal.coerceIn(0, MAX_COUNT_CAP),
            lastAnsweredAt = now,
            lastResultWasWrong = lastResultWasWrong,
            dueAt = dueAt,
            seenCount = (h.seenCount + 1).coerceIn(0, MAX_COUNT_CAP),
            lastSeenInTestId = testId ?: h.lastSeenInTestId,
            lastSeenTestIndex = h.lastSeenTestIndex,
            consecutiveCorrectCount = consecutiveCorrectCount
        )
        saveEntry(newEntry)
    }

    private fun defaultEntry(questionId: String) = HistoryEntry(
        questionId = questionId,
        lastSeenAt = 0L,
        timesCorrect = 0,
        timesWrong = 0,
        lastResult = "",
        wrongCountTotal = 0,
        correctCountTotal = 0,
        lastAnsweredAt = 0L,
        lastResultWasWrong = false,
        dueAt = 0L,
        seenCount = 0,
        lastSeenInTestId = null,
        lastSeenTestIndex = 0,
        consecutiveCorrectCount = 0
    )

    private fun saveEntry(e: HistoryEntry) {
        val o = JSONObject().apply {
            put("lastSeenAt", e.lastSeenAt)
            put("timesCorrect", e.timesCorrect)
            put("timesWrong", e.timesWrong)
            put("lastResult", e.lastResult)
            put("wrongCountTotal", e.wrongCountTotal)
            put("correctCountTotal", e.correctCountTotal)
            put("lastAnsweredAt", e.lastAnsweredAt)
            put("lastResultWasWrong", e.lastResultWasWrong)
            put("dueAt", e.dueAt)
            put("seenCount", e.seenCount)
            put("lastSeenInTestId", e.lastSeenInTestId ?: "")
            put("lastSeenTestIndex", e.lastSeenTestIndex)
            put("consecutiveCorrectCount", e.consecutiveCorrectCount)
        }
        prefs.edit().putString("q_${e.questionId}", o.toString()).apply()
    }

    /** G1: Soru tekrar sorulma zamanı geldi mi? (dueAt <= now) */
    fun isDue(questionId: String): Boolean {
        val h = getHistory(questionId) ?: return true
        return h.dueAt <= System.currentTimeMillis()
    }

    /** G1: Son cevap yanlış mıydı? */
    fun lastWasWrong(questionId: String): Boolean {
        val h = getHistory(questionId) ?: return false
        return h.lastResultWasWrong
    }

    /** G1: Hiç görülmemiş soru */
    fun neverSeen(questionId: String): Boolean {
        val h = getHistory(questionId) ?: return true
        return h.seenCount == 0
    }

    /** G2: Her yeni testte +1. Quiz bitince artırılır. */
    fun getGlobalTestIndex(): Int = prefs.getInt(KEY_GLOBAL_TEST_INDEX, 0)

    /** G5: Quiz bitince çağrılır. globalTestIndex++, her soru için lastSeenTestIndex günceller. */
    fun onQuizCompleted(questionIds: List<String>) {
        val newIndex = getGlobalTestIndex() + 1
        prefs.edit().putInt(KEY_GLOBAL_TEST_INDEX, newIndex).apply()
        questionIds.forEach { id ->
            val h = getHistory(id) ?: defaultEntry(id)
            val newEntry = h.copy(lastSeenTestIndex = newIndex)
            saveEntry(newEntry)
        }
    }

    /** G4: Test oluşturulunca her soru için lastSeenInTestId güncelle (seenCount quiz bitince artar) */
    fun recordSeenInTest(questionIds: List<String>, testId: String) {
        val now = System.currentTimeMillis()
        prefs.edit().apply {
            questionIds.forEach { id ->
                val h = getHistory(id) ?: defaultEntry(id)
                val newEntry = HistoryEntry(
                    questionId = id,
                    lastSeenAt = now,
                    timesCorrect = h.timesCorrect,
                    timesWrong = h.timesWrong,
                    lastResult = h.lastResult,
                    wrongCountTotal = h.wrongCountTotal,
                    correctCountTotal = h.correctCountTotal,
                    lastAnsweredAt = h.lastAnsweredAt,
                    lastResultWasWrong = h.lastResultWasWrong,
                    dueAt = h.dueAt,
                    seenCount = h.seenCount,
                    lastSeenInTestId = testId,
                    lastSeenTestIndex = h.lastSeenTestIndex,
                    consecutiveCorrectCount = h.consecutiveCorrectCount
                )
                val o = JSONObject().apply {
                    put("lastSeenAt", newEntry.lastSeenAt)
                    put("timesCorrect", newEntry.timesCorrect)
                    put("timesWrong", newEntry.timesWrong)
                    put("lastResult", newEntry.lastResult)
                    put("wrongCountTotal", newEntry.wrongCountTotal)
                    put("correctCountTotal", newEntry.correctCountTotal)
                    put("lastAnsweredAt", newEntry.lastAnsweredAt)
                    put("lastResultWasWrong", newEntry.lastResultWasWrong)
                    put("dueAt", newEntry.dueAt)
                    put("seenCount", newEntry.seenCount)
                    put("lastSeenInTestId", newEntry.lastSeenInTestId ?: "")
                    put("lastSeenTestIndex", newEntry.lastSeenTestIndex)
                    put("consecutiveCorrectCount", newEntry.consecutiveCorrectCount)
                }
                putString("q_$id", o.toString())
            }
            apply()
        }
    }

    fun getWrongQuestionIds(withinDays: Int = 7): Set<String> {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(withinDays.toLong())
        val allKeys = prefs.all.keys.filter { it.startsWith("q_") }
        return allKeys.mapNotNull { key ->
            val id = key.removePrefix("q_")
            val h = getHistory(id) ?: return@mapNotNull null
            if (h.lastResultWasWrong && h.lastAnsweredAt >= cutoff) id else null
        }.toSet()
    }

    fun getRecentlySeenIds(limit: Int = 300): Set<String> =
        getRecentlySeenIdsForProfile(DEFAULT_PROFILE, limit)

    fun getAllWrongIds(): Set<String> {
        val allKeys = prefs.all.keys.filter { it.startsWith("q_") }
        return allKeys.mapNotNull { key ->
            val id = key.removePrefix("q_")
            val h = getHistory(id) ?: return@mapNotNull null
            if (h.wrongCountTotal > 0) id else null
        }.toSet()
    }

    fun recordSeenIds(ids: List<String>) {
        recordSeenIdsForProfile(DEFAULT_PROFILE, ids)
    }

    fun getRecentlySeenIdsForProfile(profileId: String, limit: Int = 300): Set<String> {
        val key = "recent_seen_$profileId"
        val json = prefs.getString(key, "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length())
                .mapNotNull { i -> arr.optString(i, null).takeIf { it.isNotBlank() } }
                .take(limit)
                .toSet()
        } catch (_: Exception) { emptySet() }
    }

    fun recordSeenIdsForProfile(profileId: String, ids: List<String>) {
        val existing = (prefs.getString("recent_seen_$profileId", "[]") ?: "[]").let { s ->
            try {
                val arr = org.json.JSONArray(s)
                (0 until arr.length()).mapNotNull { i -> arr.optString(i, null).takeIf { it.isNotBlank() } }
            } catch (_: Exception) {
                emptyList()
            }
        }
        // STRICT QUALITY MODE: raised cap from 150 to 300.
        // New IDs are most recent; keep unique order and cap at 300.
        val combined = (ids + existing).distinct().take(300)
        prefs.edit().putString("recent_seen_$profileId", org.json.JSONArray(combined).toString()).apply()
    }

    /** G2: Son N testte çıkan soru ID'leri - öncelik düşürmek için */
    @Suppress("UNUSED_PARAMETER")
    fun recordTestCreated(profileId: String, testId: String, questionIds: List<String>) {
        // Shift older slots down: 4→5, 3→4, 2→3, 1→2
        val editor = prefs.edit()
        for (slot in CROSS_QUIZ_WINDOW downTo 2) {
            val prev = prefs.getString("recent_test_${slot - 1}_$profileId", "[]") ?: "[]"
            editor.putString("recent_test_${slot}_$profileId", prev)
        }
        editor.putString("recent_test_1_$profileId", org.json.JSONArray(questionIds).toString())
        editor.apply()
    }

    fun getQuestionIdsFromLastNTests(profileId: String, n: Int): Set<String> {
        val result = mutableSetOf<String>()
        val effectiveN = n.coerceIn(1, CROSS_QUIZ_WINDOW)
        for (slot in 1..effectiveN) {
            (prefs.getString("recent_test_${slot}_$profileId", "[]") ?: "[]").let { json ->
                try { org.json.JSONArray(json).let { arr -> for (i in 0 until arr.length()) result.add(arr.optString(i, "")) } } catch (_: Exception) { }
            }
        }
        return result.filter { it.isNotBlank() }.toSet()
    }

    companion object {
        private const val DEFAULT_PROFILE = "default"
        private const val PREFS = "edu_question_history"
        private const val MAX_COUNT_CAP = 10000
        private const val KEY_GLOBAL_TEST_INDEX = "global_test_index"
        /**
         * STRICT QUALITY MODE: raised from 5 to 10.
         * Tracks last 10 quizzes (~200 questions) for hard-block repeat prevention.
         */
        private const val CROSS_QUIZ_WINDOW = 10
    }
}
