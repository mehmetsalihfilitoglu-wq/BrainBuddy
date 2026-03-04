package com.brainbuddy.app.quiz

import android.content.Context
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.QuestionMapper
import com.brainbuddy.app.db.WrongAnswerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray

/**
 * Repository for PastTestDetail: snapshot, wrong questions with unlock state.
 */
class PastTestDetailRepository(private val context: Context) {

    private val db get() = DatabaseProvider.get(context)
    private val snapshotDao get() = db.snapshotDao()
    private val questionDao get() = db.questionDao()
    private val wrongAnswerDao get() = db.wrongAnswerDao()

    suspend fun getSnapshot(testId: String) = snapshotDao.getSnapshot(testId)

    /** Emits list of WrongQuestionUiItem. Re-emits when unlock state changes. */
    fun getWrongQuestionItemsFlow(testId: String, showCorrect: Boolean): Flow<List<WrongQuestionUiItem>> = flow {
        val snapshot = getSnapshot(testId) ?: return@flow emit(emptyList())
        val wrongIds = parseWrongIds(snapshot.wrongQuestionIdsJson)
        if (wrongIds.isEmpty()) return@flow emit(emptyList())
        val questions = questionDao.getQuestionsByIds(wrongIds).map { QuestionMapper.toQuestion(it) }
        val questionsMap = questions.associateBy { it.id }
        val userAnswersMap = parseUserAnswers(snapshot.questionIdsJson, snapshot.userAnswersJson)
        wrongAnswerDao.getWrongsForAttempt(testId).collect { wrongAnswers ->
            val unlockedSet = wrongAnswers.filter { it.isUnlocked }.map { it.questionId }.toSet()
            emit(wrongIds.map { qId ->
                val q = questionsMap[qId]
                val userSel = userAnswersMap[qId] ?: -1
                val userChoiceText = if (userSel in 0..3) q?.choices?.getOrNull(userSel) ?: "-" else "-"
                val correctAnswerText = q?.choices?.getOrNull(q?.correctIndex ?: 0) ?: "?"
                WrongQuestionUiItem(
                    questionId = qId,
                    question = q,
                    userChoiceText = userChoiceText,
                    correctAnswerText = correctAnswerText,
                    hint = q?.hint,
                    isUnlocked = qId in unlockedSet,
                    showCorrect = showCorrect
                )
            })
        }
    }

    suspend fun unlockWrongAnswer(testId: String, questionId: String) {
        wrongAnswerDao.unlockWrongAnswer(testId, questionId)
    }

    suspend fun unlockAllWrongsForAttempt(testId: String) {
        wrongAnswerDao.unlockAllWrongsForAttempt(testId, System.currentTimeMillis())
    }

    private fun parseWrongIds(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            (0 until JSONArray(json).length()).map { JSONArray(json).getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseUserAnswers(questionIdsJson: String?, userAnswersJson: String?): Map<String, Int> {
        if (questionIdsJson.isNullOrBlank() || userAnswersJson.isNullOrBlank()) return emptyMap()
        return try {
            val qIds = JSONArray(questionIdsJson)
            val ans = org.json.JSONArray(userAnswersJson)
            (0 until minOf(qIds.length(), ans.length())).mapNotNull { i ->
                val qId = qIds.optString(i, "")
                val v = ans.opt(i)
                if (qId.isBlank()) null
                else when (v) {
                    is Number -> qId to v.toInt()
                    org.json.JSONObject.NULL, null -> qId to -1
                    else -> null
                }
            }.toMap()
        } catch (_: Exception) { emptyMap() }
    }
}
