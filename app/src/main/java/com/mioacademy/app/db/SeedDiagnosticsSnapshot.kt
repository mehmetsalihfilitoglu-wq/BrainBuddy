package com.mioacademy.app.db

/**
 * Optional seed-run metrics. Every field is null unless it was actually computed in that run.
 * Never use sentinel values like -1 — use null for "not recorded".
 */
data class SeedDiagnosticsSnapshot(
    val loadedRootGeneral: Int? = null,
    val loadedPacks: Int? = null,
    val loadedGradeBased: Int? = null,
    val loadedLgsExam: Int? = null,
    val loadedSynthetic: Int? = null,
    val discoveredGradeBasedDirs: Int? = null,
    val discoveredGradeBasedJsonFiles: Int? = null,
    val totalBeforeNormalize: Int? = null,
    val totalAfterNormalize: Int? = null,
    val invalidGradeBeforeNormalize: Int? = null,
    val invalidGradeAfterNormalize: Int? = null,
    val finalInserted: Int? = null,
    val normalizationApplied: Boolean? = null,
    val invalidAfterNormalize: Int? = null,
    val dbCheckTotalRows: Int? = null,
    val dbCheckInvalidRows: Int? = null,
    val dbCheckValidRows: Int? = null,
    val dbCheckSampleRows: List<String>? = null,
) {
    /** True if at least one diagnostic was recorded (non-null). */
    fun hasAnyRecordedValue(): Boolean =
        loadedRootGeneral != null ||
            loadedPacks != null ||
            loadedGradeBased != null ||
            loadedLgsExam != null ||
            loadedSynthetic != null ||
            discoveredGradeBasedDirs != null ||
            discoveredGradeBasedJsonFiles != null ||
            totalBeforeNormalize != null ||
            totalAfterNormalize != null ||
            invalidGradeBeforeNormalize != null ||
            invalidGradeAfterNormalize != null ||
            finalInserted != null ||
            normalizationApplied != null ||
            invalidAfterNormalize != null ||
            dbCheckTotalRows != null ||
            dbCheckInvalidRows != null ||
            dbCheckValidRows != null ||
            !dbCheckSampleRows.isNullOrEmpty()
}
