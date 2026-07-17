package com.edumio.app.core

enum class ExamType(val code: String, val fullNameIt: String) {
    IMAT("IMAT", "International Medical Admissions Test"),
    TIL_I("TIL-I", "Test di Ingegneria — Logica e Scienze"),
    TIL_A("TIL-A", "Test di Architettura — Arte e Spazio"),
    CENT_S("CEnT-S", "Test di Scienze ed Economia"),
    SAT("SAT", "Scholastic Assessment Test"),
    TOLC_PSI("TOLC-PSI", "Test Online CISIA — Psicologia"),
    TOLC_SU("TOLC-SU", "Test Online CISIA — Scienze Umane"),
    TOLC_I("TOLC-I", "Test Online CISIA — Ingegneria"),
    TOLC_E("TOLC-E", "Test Online CISIA — Economia"),
    TOLC_B("TOLC-B", "Test Online CISIA — Biologia e Farmacia"),
    TOLC_A("TOLC-A", "Test Online CISIA — Architettura"),
    ITALIAN_LANG("İtalyanca", "Preparazione Linguistica"),
    UNKNOWN("Genel", "Preparazione Generale");

    /** Subject IDs for this exam, ordered by weight (heaviest first). */
    val subjectCodes: List<String> get() = when (this) {
        IMAT         -> listOf("biology", "chemistry", "physics_math", "logic")
        TIL_I        -> listOf("math", "logic_reading", "physics", "basic_technical")
        TIL_A        -> listOf("reading", "logic", "art_history", "spatial", "math_physics")
        CENT_S       -> listOf("math", "reading_data", "biology", "chemistry", "physics")
        SAT          -> listOf("reading_writing", "math")
        TOLC_PSI     -> listOf("logic", "reading", "general_culture", "history")
        TOLC_SU      -> listOf("logic", "reading", "history", "general_culture")
        TOLC_I       -> listOf("math", "physics", "chemistry", "logic")
        TOLC_E       -> listOf("math", "logic", "english", "reading")
        TOLC_B       -> listOf("biology", "chemistry", "math", "logic")
        TOLC_A       -> listOf("math", "spatial", "art_history", "logic")
        ITALIAN_LANG -> listOf("grammar", "vocabulary", "reading", "writing")
        UNKNOWN      -> listOf("math", "logic", "english")
    }

    /** Subject weights for the exam readiness score (values sum to 1.0). */
    val subjectWeights: Map<String, Float> get() = when (this) {
        IMAT         -> mapOf("biology" to .38f, "chemistry" to .25f, "physics_math" to .22f, "logic" to .15f)
        TIL_I        -> mapOf("math" to .38f, "logic_reading" to .24f, "physics" to .24f, "basic_technical" to .14f)
        TIL_A        -> mapOf("reading" to .20f, "logic" to .20f, "art_history" to .20f, "spatial" to .20f, "math_physics" to .20f)
        CENT_S       -> mapOf("math" to .27f, "reading_data" to .27f, "biology" to .18f, "chemistry" to .18f, "physics" to .09f)
        SAT          -> mapOf("reading_writing" to .55f, "math" to .45f)
        TOLC_PSI     -> mapOf("logic" to .40f, "reading" to .30f, "general_culture" to .20f, "history" to .10f)
        TOLC_SU      -> mapOf("logic" to .35f, "reading" to .30f, "history" to .20f, "general_culture" to .15f)
        TOLC_I       -> mapOf("math" to .40f, "physics" to .30f, "chemistry" to .20f, "logic" to .10f)
        TOLC_E       -> mapOf("math" to .40f, "logic" to .30f, "english" to .20f, "reading" to .10f)
        TOLC_B       -> mapOf("biology" to .40f, "chemistry" to .30f, "math" to .20f, "logic" to .10f)
        TOLC_A       -> mapOf("math" to .40f, "spatial" to .30f, "art_history" to .20f, "logic" to .10f)
        ITALIAN_LANG -> mapOf("grammar" to .30f, "vocabulary" to .25f, "reading" to .25f, "writing" to .20f)
        UNKNOWN      -> mapOf("math" to .40f, "logic" to .35f, "english" to .25f)
    }
}
