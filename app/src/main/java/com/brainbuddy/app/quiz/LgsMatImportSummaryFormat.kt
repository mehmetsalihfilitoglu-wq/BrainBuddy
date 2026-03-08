package com.brainbuddy.app.quiz

/** Result of importing only math LGS packs from assets/lgs_import/mat/. */
data class LgsMatImportSummary(
    val importedCount: Int,
    val skippedDuplicateCount: Int,
    val deactivatedLowQualityCount: Int,
    val parseErrorCount: Int,
    val validationRejectedCount: Int = 0,
    val activeMatCount: Int,
    val inactiveLowQualityMatCount: Int,
    val matCountsByDifficulty: Map<Int, Int>,
    val matCountsByQuestionType: Map<String, Int>
)

/**
 * Formats [LgsMatImportSummary] for display in the MAT import result dialog.
 */
fun formatLgsMatSummaryText(s: LgsMatImportSummary): String {
    val diffStr = s.matCountsByDifficulty.entries.sortedBy { it.key }.joinToString(" ") { e -> "diff" + e.key + "=" + e.value }
    val typeStr = s.matCountsByQuestionType.entries.joinToString(" ") { e -> e.key + "=" + e.value }
    return listOf(
        "MAT LGS Import tamamlandı",
        "  Eklenen: ${s.importedCount}",
        "  Tekrar atlandı: ${s.skippedDuplicateCount}",
        if (s.deactivatedLowQualityCount > 0) "  Düşük kalite (pasif): ${s.deactivatedLowQualityCount}" else null,
        "  Parse hatası: ${s.parseErrorCount}",
        if (s.validationRejectedCount > 0) "  Doğrulama reddedildi: ${s.validationRejectedCount}" else null,
        "  Aktif MAT LGS: ${s.activeMatCount}",
        "  Pasif MAT (düşük kalite): ${s.inactiveLowQualityMatCount}",
        "  Zorluk: $diffStr",
        "  questionType: $typeStr"
    ).filterNotNull().joinToString("\n")
}
