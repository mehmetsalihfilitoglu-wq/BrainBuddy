package com.brainbuddy.app.quiz

import android.content.Context
import android.util.Log
import com.brainbuddy.app.core.ProfileScopedPrefs
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
            existing.wrongCount++
            existing.dueAfterTest = nextDueAfterTest(existing.wrongCount)
            Log.d(TAG, "Wrong registered (again): $questionId wrongCount=${existing.wrongCount}")
        } else {
            wrongPool.add(
                WrongQuestion(
                    questionId = questionId,
                    wrongCount = 1,
                    dueAfterTest = nextDueAfterTest(1)
                )
            )
            Log.d(TAG, "Wrong registered: $questionId")
        }
        saveSchedulerState()
    }

    /**
     * When a repeated question is answered correctly, remove it from the pool.
     */
    fun markCorrect(questionId: String) {
        val removed = wrongPool.removeAll { it.questionId == questionId }
        if (removed) {
            Log.d(TAG, "Question mastered: $questionId")
            saveSchedulerState()
        }
    }

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
        val due = wrongPool
            .filter { completedTests >= it.dueAfterTest }
            .filter { it.lastShownAtCompletedTest < completedTests - 1 }
            .minByOrNull { it.dueAfterTest }
            ?: return null
        Log.d(TAG, "Due wrong injected: ${due.questionId}")
        return due.questionId
    }

    private fun nextDueAfterTest(wrongCount: Int): Int {
        val offset = when (wrongCount) {
            1 -> 3
            2 -> 5
            3 -> 8
            else -> 12
        }
        return completedTests + offset
    }

    fun saveSchedulerState() {
        prefs.edit()
            .putInt(KEY_COMPLETED_TESTS, completedTests)
            .putString(KEY_POOL_JSON, poolToJson())
            .apply()
    }

    fun loadSchedulerState() {
        completedTests = prefs.getInt(KEY_COMPLETED_TESTS, 0)
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
                        dueAfterTest = o.optInt(KEY_DUE_AFTER_TEST, completedTests + 3),
                        lastShownAtCompletedTest = o.optInt(KEY_LAST_SHOWN_AT, -1)
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
                }
            )
        }
        return arr.toString()
    }

    companion object {
        private const val TAG = "WrongScheduler"
        private const val KEY_COMPLETED_TESTS = "scheduler_completed_tests"
        private const val KEY_POOL_JSON = "scheduler_pool_json"
        private const val KEY_QID = "questionId"
        private const val KEY_WRONG_COUNT = "wrongCount"
        private const val KEY_DUE_AFTER_TEST = "dueAfterTest"
        private const val KEY_LAST_SHOWN_AT = "lastShownAtCompletedTest"
    }
}

