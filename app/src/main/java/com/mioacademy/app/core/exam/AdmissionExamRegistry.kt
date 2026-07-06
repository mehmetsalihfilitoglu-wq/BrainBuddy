package com.mioacademy.app.core.exam

import com.mioacademy.app.core.ExamType

object AdmissionExamRegistry {

    private val IMAT = AdmissionExam(
        examType = ExamType.IMAT,
        durationMinutes = 100,
        totalQuestions = 60,
        scoring = ExamScoring(correctPoints = 1.5f, wrongPenalty = 0.4f),
        subjects = listOf(
            ExamSubject("biology",      "Biyoloji",           23, 23f / 60),
            ExamSubject("chemistry",    "Kimya",              15, 15f / 60),
            ExamSubject("physics_math", "Fizik & Matematik",  13, 13f / 60),
            ExamSubject("logic",        "Mantık & Okuma",      9,  9f / 60)
        )
    )

    private val TIL_I = AdmissionExam(
        examType = ExamType.TIL_I,
        durationMinutes = 90,
        totalQuestions = 42,
        scoring = ExamScoring(correctPoints = 1f, wrongPenalty = 0.25f),
        subjects = listOf(
            ExamSubject("math",            "Matematik",        16, 16f / 42),
            ExamSubject("logic_reading",   "Mantık & Okuma",   10, 10f / 42),
            ExamSubject("physics",         "Fizik",            10, 10f / 42),
            ExamSubject("basic_technical", "Teknik Temel",      6,  6f / 42)
        )
    )

    private val TIL_A = AdmissionExam(
        examType = ExamType.TIL_A,
        durationMinutes = 100,
        totalQuestions = 50,
        scoring = ExamScoring(correctPoints = 1f, wrongPenalty = 0.25f),
        subjects = listOf(
            ExamSubject("reading",      "Okuduğunu Anlama", 10, 10f / 50),
            ExamSubject("logic",        "Mantık",           10, 10f / 50),
            ExamSubject("art_history",  "Sanat Tarihi",     10, 10f / 50),
            ExamSubject("spatial",      "Uzaysal Düşünme",  10, 10f / 50),
            ExamSubject("math_physics", "Matematik & Fizik",10, 10f / 50)
        )
    )

    private val CENT_S = AdmissionExam(
        examType = ExamType.CENT_S,
        durationMinutes = 110,
        totalQuestions = 55,
        scoring = ExamScoring(correctPoints = 1f, wrongPenalty = 0.25f),
        subjects = listOf(
            ExamSubject("math",         "Matematik",     15, 15f / 55),
            ExamSubject("reading_data", "Okuma & Veri",  15, 15f / 55),
            ExamSubject("biology",      "Biyoloji",      10, 10f / 55),
            ExamSubject("chemistry",    "Kimya",         10, 10f / 55),
            ExamSubject("physics",      "Fizik",          5,  5f / 55)
        )
    )

    private val SAT = AdmissionExam(
        examType = ExamType.SAT,
        durationMinutes = 134,
        totalQuestions = 98,
        scoring = ExamScoring(correctPoints = 1f, wrongPenalty = 0f),
        subjects = listOf(
            ExamSubject("reading_writing", "Okuma & Yazma", 54, 54f / 98),
            ExamSubject("math",            "Matematik",     44, 44f / 98)
        )
    )

    private val TOLC_SU = AdmissionExam(
        examType = ExamType.TOLC_SU,
        durationMinutes = 85,
        totalQuestions = 50,
        scoring = ExamScoring(correctPoints = 1f, wrongPenalty = 0.25f),
        subjects = listOf(
            ExamSubject("logic",          "Mantık",              20, 20f / 50),
            ExamSubject("reading",        "Okuduğunu Anlama",    15, 15f / 50),
            ExamSubject("history",        "Tarih",               10, 10f / 50),
            ExamSubject("general_culture","Genel Kültür",         5,  5f / 50)
        )
    )

    private val TOLC_PSI = AdmissionExam(
        examType = ExamType.TOLC_PSI,
        durationMinutes = 90,
        totalQuestions = 50,
        scoring = ExamScoring(correctPoints = 1f, wrongPenalty = 0.25f),
        subjects = listOf(
            ExamSubject("logic",          "Mantık",              20, 20f / 50),
            ExamSubject("reading",        "Okuduğunu Anlama",    15, 15f / 50),
            ExamSubject("general_culture","Genel Kültür",        10, 10f / 50),
            ExamSubject("history",        "Tarih",                5,  5f / 50)
        )
    )

    private val ITALIAN_LANG = AdmissionExam(
        examType = ExamType.ITALIAN_LANG,
        durationMinutes = 60,
        totalQuestions = 40,
        scoring = ExamScoring(correctPoints = 1f, wrongPenalty = 0f),
        subjects = listOf(
            ExamSubject("grammar",    "Dilbilgisi",       12, 12f / 40),
            ExamSubject("vocabulary", "Kelime Bilgisi",   10, 10f / 40),
            ExamSubject("reading",    "Okuma & Anlama",   10, 10f / 40),
            ExamSubject("writing",    "Yazma",             8,  8f / 40)
        )
    )

    private val registry: Map<ExamType, AdmissionExam> = mapOf(
        ExamType.IMAT        to IMAT,
        ExamType.TIL_I       to TIL_I,
        ExamType.TIL_A       to TIL_A,
        ExamType.CENT_S      to CENT_S,
        ExamType.SAT         to SAT,
        ExamType.TOLC_SU     to TOLC_SU,
        ExamType.TOLC_PSI    to TOLC_PSI,
        ExamType.ITALIAN_LANG to ITALIAN_LANG,
        // Legacy TOLC_ entries — redirect to closest modern equivalent
        ExamType.TOLC_I      to TIL_I,
        ExamType.TOLC_A      to TIL_A,
        ExamType.TOLC_E      to CENT_S,
        ExamType.TOLC_B      to CENT_S,
        ExamType.UNKNOWN     to ITALIAN_LANG
    )

    fun get(examType: ExamType): AdmissionExam = registry[examType] ?: ITALIAN_LANG
}
