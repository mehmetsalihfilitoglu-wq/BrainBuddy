package com.brainbuddy.app.quiz

import android.content.Context
import com.brainbuddy.app.core.ActiveProfileManager
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.HistoryDao
import com.brainbuddy.app.db.QuestionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Computes weak topics from question history: topics with highest wrong rate (wrong / total).
 */
class WeakTopicAnalyzer(context: Context) {

    private val db = DatabaseProvider.get(context)
    private val historyDao: HistoryDao = db.historyDao()
    private val questionDao: QuestionDao = db.questionDao()
    private val userId: String = ActiveProfileManager.getActiveProfileId(context)

    /**
     * Returns up to top 3 topic names with highest wrongRate = wrongCount / (wrongCount + correctCount),
     * considering only rows with some history (wrongCount + correctCount > 0).
     */
    fun getTopWeakTopics(limit: Int = 3): List<String> = runBlocking(Dispatchers.IO) {
        // Join history with questions at the DB level is not exposed directly here, so we:
        // 1) load all history rows for user
        // 2) map questionId -> topic via QuestionDao
        val history = questionDao.getAllHistoryForUser(userId) // extension defined on QuestionDao
        if (history.isEmpty()) return@runBlocking emptyList()

        val byQuestion = history.groupBy { it.questionId }
        val ids = byQuestion.keys.toList()
        val questions = questionDao.getQuestionsByIds(ids).associateBy { it.id }

        val byTopic = mutableMapOf<String, Pair<Int, Int>>() // topic -> (wrong, total)
        byQuestion.forEach { (qid, rows) ->
            val q = questions[qid] ?: return@forEach
            val topic = q.topic ?: "Diğer"
            var wrong = 0
            var total = 0
            rows.forEach { h ->
                wrong += h.wrongCount
                total += h.wrongCount + h.correctCount
            }
            if (total > 0) {
                val prev = byTopic[topic] ?: (0 to 0)
                byTopic[topic] = (prev.first + wrong) to (prev.second + total)
            }
        }

        return@runBlocking byTopic
            .filter { it.value.second > 0 }
            .map { (topic, pair) ->
                val wrong = pair.first.toDouble()
                val total = pair.second.toDouble()
                val rate = if (total > 0) wrong / total else 0.0
                topic to rate
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}

