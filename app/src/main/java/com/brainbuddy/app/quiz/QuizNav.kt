package com.brainbuddy.app.quiz

import android.content.Context
import android.content.Intent

object QuizNav {
    fun openResult(
        context: Context,
        answers: List<AnswerResult>,
        level: Int
    ) {
        val i = Intent(context, QuizResultActivity::class.java).apply {
            putExtra(QuizContracts.EXTRA_ANSWERS_JSON, encodeAnswers(answers))
            putExtra(QuizContracts.EXTRA_LEVEL, level)
        }
        context.startActivity(i)
    }
}