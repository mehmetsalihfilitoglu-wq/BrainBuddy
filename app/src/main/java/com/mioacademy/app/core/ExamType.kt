package com.mioacademy.app.core

enum class ExamType(val code: String, val fullNameIt: String) {
    IMAT("IMAT", "International Medical Admissions Test"),
    TOLC_I("TOLC-I", "Test Online CISIA — Ingegneria"),
    TOLC_E("TOLC-E", "Test Online CISIA — Economia"),
    TOLC_B("TOLC-B", "Test Online CISIA — Biologia e Farmacia"),
    TOLC_A("TOLC-A", "Test Online CISIA — Architettura"),
    TOLC_SU("TOLC-SU", "Test Online CISIA — Scienze Umane"),
    ITALIAN_LANG("İtalyanca", "Preparazione Linguistica"),
    UNKNOWN("Genel", "Preparazione Generale");

    /** Subject codes included in this exam, ordered by weight (heaviest first). */
    val subjectCodes: List<String> get() = when (this) {
        IMAT     -> listOf("biology", "chemistry", "physics", "math", "logic", "english")
        TOLC_I   -> listOf("math", "physics", "chemistry", "logic")
        TOLC_E   -> listOf("math", "logic", "english", "reading")
        TOLC_B   -> listOf("biology", "chemistry", "math", "logic")
        TOLC_A   -> listOf("math", "spatial", "art_history", "logic")
        TOLC_SU  -> listOf("logic", "reading", "history", "general_culture")
        ITALIAN_LANG -> listOf("grammar", "vocabulary", "reading", "writing")
        UNKNOWN  -> listOf("math", "logic", "english")
    }

    /** Weights per subject code for the Exam Readiness Score (must sum to 1.0). */
    val subjectWeights: Map<String, Float> get() = when (this) {
        IMAT     -> mapOf("biology" to .23f, "chemistry" to .15f, "physics" to .15f,
                          "math" to .15f, "logic" to .17f, "english" to .15f)
        TOLC_I   -> mapOf("math" to .40f, "physics" to .30f, "chemistry" to .20f, "logic" to .10f)
        TOLC_E   -> mapOf("math" to .40f, "logic" to .30f, "english" to .20f, "reading" to .10f)
        TOLC_B   -> mapOf("biology" to .40f, "chemistry" to .30f, "math" to .20f, "logic" to .10f)
        TOLC_A   -> mapOf("math" to .40f, "spatial" to .30f, "art_history" to .20f, "logic" to .10f)
        TOLC_SU  -> mapOf("logic" to .35f, "reading" to .30f, "history" to .20f, "general_culture" to .15f)
        ITALIAN_LANG -> mapOf("grammar" to .30f, "vocabulary" to .25f, "reading" to .25f, "writing" to .20f)
        UNKNOWN  -> mapOf("math" to .40f, "logic" to .35f, "english" to .25f)
    }
}
