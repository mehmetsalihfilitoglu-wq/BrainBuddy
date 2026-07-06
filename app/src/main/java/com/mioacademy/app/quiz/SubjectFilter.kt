package com.mioacademy.app.quiz

/**
 * Maps a subject / topic display name to the value expected by
 * [QuizActivity.EXTRA_SUBJECT_FILTER]. Returns null → launch a mixed quiz.
 *
 * Single source of truth so Home's "next step" and StudyHub's subject cards
 * always target the same underlying question pool.
 */
object SubjectFilter {
    fun forName(subject: String): String? = when {
        subject == "Matematik" || subject.startsWith("Matematik") -> "Matematik"
        subject.contains("Matematik") -> "Matematik"
        subject == "İngilizce" -> "İngilizce"
        subject == "Biyoloji" || subject.startsWith("Biyoloji") -> "Biyoloji"
        subject == "Kimya" || subject.startsWith("Kimya") -> "Kimya"
        subject == "Fizik" || subject.startsWith("Fizik") -> "Fizik"
        subject.startsWith("Okuma") || subject.startsWith("Okudu") ||
            subject == "Mantık" || subject.startsWith("Mantık") -> "Türkçe"
        subject.contains("Tarih") -> "İnkılap Tarihi"
        else -> null
    }
}
