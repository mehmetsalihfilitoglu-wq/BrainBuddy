package com.mioacademy.app.dailychallenge

/**
 * Human-readable Turkish labels for the internal section codes shown to students. Pure (no Android)
 * so it can be reused across the completion, home, and review screens and unit-tested. Never exposes
 * internal provenance/source codes — only the pedagogical section name.
 */
object DailyChallengeSectionLabels {

    private val labels = mapOf(
        "math" to "Matematik",
        "physics" to "Fizik",
        "physics_math" to "Fizik & Matematik",
        "biology" to "Biyoloji",
        "chemistry" to "Kimya",
        "logic" to "Mantık",
        "logic_reading" to "Mantık & Okuma",
        "reading" to "Okuma",
        "reading_data" to "Okuma & Veri",
        "basic_technical" to "Teknik Bilgi",
        "computer_science" to "Bilgisayar",
        "representation" to "Gösterim",
        "general_knowledge" to "Genel Kültür",
        "critical_thinking" to "Eleştirel Düşünme",
    )

    fun label(sectionCode: String): String =
        labels[sectionCode] ?: sectionCode.split('_')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
