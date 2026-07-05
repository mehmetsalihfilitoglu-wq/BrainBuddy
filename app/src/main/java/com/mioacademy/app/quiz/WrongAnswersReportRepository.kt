package com.mioacademy.app.quiz

import android.content.Context
import com.mioacademy.app.db.DatabaseProvider
import com.mioacademy.app.db.QuestionMapper
import org.json.JSONArray

/**
 * Repository for WrongAnswersReport: wrong answers in date range from wrong_answers + test_snapshots.
 * Same data source as PastTestDetail (wrong_answers table).
 */
class WrongAnswersReportRepository(private val context: Context) {

    private val db get() = DatabaseProvider.get(context)
    private val wrongAnswerDao get() = db.wrongAnswerDao()
    private val snapshotDao get() = db.snapshotDao()
    private val questionDao get() = db.questionDao()

    /** Load wrong report items for date range. Blocks. */
    suspend fun loadWrongItems(sinceMillis: Long, profileId: String): List<WrongReportUiItem> {
        val rows = wrongAnswerDao.getWrongInRange(sinceMillis, profileId)
        if (rows.isEmpty()) return emptyList()

        val testIds = rows.map { it.testId }.distinct()
        val snapshots = testIds.mapNotNull { snapshotDao.getSnapshot(it) }.associateBy { it.testId }
        val allQuestionIds = rows.map { it.questionId }.distinct()
        val questionsMap = if (allQuestionIds.isEmpty()) emptyMap()
        else questionDao.getQuestionsByIds(allQuestionIds).map { QuestionMapper.toQuestion(it) }.associateBy { it.id }

        return rows.map { row ->
            val snapshot = snapshots[row.testId]
            val question = questionsMap[row.questionId]
            val userChoiceText = parseUserChoice(snapshot, row.questionId, question)
            WrongReportUiItem(
                testId = row.testId,
                questionId = row.questionId,
                questionStem = question?.stem,
                userChoiceText = userChoiceText,
                isUnlocked = row.isUnlocked
            )
        }
    }

    suspend fun unlockWrongAnswer(testId: String, questionId: String) {
        wrongAnswerDao.unlockWrongAnswer(testId, questionId)
    }

    private fun parseUserChoice(snapshot: com.mioacademy.app.db.TestSnapshotEntity?, questionId: String, question: Question?): String {
        if (snapshot == null || question == null) return "-"
        return try {
            val qIds = JSONArray(snapshot.questionIdsJson)
            val ans = JSONArray(snapshot.userAnswersJson)
            val idx = (0 until qIds.length()).indexOfFirst { qIds.optString(it, "") == questionId }
            if (idx < 0) return "-"
            val userSel = ans.opt(idx)
            val sel = when (userSel) {
                is Number -> userSel.toInt()
                else -> -1
            }
            if (sel in 0..3) question.choices.getOrNull(sel) ?: "-" else "-"
        } catch (_: Exception) { "-" }
    }
}

/** UI item for WrongAnswersReport. Correct answer never shown (per spec). */
data class WrongReportUiItem(
    val testId: String,
    val questionId: String,
    val questionStem: String?,
    val userChoiceText: String,
    val isUnlocked: Boolean
)
