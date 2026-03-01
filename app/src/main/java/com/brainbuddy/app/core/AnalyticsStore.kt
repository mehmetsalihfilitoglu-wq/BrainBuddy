package com.brainbuddy.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class QuizSession(
    val tsMs: Long,
    val correct: Int,
    val total: Int,
    val pointsEarned: Int
)

class AnalyticsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordSession(session: QuizSession) {
        val arr = JSONArray(prefs.getString(KEY_SESSIONS, "[]"))
        val obj = JSONObject()
            .put("tsMs", session.tsMs)
            .put("correct", session.correct)
            .put("total", session.total)
            .put("pointsEarned", session.pointsEarned)
        arr.put(obj)
        trimAndSave(arr, KEY_SESSIONS, 200)
    }

    fun recordTestPerformance(perf: TestPerformance) {
        val arr = JSONArray(prefs.getString(KEY_PERFORMANCES, "[]"))
        val obj = JSONObject()
            .put("quizId", perf.quizId)
            .put("tsMs", perf.tsMs)
            .put("userId", perf.userId)
            .put("accuracy", perf.accuracy.toDouble())
            .put("correctCount", perf.correctCount)
            .put("wrongCount", perf.wrongCount)
            .put("blankCount", perf.blankCount)
            .put("totalQuestions", perf.totalQuestions)
            .put("passed", perf.passed)
            .put("wrongQuestionIds", JSONArray(perf.wrongQuestionIds))
            .put("byTopic", JSONObject(perf.byTopic))
            .put("byDifficulty", JSONObject(perf.byDifficulty))
            .put("byTopicCounts", encodeTopicCountsMap(perf.byTopicCounts))
            .put("byDifficultyCounts", encodeTopicCountsMap(perf.byDifficultyCounts))
        arr.put(obj)
        trimAndSave(arr, KEY_PERFORMANCES, 200)
    }

    private fun encodeTopicCountsMap(m: Map<String, TopicCounts>): JSONObject {
        val o = JSONObject()
        m.forEach { (k, tc) ->
            o.put(k, JSONObject()
                .put("correct", tc.correct)
                .put("wrong", tc.wrong)
                .put("blank", tc.blank)
                .put("total", tc.total))
        }
        return o
    }

    private fun decodeTopicCountsMap(obj: JSONObject?): Map<String, TopicCounts> {
        if (obj == null) return emptyMap()
        return obj.keys().asSequence().associateWith { key ->
            val t = obj.getJSONObject(key)
            TopicCounts(
                correct = t.optInt("correct", 0),
                wrong = t.optInt("wrong", 0),
                blank = t.optInt("blank", 0),
                total = t.optInt("total", 0)
            )
        }
    }

    fun getTestPerformances(): List<TestPerformance> {
        val arr = JSONArray(prefs.getString(KEY_PERFORMANCES, "[]"))
        val out = ArrayList<TestPerformance>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val wrongArr = o.optJSONArray("wrongQuestionIds") ?: JSONArray()
            val wrongIds = (0 until wrongArr.length()).map { wrongArr.getString(it) }
            val topicObj = o.optJSONObject("byTopic") ?: JSONObject()
            val diffObj = o.optJSONObject("byDifficulty") ?: JSONObject()
            val byTopic = topicObj.keys().asSequence().associateWith { topicObj.getDouble(it).toFloat() }
            val byDiff = diffObj.keys().asSequence().associateWith { diffObj.getDouble(it).toFloat() }
            val totalQ = o.optInt("totalQuestions", 0)
            val correct = o.optInt("correctCount", 0)
            val wrong = o.optInt("wrongCount", 0)
            val blank = o.optInt("blankCount", 0)
            val byTc = decodeTopicCountsMap(o.optJSONObject("byTopicCounts"))
            val byDc = decodeTopicCountsMap(o.optJSONObject("byDifficultyCounts"))
            out.add(
                TestPerformance(
                    quizId = o.optString("quizId", ""),
                    tsMs = o.getLong("tsMs"),
                    userId = o.optString("userId", "default"),
                    accuracy = o.optDouble("accuracy", 0.0).toFloat(),
                    correctCount = correct,
                    wrongCount = wrong,
                    blankCount = blank,
                    totalQuestions = if (totalQ > 0) totalQ else (correct + wrong + blank),
                    passed = o.optBoolean("passed", true),
                    wrongQuestionIds = wrongIds,
                    byTopic = byTopic,
                    byDifficulty = byDiff,
                    byTopicCounts = byTc,
                    byDifficultyCounts = byDc
                )
            )
        }
        return out
    }

    fun getLastAccuracies(n: Int = 10): List<Float> =
        getTestPerformances().takeLast(n).map { it.accuracy }

    /** Last N test performances for overall stats / recent trend. */
    fun getLastTests(n: Int = 10): List<TestPerformance> =
        getTestPerformances().takeLast(n)

    fun getOverallAccuracy(): Float {
        val perfs = getTestPerformances()
        if (perfs.isEmpty()) return 0f
        return perfs.map { it.accuracy }.average().toFloat()
    }

    fun getTopicMastery(): Map<String, Float> {
        val perfs = getTestPerformances()
        val byTopic = mutableMapOf<String, MutableList<Float>>()
        perfs.forEach { p ->
            p.byTopic.forEach { (topic, acc) ->
                byTopic.getOrPut(topic) { mutableListOf() }.add(acc)
            }
        }
        return byTopic.mapValues { (_, accs) -> accs.average().toFloat() }
    }

    fun getStrongestTopics(n: Int = 3): List<Pair<String, Float>> =
        getTopicMastery().toList().sortedByDescending { it.second }.take(n)

    fun getWeakestTopics(n: Int = 3): List<Pair<String, Float>> =
        getTopicMastery().toList().sortedBy { it.second }.filter { it.second < 100f }.take(n)

    /** Overall counts for X/Y display (correct/wrong/blank/total). */
    fun getOverallCounts(): OverallCounts {
        val perfs = getTestPerformances()
        var correct = 0
        var wrong = 0
        var blank = 0
        perfs.forEach { p ->
            correct += p.correctCount
            wrong += p.wrongCount
            blank += p.blankCount
        }
        return OverallCounts(correct, wrong, blank, correct + wrong + blank)
    }

    /** Topic mastery with counts (correct/total per topic). */
    fun getTopicMasteryWithCounts(): Map<String, TopicCounts> {
        val perfs = getTestPerformances()
        val agg = mutableMapOf<String, MutableList<TopicCounts>>()
        perfs.forEach { p ->
            p.byTopicCounts.forEach { (topic, tc) ->
                agg.getOrPut(topic) { mutableListOf() }.add(tc)
            }
        }
        return agg.mapValues { (_, list) ->
            TopicCounts(
                correct = list.sumOf { it.correct },
                wrong = list.sumOf { it.wrong },
                blank = list.sumOf { it.blank },
                total = list.sumOf { it.total }
            )
        }
    }

    fun getUserStats(): UserStats {
        val counts = getOverallCounts()
        val topicCounts = getTopicMasteryWithCounts()
        return UserStats(
            overallCorrect = counts.correct,
            overallWrong = counts.wrong,
            overallBlank = counts.blank,
            overallTotal = counts.total,
            topicMasteryCounts = topicCounts
        )
    }

    fun getStrongestTopicsWithCounts(n: Int = 3): List<Pair<String, TopicCounts>> =
        getTopicMasteryWithCounts().toList()
            .filter { it.second.total > 0 }
            .sortedByDescending { it.second.accuracy }
            .take(n)

    fun getWeakestTopicsWithCounts(n: Int = 3): List<Pair<String, TopicCounts>> =
        getTopicMasteryWithCounts().toList()
            .filter { it.second.total > 0 && it.second.accuracy < 100f }
            .sortedBy { it.second.accuracy }
            .take(n)

    /** Store review corrections count (when user gets a question right during wrong-answer review). */
    fun recordReviewCorrection() {
        prefs.edit().putInt(KEY_REVIEW_CORRECTIONS, getTotalReviewCorrections() + 1).apply()
    }

    fun getTotalReviewCorrections(): Int = prefs.getInt(KEY_REVIEW_CORRECTIONS, 0)

    private fun trimAndSave(arr: JSONArray, key: String, maxSize: Int) {
        val trimmed = JSONArray()
        val start = (arr.length() - maxSize).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        prefs.edit().putString(key, trimmed.toString()).apply()
    }

    fun getSessions(): List<QuizSession> {
        val arr = JSONArray(prefs.getString(KEY_SESSIONS, "[]"))
        val out = ArrayList<QuizSession>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                QuizSession(
                    tsMs = o.getLong("tsMs"),
                    correct = o.getInt("correct"),
                    total = o.getInt("total"),
                    pointsEarned = o.getInt("pointsEarned")
                )
            )
        }
        return out
    }

    companion object {
        private const val PREFS = "bb_analytics"
        private const val KEY_SESSIONS = "sessions_json"
        private const val KEY_PERFORMANCES = "test_performances"
        private const val KEY_REVIEW_CORRECTIONS = "review_corrections_total"
    }
}