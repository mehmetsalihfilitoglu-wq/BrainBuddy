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
        val quotaBefore: Int,
        val quotaAfter: Int,
        val quotaAdded: Int,
        val candidateSampleG6Mat: Int,
        val candidateSampleG4Ing: Int,
        val candidateSampleLgsMat: Int,
        val gradeDistribution: Map<Int, Int>,
        val gradeSubjectLines: List<String>,
        val capturedAtEpochMs: Long
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

    suspend fun captureAfterStartup(
        context: Context,
        pool: StartupPoolSnapshot?,
        quotaReport: PoolQuotaEnforcer.QuotaReport
    ) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val dao = DatabaseProvider.get(app).questionDao()
        val total = pool?.total ?: dao.countAll()
        val active = pool?.active ?: dao.countAllActive()
        val inactive = pool?.inactive ?: dao.countAllInactive()
        val candG6 = pool?.candidateSampleSizeG6Mat ?: dao.getCandidatePoolByGradeSubject(6, "mat").size
        val candG4 = pool?.candidateSampleSizeG4Ing ?: dao.getCandidatePoolByGradeSubject(4, "ing").size
        val candLgs = pool?.candidateSampleSizeLgsMat ?: dao.getCandidatePoolByLgsSubject("mat").size

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
            capturedAtEpochMs = System.currentTimeMillis()
        )
        lastSnapshot = snap
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        val f = File(dir, AUDIT_FILENAME)
        auditFileAbsolutePath = f.absolutePath
        val text = formatAuditText(snap, auditFileAbsolutePath)
        lastAuditText = text
        f.writeText(text)
    }

    suspend fun buildLiveReport(context: Context): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val dao = DatabaseProvider.get(app).questionDao()
        val quota = PoolQuotaEnforcer.lastReport()
        val snap = StartupAuditSnapshot(
            total = dao.countAll(),
            active = dao.countAllActive(),
            inactive = dao.countAllInactive(),
            seedSkipped = DbSeeder.lastSeedSkipped,
            insertedThisRun = DbSeeder.lastInsertedThisRun,
            quotaBefore = quota?.totalRowCountBefore ?: -1,
            quotaAfter = quota?.totalRowCountAfter ?: -1,
            quotaAdded = quota?.totalRowsAdded ?: -1,
            candidateSampleG6Mat = dao.getCandidatePoolByGradeSubject(6, "mat").size,
            candidateSampleG4Ing = dao.getCandidatePoolByGradeSubject(4, "ing").size,
            candidateSampleLgsMat = dao.getCandidatePoolByLgsSubject("mat").size,
            gradeDistribution = dao.getCountsGroupedByGrade().associate { it.grade to it.count },
            gradeSubjectLines = dao.getAllGroupedByGradeSubject().map { "${it.grade}-${it.subject}=${it.count}" },
            capturedAtEpochMs = System.currentTimeMillis()
        )
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        val f = File(dir, AUDIT_FILENAME)
        auditFileAbsolutePath = f.absolutePath
        val text = formatAuditText(snap, auditFileAbsolutePath)
        lastSnapshot = snap
        lastAuditText = text
        f.writeText(text)
        text
    }

    fun formatAuditText(s: StartupAuditSnapshot, auditFilePath: String? = null): String = buildString {
        appendLine("AppStartupAudit")
        appendLine("total=${s.total}")
        appendLine("active=${s.active}")
        appendLine("inactive=${s.inactive}")
        appendLine("seedSkipped=${s.seedSkipped}")
        appendLine("insertedThisRun=${s.insertedThisRun}")
        appendLine("quotaBefore=${s.quotaBefore}")
        appendLine("quotaAfter=${s.quotaAfter}")
        appendLine("quotaAdded=${s.quotaAdded}")
        appendLine()
        appendLine("CandidateSampleSizes (LIMIT queries; not full DB)")
        appendLine("candidateSample_g6_mat=${s.candidateSampleG6Mat}")
        appendLine("candidateSample_g4_ing=${s.candidateSampleG4Ing}")
        appendLine("candidateSample_lgs_mat=${s.candidateSampleLgsMat}")
        appendLine()
        appendLine("GradeDistribution:")
        s.gradeDistribution.entries.sortedBy { it.key }.forEach { (g, c) ->
            appendLine("grade$g=$c")
        }
        appendLine()
        appendLine("GradeSubjectDistribution:")
        s.gradeSubjectLines.forEach { appendLine(it) }
        appendLine()
        appendLine("capturedAtEpochMs=${s.capturedAtEpochMs}")
        if (auditFilePath != null) {
            appendLine()
            appendLine("auditFilePath=$auditFilePath")
        }
    }
}
