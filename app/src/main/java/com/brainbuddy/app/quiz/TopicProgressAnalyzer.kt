package com.brainbuddy.app.quiz

import android.content.Context
import com.brainbuddy.app.core.ActiveProfileManager
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.HistoryDao
import com.brainbuddy.app.db.QuestionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Computes per-topic and per-subject progress using the Room question history.
 */
class TopicProgressAnalyzer(context: Context) {

    private val db = DatabaseProvider.get(context)
    private val historyDao: HistoryDao = db.historyDao()
    private val questionDao: QuestionDao = db.questionDao()
    private val userId: String = ActiveProfileManager.getActiveProfileId(context)

    data class SubjectProgress(
        val subjectCode: String,
        val subjectName: String,
        val progress: Float,
        val weakTopics: List<TopicProgress>,
        val completedTopics: List<TopicProgress>
    )

    private val subjectDisplayNames = mapOf(
        "mat" to "Math",
        "fen" to "Fen",
        "turkce" to "Turkish",
        "ing" to "English",
        "sosyal" to "Social"
    )

    /**
     * Full topic progress list (all subjects).
     */
    fun getTopicProgress(): List<TopicProgress> = runBlocking(Dispatchers.IO) {
        val history = historyDao.getAllForUser(userId)
        if (history.isEmpty()) return@runBlocking emptyList()

        val byQuestion = history.groupBy { it.questionId }
        val ids = byQuestion.keys.toList()
        val questions = questionDao.getQuestionsByIds(ids).associateBy { it.id }

        // Aggregate per topic: (correct, wrong)
        val byTopicCounts = mutableMapOf<String, Pair<Int, Int>>() // topic -> (correct, wrong)

        byQuestion.forEach { (qid, rows) ->
            val q = questions[qid] ?: return@forEach
            val topic = q.topic ?: "Other"
            var correct = 0
            var wrong = 0
            rows.forEach { h ->
                correct += h.correctCount
                wrong += h.wrongCount
            }
            if (correct + wrong > 0) {
                val prev = byTopicCounts[topic] ?: (0 to 0)
                byTopicCounts[topic] = (prev.first + correct) to (prev.second + wrong)
            }
        }

        return@runBlocking byTopicCounts.map { (topic, pair) ->
            val correct = pair.first
            val wrong = pair.second
            val total = correct + wrong
            val progress = if (total > 0) correct.toFloat() / total.toFloat() else 0f
            TopicProgress(topic = topic, correct = correct, wrong = wrong, total = total, progress = progress)
        }
    }

    /**
     * Subject-level progress summaries with weak/completed topics.
     */
    fun getSubjectProgress(): List<SubjectProgress> = runBlocking(Dispatchers.IO) {
        val history = historyDao.getAllForUser(userId)
        if (history.isEmpty()) return@runBlocking emptyList()

        val byQuestion = history.groupBy { it.questionId }
        val ids = byQuestion.keys.toList()
        val questions = questionDao.getQuestionsByIds(ids).associateBy { it.id }

        // key: Pair(subjectCode, topic) -> (correct, wrong)
        data class Key(val subject: String, val topic: String)
        val byKey = mutableMapOf<Key, Pair<Int, Int>>()

        byQuestion.forEach { (qid, rows) ->
            val q = questions[qid] ?: return@forEach
            val subject = q.subject
            val topic = q.topic ?: "Other"
            var correct = 0
            var wrong = 0
            rows.forEach { h ->
                correct += h.correctCount
                wrong += h.wrongCount
            }
            if (correct + wrong > 0) {
                val key = Key(subject, topic)
                val prev = byKey[key] ?: (0 to 0)
                byKey[key] = (prev.first + correct) to (prev.second + wrong)
            }
        }

        // Build TopicProgress per subject+topic
        val bySubjectTopics = mutableMapOf<String, MutableList<TopicProgress>>() // subjectCode -> topics
        byKey.forEach { (key, pair) ->
            val correct = pair.first
            val wrong = pair.second
            val total = correct + wrong
            val progress = if (total > 0) correct.toFloat() / total.toFloat() else 0f
            val tp = TopicProgress(topic = key.topic, correct = correct, wrong = wrong, total = total, progress = progress)
            bySubjectTopics.getOrPut(key.subject) { mutableListOf() }.add(tp)
        }

        // Build subject summaries
        return@runBlocking bySubjectTopics.map { (subjectCode, topics) ->
            val subjectCorrect = topics.sumOf { it.correct }
            val subjectWrong = topics.sumOf { it.wrong }
            val subjectTotal = subjectCorrect + subjectWrong
            val subjectProgress = if (subjectTotal > 0) subjectCorrect.toFloat() / subjectTotal.toFloat() else 0f

            val weakTopics = topics.filter { it.state() == TopicProgressState.WEAK }
            val completedTopics = topics.filter { it.state() == TopicProgressState.COMPLETED }

            SubjectProgress(
                subjectCode = subjectCode,
                subjectName = subjectDisplayNames[subjectCode] ?: subjectCode,
                progress = subjectProgress,
                weakTopics = weakTopics.sortedBy { it.progress },
                completedTopics = completedTopics.sortedByDescending { it.progress }
            )
        }
    }
}

