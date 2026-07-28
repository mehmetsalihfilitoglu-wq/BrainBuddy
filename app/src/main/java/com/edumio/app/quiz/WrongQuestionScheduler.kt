package com.edumio.app.quiz

import android.content.Context
import android.util.Log
import com.edumio.app.core.ProfileScopedPrefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Schedules wrong questions to reappear after a number of completed tests (spacing by tests).
 * - 1st wrong → due after 3 tests
 * - 2nd wrong → due after 5 tests
 * - 3rd wrong → due after 8 tests
 * - 4+ wrong → due after 12 tests
 *
 * At most one due wrong question is injected per test.
 */
class WrongQuestionScheduler(context: Context) {

    private val prefs = ProfileScopedPrefs.wrongScheduler(context)

    private val wrongPool = mutableListOf<WrongQuestion>()
    var completedTests: Int = 0
        private set

    init {
        loadSchedulerState()
    }

    /**
     * Register a wrong answer. If the question is already in the pool, increment wrongCount
     * and update dueAfterTest. Otherwise add with wrongCount = 1.
     */
    fun registerWrong(questionId: String) {
        val existing = wrongPool.find { it.questionId == questionId }
        if (existing != null) {
            // Wrong again → back to the bottom of the ladder, short interval.
            existing.wrongCount++
            existing.box = 0
            existing.dueAfterTest = nextDueAfterTest(0)
            Log.d(TAG, "Wrong registered (again): $questionId wrongCount=${existing.wrongCount} box=0")
        } else {
            wrongPool.add(
                WrongQuestion(
                    questionId = questionId,
                    wrongCount = 1,
                    dueAfterTest = nextDueAfterTest(0),
                    box = 0,
                    firstSeenMs = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "Wrong registered: $questionId")
        }
        saveSchedulerState()
    }

    /**
     * A correct answer advances the question one step up the mastery ladder with
     * a longer interval. It is only removed once it clears [MASTERED_BOX] — i.e.
     * answered correctly several times across spaced tests. One correct answer no
     * longer "cleans" the question; the goal is durable learning.
     */
    fun markCorrect(questionId: String) {
        val q = wrongPool.find { it.questionId == questionId } ?: return
        q.correctCount++
        q.box++
        if (q.box >= MASTERED_BOX) {
            wrongPool.removeAll { it.questionId == questionId }
            masteredTotal++
            Log.d(TAG, "Question mastered (box=${q.box}): $questionId")
        } else {
            q.dueAfterTest = nextDueAfterTest(q.box)
            Log.d(TAG, "Question advanced to box ${q.box}: $questionId")
        }
        saveSchedulerState()
    }

    /** Count of questions that reached mastery in this area (for journey/readiness). */
    var masteredTotal: Int = 0
        private set

    /** Questions still on the review ladder, most valuable first (repeated mistakes + earlier due). */
    fun reviewQueueSize(): Int = wrongPool.size

    fun dueCount(): Int = wrongPool.count { completedTests >= it.dueAfterTest }

    data class LearningStates(val wrong: Int, val reviewing: Int, val mastered: Int) {
        val total: Int get() = wrong + reviewing + mastered
    }

    /** How the student's questions are distributed across the learning path (real). */
    fun learningStates(): LearningStates = LearningStates(
        wrong = wrongPool.count { it.box == 0 },
        reviewing = wrongPool.count { it.box >= 1 },
        mastered = masteredTotal
    )

    /**
     * Call when a test is finished. Increments the global test counter.
     */
    fun onTestCompleted() {
        completedTests++
        saveSchedulerState()
    }

    /**
     * Mark that a due question was actually injected into a test at the current completedTests index.
     */
    fun markShown(questionId: String) {
        val w = wrongPool.find { it.questionId == questionId } ?: return
        w.lastShownAtCompletedTest = completedTests
        saveSchedulerState()
    }

    /**
     * Returns one question ID that is due (completedTests >= dueAfterTest) and not shown in the
     * immediately previous normal test, or null.
     * Only one question per call; does not remove from pool (removal happens on markCorrect).
     */
    fun getDueWrongQuestion(): String? {
        // Intelligent priority: among due questions, prefer repeated mistakes,
        // then the one that has waited longest (earliest due).
        val due = wrongPool
            .filter { completedTests >= it.dueAfterTest }
            .filter { it.lastShownAtCompletedTest < completedTests - 1 }
            .sortedWith(compareByDescending<WrongQuestion> { it.wrongCount }.thenBy { it.dueAfterTest })
            .firstOrNull()
            ?: return null
        Log.d(TAG, "Due wrong injected: ${due.questionId} box=${due.box} wrongCount=${due.wrongCount}")
        return due.questionId
    }

    /** Interval grows as the question climbs the ladder (spacing effect). */
    private fun nextDueAfterTest(box: Int): Int {
        val offset = when (box) {
            0 -> 2   // just wrong → soon
            1 -> 4   // one spaced correct
            else -> 8 // deeper — long interval before the mastering review
        }
        return completedTests + offset
    }

    fun saveSchedulerState() {
        prefs.edit()
            .putInt(KEY_COMPLETED_TESTS, completedTests)
            .putInt(KEY_MASTERED_TOTAL, masteredTotal)
            .putString(KEY_POOL_JSON, poolToJson())
            .apply()
    }

    fun loadSchedulerState() {
        completedTests = prefs.getInt(KEY_COMPLETED_TESTS, 0)
        masteredTotal = prefs.getInt(KEY_MASTERED_TOTAL, 0)
        wrongPool.clear()
        val json = prefs.getString(KEY_POOL_JSON, null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                wrongPool.add(
                    WrongQuestion(
                        questionId = o.optString(KEY_QID, ""),
                        wrongCount = o.optInt(KEY_WRONG_COUNT, 1),
                        dueAfterTest = o.optInt(KEY_DUE_AFTER_TEST, completedTests + 2),
                        lastShownAtCompletedTest = o.optInt(KEY_LAST_SHOWN_AT, -1),
                        box = o.optInt(KEY_BOX, 0),
                        correctCount = o.optInt(KEY_CORRECT_COUNT, 0),
                        firstSeenMs = o.optLong(KEY_FIRST_SEEN, 0L)
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun poolToJson(): String {
        val arr = JSONArray()
        for (w in wrongPool) {
            arr.put(
                JSONObject().apply {
                    put(KEY_QID, w.questionId)
                    put(KEY_WRONG_COUNT, w.wrongCount)
                    put(KEY_DUE_AFTER_TEST, w.dueAfterTest)
                    put(KEY_LAST_SHOWN_AT, w.lastShownAtCompletedTest)
                    put(KEY_BOX, w.box)
                    put(KEY_CORRECT_COUNT, w.correctCount)
                    put(KEY_FIRST_SEEN, w.firstSeenMs)
                }
            )
        }
        return arr.toString()
    }

    companion object {
        private const val TAG = "WrongScheduler"
        /** Correct answers (across spaced tests) needed to master a question. */
        const val MASTERED_BOX = 3
        private const val KEY_COMPLETED_TESTS = "scheduler_completed_tests"
        private const val KEY_MASTERED_TOTAL = "scheduler_mastered_total"
        private const val KEY_POOL_JSON = "scheduler_pool_json"
        private const val KEY_QID = "questionId"
        private const val KEY_WRONG_COUNT = "wrongCount"
        private const val KEY_DUE_AFTER_TEST = "dueAfterTest"
        private const val KEY_LAST_SHOWN_AT = "lastShownAtCompletedTest"
        private const val KEY_BOX = "box"
        private const val KEY_CORRECT_COUNT = "correctCount"
        private const val KEY_FIRST_SEEN = "firstSeenMs"
    }
}

