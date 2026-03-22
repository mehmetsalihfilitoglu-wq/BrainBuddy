package com.brainbuddy.app.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists startup audit text to [AUDIT_FILENAME] and keeps the latest snapshot for in-app UI.
 */
object StartupAuditRecorder {

    const val AUDIT_FILENAME = "brainbuddy_audit.txt"

    data class StartupReadyPayload(
        val auditSnapshot: StartupAuditSnapshot,
        val auditText: String,
        val auditPath: String?
    )

    @Volatile
    var lastAuditText: String = ""

    @Volatile
    var auditFileAbsolutePath: String? = null

    @Volatile
    var lastSnapshot: StartupAuditSnapshot? = null

    data class StartupPoolSnapshot(
        val total: Int,
        val active: Int,
        val inactive: Int,
        val candidateSampleSizeG6Mat: Int,
        val candidateSampleSizeG4Ing: Int,
        val candidateSampleSizeLgsMat: Int
    )

    data class StartupAuditSnapshot(
        val total: Int,
        val active: Int,
        val inactive: Int,
        val seedSkipped: Boolean,
        val insertedThisRun: Int,
        val quotaBefore: Int?,
        val quotaAfter: Int?,
        val quotaAdded: Int?,
        val candidateSampleG6Mat: Int,
        val candidateSampleG4Ing: Int,
        val candidateSampleLgsMat: Int,
        val gradeDistribution: Map<Int, Int>,
        val gradeSubjectLines: List<String>,
        val capturedAtEpochMs: Long,
        val seedDiagnostics: SeedDiagnosticsSnapshot? = null,
    )

    suspend fun computePoolSnapshot(context: Context): StartupPoolSnapshot? = withContext(Dispatchers.IO) {
        try {
            val dao = DatabaseProvider.get(context.applicationContext).questionDao()
            StartupPoolSnapshot(
                total = dao.countAll(),
                active = dao.countAllActive(),
                inactive = dao.countAllInactive(),
                candidateSampleSizeG6Mat = dao.getCandidatePoolByGradeSubject(6, "mat").size,
                candidateSampleSizeG4Ing = dao.getCandidatePoolByGradeSubject(4, "ing").size,
                candidateSampleSizeLgsMat = dao.getCandidatePoolByLgsSubject("mat").size
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Call only after [DbSeeder.seedIfNeeded] and [PoolQuotaEnforcer.enforceCoreQuotas] complete.
     * Recomputes all counts from DB (post-quota), writes [AUDIT_FILENAME] once for startup, and returns the published payload.
     */
    suspend fun finalizeStartupAudit(
        context: Context,
        quotaReport: PoolQuotaEnforcer.QuotaReport
    ): StartupReadyPayload = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val dao = DatabaseProvider.get(app).questionDao()
        val total = dao.countAll()
        val active = dao.countAllActive()
        val inactive = dao.countAllInactive()
        val candG6 = dao.getCandidatePoolByGradeSubject(6, "mat").size
        val candG4 = dao.getCandidatePoolByGradeSubject(4, "ing").size
        val candLgs = dao.getCandidatePoolByLgsSubject("mat").size
        val gradeMap = dao.getCountsGroupedByGrade().associate { it.grade to it.count }
        val gsLines = dao.getAllGroupedByGradeSubject().map { "${it.grade}-${it.subject}=${it.count}" }

        val snap = StartupAuditSnapshot(
            total = total,
            active = active,
            inactive = inactive,
            seedSkipped = DbSeeder.lastSeedSkipped,
            insertedThisRun = DbSeeder.lastInsertedThisRun,
            quotaBefore = quotaReport.totalRowCountBefore,
            quotaAfter = quotaReport.totalRowCountAfter,
            quotaAdded = quotaReport.totalRowsAdded,
            candidateSampleG6Mat = candG6,
            candidateSampleG4Ing = candG4,
            candidateSampleLgsMat = candLgs,
            gradeDistribution = gradeMap,
            gradeSubjectLines = gsLines,
            capturedAtEpochMs = System.currentTimeMillis(),
            seedDiagnostics = DbSeeder.getLastSeedDiagnosticsSnapshot(),
        )
        lastSnapshot = snap
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        val f = File(dir, AUDIT_FILENAME)
        auditFileAbsolutePath = f.absolutePath
        val text = formatAuditText(
            snap,
            auditFileAbsolutePath,
            startupStatusLine = "FINALIZED",
            omitPrimaryCountsFromBody = false,
        )
        lastAuditText = text
        f.writeText(text)
        StartupReadyPayload(auditSnapshot = snap, auditText = text, auditPath = auditFileAbsolutePath)
    }

    /**
     * Manual refresh: recomputes DB counts; quota lines only when last enforce report or prior snapshot has quota.
     */
    suspend fun buildLiveReport(context: Context): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val dao = DatabaseProvider.get(app).questionDao()
        val quota = PoolQuotaEnforcer.lastReport()
        val ls = lastSnapshot
        val qb = quota?.totalRowCountBefore ?: ls?.quotaBefore
        val qa = quota?.totalRowCountAfter ?: ls?.quotaAfter
        val qadd = quota?.totalRowsAdded ?: ls?.quotaAdded
        val haveQuota = qb != null && qa != null && qadd != null

        val snap = StartupAuditSnapshot(
            total = dao.countAll(),
            active = dao.countAllActive(),
            inactive = dao.countAllInactive(),
            seedSkipped = DbSeeder.lastSeedSkipped,
            insertedThisRun = DbSeeder.lastInsertedThisRun,
            quotaBefore = if (haveQuota) qb else null,
            quotaAfter = if (haveQuota) qa else null,
            quotaAdded = if (haveQuota) qadd else null,
            candidateSampleG6Mat = dao.getCandidatePoolByGradeSubject(6, "mat").size,
            candidateSampleG4Ing = dao.getCandidatePoolByGradeSubject(4, "ing").size,
            candidateSampleLgsMat = dao.getCandidatePoolByLgsSubject("mat").size,
            gradeDistribution = dao.getCountsGroupedByGrade().associate { it.grade to it.count },
            gradeSubjectLines = dao.getAllGroupedByGradeSubject().map { "${it.grade}-${it.subject}=${it.count}" },
            capturedAtEpochMs = System.currentTimeMillis(),
            seedDiagnostics = DbSeeder.getLastSeedDiagnosticsSnapshot(),
        )
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        val f = File(dir, AUDIT_FILENAME)
        auditFileAbsolutePath = f.absolutePath
        val text = formatAuditText(
            snap,
            auditFileAbsolutePath,
            startupStatusLine = "REFRESHED",
            includeQuotaLines = haveQuota,
            omitPrimaryCountsFromBody = false,
        )
        lastSnapshot = snap
        lastAuditText = text
        f.writeText(text)
        text
    }

    /**
     * @param omitPrimaryCountsFromBody When true, skips SECTION_A total/active/inactive lines (shown separately in UI).
     */
    fun formatAuditText(
        s: StartupAuditSnapshot,
        auditFilePath: String? = null,
        startupStatusLine: String? = null,
        includeQuotaLines: Boolean = true,
        omitPrimaryCountsFromBody: Boolean = false,
    ): String = buildString {
        appendLine("AppStartupAudit")
        if (startupStatusLine != null) {
            appendLine("startupStatus=$startupStatusLine")
        }
        appendLine()
        appendLine("SECTION_A_FINAL_DATABASE_COUNTS")
        if (!omitPrimaryCountsFromBody) {
            appendLine("TOTAL=${s.total}")
            appendLine("ACTIVE=${s.active}")
            appendLine("INACTIVE=${s.inactive}")
        }
        appendLine("candidateSample_g6_mat=${s.candidateSampleG6Mat}")
        appendLine("candidateSample_g4_ing=${s.candidateSampleG4Ing}")
        appendLine("candidateSample_lgs_mat=${s.candidateSampleLgsMat}")
        appendLine()
        appendLine("SECTION_DATABASE_DISTRIBUTIONS")
        appendLine("GradeDistribution:")
        s.gradeDistribution.entries.sortedBy { it.key }.forEach { (g, c) ->
            appendLine("grade$g=$c")
        }
        appendLine()
        appendLine("GradeSubjectDistribution:")
        s.gradeSubjectLines.forEach { appendLine(it) }
        appendLine()
        appendLine("SECTION_PIPELINE_META")
        appendLine("seedSkipped=${s.seedSkipped}")
        appendLine("insertedThisRun=${s.insertedThisRun}")
        if (includeQuotaLines) {
            appendLine("quotaBefore=${fmtInt(s.quotaBefore)}")
            appendLine("quotaAfter=${fmtInt(s.quotaAfter)}")
            appendLine("quotaAdded=${fmtInt(s.quotaAdded)}")
        } else {
            appendLine("quotaBefore=not available")
            appendLine("quotaAfter=not available")
            appendLine("quotaAdded=not available")
        }
        appendLine()
        appendLine("capturedAtEpochMs=${s.capturedAtEpochMs}")
        if (auditFilePath != null) {
            appendLine("auditFilePath=$auditFilePath")
        }
        appendLine()
        appendSeedDiagnosticsSection(this, s.seedDiagnostics)
    }

    /** Body text for the scroll area when primary counts are shown in a separate view above. */
    fun formatAuditBodyForDisplay(s: StartupAuditSnapshot, auditFilePath: String?, startupStatusLine: String): String =
        formatAuditText(
            s,
            auditFilePath,
            startupStatusLine = startupStatusLine,
            includeQuotaLines = s.quotaBefore != null && s.quotaAfter != null && s.quotaAdded != null,
            omitPrimaryCountsFromBody = true,
        )

    private fun fmtInt(n: Int?): String = if (n == null) "not available" else n.toString()

    private fun appendSeedDiagnosticsSection(sb: StringBuilder, d: SeedDiagnosticsSnapshot?) {
        sb.appendLine("SECTION_B_SEED_DIAGNOSTICS")
        when {
            d == null -> {
                sb.appendLine("Seed diagnostics not recorded for this run.")
            }
            !d.hasAnyRecordedValue() -> {
                sb.appendLine("No seed diagnostics captured in this run.")
            }
            else -> {
                sb.appendLine("loadedRootGeneral=${fmtInt(d.loadedRootGeneral)}")
                sb.appendLine("loadedPacks=${fmtInt(d.loadedPacks)}")
                sb.appendLine("loadedGradeBased=${fmtInt(d.loadedGradeBased)}")
                sb.appendLine("loadedLgsExam=${fmtInt(d.loadedLgsExam)}")
                sb.appendLine("loadedSynthetic=${fmtInt(d.loadedSynthetic)}")
                sb.appendLine("discoveredGradeBasedDirs=${fmtInt(d.discoveredGradeBasedDirs)}")
                sb.appendLine("discoveredGradeBasedJsonFiles=${fmtInt(d.discoveredGradeBasedJsonFiles)}")
                sb.appendLine("totalBeforeNormalize=${fmtInt(d.totalBeforeNormalize)}")
                sb.appendLine("totalAfterNormalize=${fmtInt(d.totalAfterNormalize)}")
                sb.appendLine("invalidGradeBeforeNormalize=${fmtInt(d.invalidGradeBeforeNormalize)}")
                sb.appendLine("invalidGradeAfterNormalize=${fmtInt(d.invalidGradeAfterNormalize)}")
                sb.appendLine("finalInserted=${fmtInt(d.finalInserted)}")
                sb.appendLine("normalizationApplied=${fmtBool(d.normalizationApplied)}")
                sb.appendLine("invalidAfterNormalize=${fmtInt(d.invalidAfterNormalize)}")
                sb.appendLine("dbCheckTotalRows=${fmtInt(d.dbCheckTotalRows)}")
                sb.appendLine("dbCheckInvalidRows=${fmtInt(d.dbCheckInvalidRows)}")
                sb.appendLine("dbCheckValidRows=${fmtInt(d.dbCheckValidRows)}")
                val samples = d.dbCheckSampleRows
                if (samples.isNullOrEmpty()) {
                    sb.appendLine("dbCheckSampleRows=not available")
                } else {
                    sb.appendLine("dbCheckSampleRows:")
                    samples.forEachIndexed { i, row ->
                        sb.appendLine("  ${i + 1}: $row")
                    }
                }
            }
        }
    }

    private fun fmtBool(b: Boolean?): String = when (b) {
        null -> "not available"
        true -> "yes"
        false -> "no"
    }
}
