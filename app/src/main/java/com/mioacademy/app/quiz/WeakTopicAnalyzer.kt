package com.mioacademy.app.quiz

import android.content.Context
import com.mioacademy.app.core.ActiveProfileManager
import com.mioacademy.app.db.DatabaseProvider
import com.mioacademy.app.db.HistoryDao
import com.mioacademy.app.db.QuestionDao
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
        val history = historyDao.getAllForUser(userId)
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

