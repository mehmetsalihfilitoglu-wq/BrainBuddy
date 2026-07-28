package com.edumio.app.core.exam

/**
 * One section/subject within an AdmissionExam.
 * questionCount is official; weight = questionCount / exam.totalQuestions.
 */
data class ExamSubject(
    val id: String,
    val displayNameTr: String,
    val questionCount: Int,
    val weight: Float
)
