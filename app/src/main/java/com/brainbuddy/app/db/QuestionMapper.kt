package com.brainbuddy.app.db

import com.brainbuddy.app.quiz.ExamType
import com.brainbuddy.app.quiz.LevelGroup
import com.brainbuddy.app.quiz.Question
import com.brainbuddy.app.quiz.QuizDifficulty
import com.brainbuddy.app.quiz.Subject
import org.json.JSONArray

object QuestionMapper {

    fun toQuestion(j: HistoryJoinedQuestion): Question {
        val choices = parseChoices(j.optionsJson)
        val subject = mapSubject(j.subject)
        val levelGroup = j.levelGroup?.let { parseLevelGroup(it) } ?: LevelGroup.GRADE_5_8
        val grade = j.gradeTag?.toIntOrNull()?.coerceIn(1, 8) ?: 6
        return Question(
            id = j.questionId,
            levelGroup = levelGroup,
            subject = subject,
            gradeTag = j.gradeTag ?: "",
            grade = grade,
            stem = j.text,
            choices = choices,
            correctIndex = j.correctIndex.coerceIn(0, choices.size - 1),
            hint = j.hint?.takeIf { it.isNotBlank() },
            imageAsset = null,
            difficulty = QuizDifficulty.MEDIUM,
            examType = ExamType.GENERAL,
            topic = null
        )
    }

    fun toQuestion(e: QuestionEntity): Question {
        val choices = parseChoices(e.optionsJson)
        val subject = mapSubject(e.subject)
        val levelGroup = e.levelGroup?.let { parseLevelGroup(it) } ?: LevelGroup.GRADE_5_8
        val difficulty = when (e.difficulty) {
            0 -> QuizDifficulty.EASY
            2 -> QuizDifficulty.HARD
            3 -> QuizDifficulty.VERY_HARD
            else -> QuizDifficulty.MEDIUM
        }
        val grade = if (e.grade in 1..8) e.grade else (e.gradeTag?.toIntOrNull()?.coerceIn(1, 8) ?: 6)
        val examType = e.examType?.let { try { ExamType.valueOf(it) } catch (_: Exception) { ExamType.GENERAL } } ?: ExamType.GENERAL
        return Question(
            id = e.id,
            levelGroup = levelGroup,
            subject = subject,
            gradeTag = e.gradeTag ?: grade.toString(),
            grade = grade,
            stem = e.text,
            choices = choices,
            correctIndex = e.correctIndex.coerceIn(0, choices.size - 1),
            hint = e.hint?.takeIf { it.isNotBlank() },
            imageAsset = e.imageAsset?.takeIf { it.isNotBlank() },
            difficulty = difficulty,
            examType = examType,
            topic = parseFirstTag(e.tagsJson)
        )
    }

    private fun parseChoices(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.optString(it, "") }
        } catch (_: Exception) {
            listOf("A", "B", "C", "D")
        }
    }

    private fun mapSubject(s: String): Subject = when (s.lowercase()) {
        "math", "mat" -> Subject.MAT
        "tr", "turkce" -> Subject.TURKCE
        "en", "ing" -> Subject.ING
        "fen" -> Subject.FEN
        "sosyal" -> Subject.SOSYAL
        else -> Subject.MAT
    }

    private fun parseLevelGroup(s: String): LevelGroup = try {
        LevelGroup.valueOf(s)
    } catch (_: Exception) {
        LevelGroup.GRADE_5_8
    }

    private fun parseFirstTag(tagsJson: String?): String? {
        if (tagsJson.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(tagsJson)
            if (arr.length() > 0) arr.optString(0, "").takeIf { it.isNotBlank() } else null
        } catch (_: Exception) {
            null
        }
    }
}
