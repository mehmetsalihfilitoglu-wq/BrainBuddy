package com.mioacademy.app.quiz

/**
 * Blueprint-based quiz planner.
 * Defines explicit targets for subject counts, difficulty, and question type mix.
 */
data class QuizBlueprint(
    val mode: String,
    val totalQuestionCount: Int,
    val subjectTargets: Map<Subject, Int>,
    val difficultyTargets: Map<QuizDifficulty, Int> = emptyMap(),
    val questionTypeTargetsBySubject: Map<Subject, Map<String, Int>> = emptyMap()
) {
    /** Returns slots: list of (Subject, preferredBlueprintType?) for filling in order. */
    fun slots(): List<Pair<Subject, String?>> {
        val result = mutableListOf<Pair<Subject, String?>>()
        for ((subj, count) in subjectTargets) {
            val typeTargets = questionTypeTargetsBySubject[subj] ?: emptyMap()
            val typeSlots = typeTargets.flatMap { (t, c) -> List(c) { t } }
            var typeIdx = 0
            for (i in 0 until count) {
                val preferred = typeSlots.getOrNull(typeIdx++) ?: typeSlots.firstOrNull()
                result.add(subj to preferred)
            }
        }
        return result
    }
}

/**
 * Maps blueprint question type names to DB/stored type values (LgsQualityRules or QuestionDiversity).
 * Used to prefer candidates whose type matches the blueprint slot.
 */
/** Fixed LGS mini test blueprint: total 20, MAT=5, TURKCE=5, FEN=4, INKILAP=2, DIN=2, ING=2. */
val LGS_MINI_BLUEPRINT = QuizBlueprint(
    mode = "LGS_MINI",
    totalQuestionCount = 20,
    subjectTargets = mapOf(
        Subject.MAT to 5,
        Subject.TURKCE to 5,
        Subject.FEN to 4,
        Subject.INKILAP to 2,
        Subject.DIN to 2,
        Subject.ING to 2
    ),
    difficultyTargets = emptyMap(),
    questionTypeTargetsBySubject = mapOf(
        Subject.TURKCE to mapOf(
            "paragraf" to 2,
            "sozcuk_or_cumle" to 1,
            "gorsel_yorum" to 1,
            "mantik" to 1
        ),
        Subject.MAT to mapOf(
            "yeni_nesil_problem" to 2,
            "grafik_or_tablo" to 1,
            "mantik" to 1,
            "geometri_yorum" to 1
        ),
        Subject.FEN to mapOf(
            "deney_yorum" to 1,
            "grafik_or_tablo" to 1,
            "gunluk_hayat" to 1,
            "kavram_yorum" to 1
        ),
        Subject.INKILAP to mapOf(
            "metin_yorum" to 1,
            "neden_sonuc_or_kronoloji" to 1
        ),
        Subject.DIN to mapOf(
            "ayet_or_metin_yorum" to 1,
            "kavram_or_cikarim" to 1
        ),
        Subject.ING to mapOf(
            "reading" to 1,
            "dialogue_or_visual" to 1
        )
    )
)

object BlueprintTypeMapper {
    /** DB types that satisfy each blueprint type (LGS entities use LgsQualityRules types). */
    private val blueprintToDbTypes: Map<String, Set<String>> = mapOf(
        // TURKCE
        "paragraf" to setOf("paragraph", "long_context"),
        "sozcuk_or_cumle" to setOf("short_item"),
        "gorsel_yorum" to setOf("interpretation"),
        "mantik" to setOf("reasoning"),
        // MAT
        "yeni_nesil_problem" to setOf("context_problem"),
        "grafik_or_tablo" to setOf("interpretation"),
        "geometri_yorum" to setOf("interpretation", "GEOMETRY", "TABLE_GRAPH"),
        // FEN
        "deney_yorum" to setOf("interpretation", "EXPERIMENT"),
        "gunluk_hayat" to setOf("context_problem", "reasoning"),
        "kavram_yorum" to setOf("reasoning", "long_context"),
        // INKILAP
        "metin_yorum" to setOf("paragraph", "interpretation"),
        "neden_sonuc_or_kronoloji" to setOf("reasoning", "CAUSE_EFFECT"),
        // DIN
        "ayet_or_metin_yorum" to setOf("paragraph", "interpretation"),
        "kavram_or_cikarim" to setOf("reasoning", "COMMENTARY"),
        // ING
        "reading" to setOf("paragraph", "long_context", "READING"),
        "dialogue_or_visual" to setOf("interpretation", "DIALOGUE", "short_item")
    )

    fun dbTypesMatchBlueprint(blueprintType: String?, dbType: String): Boolean {
        if (blueprintType == null) return true
        val allowed = blueprintToDbTypes[blueprintType] ?: return true
        return dbType in allowed
    }
}
