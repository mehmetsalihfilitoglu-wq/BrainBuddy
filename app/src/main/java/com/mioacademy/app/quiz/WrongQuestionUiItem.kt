package com.mioacademy.app.quiz

/**
 * UI model for a wrong question card in Test Detail.
 * Locked: show only "Yanlış cevap verilmiş soru" + "Detayı görmek için dokun" + lock icon.
 * Unlocked: show question stem, user's choice; correct answer only in parent mode.
 */
data class WrongQuestionUiItem(
    val questionId: String,
    val question: Question?,
    val userChoiceText: String,
    val correctAnswerText: String,
    val hint: String?,
    val isUnlocked: Boolean,
    val showCorrect: Boolean
)
