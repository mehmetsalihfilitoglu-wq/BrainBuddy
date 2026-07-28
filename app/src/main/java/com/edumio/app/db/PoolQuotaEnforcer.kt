package com.edumio.app.db

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enforces minimum ACTIVE counts for core curriculum cells (grades 1..7 × five subjects).
 * Runs after seed; generates only per-cell deficits via [QuotaSyntheticQuestions].
 */
object PoolQuotaEnforcer {
    private const val TAG = "PoolQuotaEnforcer"

    /** Product minimum: active questions per (grade, core subject). */
    const val CORE_MIN_ACTIVE = 500

    val CORE_SUBJECTS = listOf("mat", "turkce", "fen", "sosyal", "ing")

    data class QuotaReport(
        /** Total question rows before quota inserts. */
        val totalRowCountBefore: Int,
        /** Total question rows after quota inserts. */
        val totalRowCountAfter: Int,
        /** Net rows added (after - before). */
        val totalRowsAdded: Int,
        /** Active counts before top-up: grade -> subject -> count */
        val activeBefore: Map<Pair<Int, String>, Int>,
        /** Deficit to 500 before top-up */
        val deficitBefore: Map<Pair<Int, String>, Int>,
        /** Human-readable root-cause notes per underfilled cell (before fill) */
        val causes: Map<Pair<Int, String>, String>,
        /** How many synthetic rows inserted per cell */
        val insertedPerCell: Map<Pair<Int, String>, Int>,
        /** Active counts after top-up */
        val activeAfter: Map<Pair<Int, String>, Int>,
        /** Deficit after (should be 0 for all core cells) */
        val deficitAfter: Map<Pair<Int, String>, Int>,
        val allCoreCellsSatisfied: Boolean
    ) {
        fun formatActiveTable(title: String, m: Map<Pair<Int, String>, Int>): String = buildString {
            appendLine(title)
            for (g in 1..7) {
                val parts = PoolQuotaEnforcer.CORE_SUBJECTS.map { sub -> "$sub=${m[g to sub] ?: 0}" }
                appendLine("grade $g: ${parts.joinToString(", ")}")
            }
        }

        fun formatDeficitTable(m: Map<Pair<Int, String>, Int>): String = buildString {
            appendLine("DEFICIT to ${PoolQuotaEnforcer.CORE_MIN_ACTIVE} (0 = OK)")
            for (g in 1..7) {
                val parts = PoolQuotaEnforcer.CORE_SUBJECTS.map { sub ->
                    val d = m[g to sub] ?: 0
                    "$sub=$d"
                }
                appendLine("grade $g: ${parts.joinToString(", ")}")
            }
        }

        fun formatCauses(): String = buildString {
            appendLine("ROOT CAUSE (underfilled cells before top-up)")
            val keys = (causes.keys + deficitBefore.filter { it.value > 0 }.keys).distinct().sortedWith(
                compareBy<Pair<Int, String>> { it.first }.thenBy { it.second }
            )
            for (k in keys) {
                val d = deficitBefore[k] ?: 0
                if (d <= 0 && causes[k] == null) continue
                appendLine("${k.first}/${k.second}: deficit=$d → ${causes[k] ?: "—"}")
            }
        }
    }

    @Volatile
    private var lastReport: QuotaReport? = null

    fun lastReport(): QuotaReport? = lastReport

    /**
     * Measures DB, diagnoses shortfalls, inserts synthetic rows only for exact deficits, re-measures.
     */
    suspend fun enforceCoreQuotas(context: Context): QuotaReport = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.get(context)
        val dao = db.questionDao()

        suspend fun snapshotActiveSuspend(): Map<Pair<Int, String>, Int> {
            val m = mutableMapOf<Pair<Int, String>, Int>()
            for (g in 1..7) {
                for (s in CORE_SUBJECTS) {
                    m[g to s] = dao.countActiveByGradeSubject(g, s)
                }
            }
            return m
        }

        val activeBefore = snapshotActiveSuspend()
        Log.i(TAG, buildString {
            appendLine("QUOTA_ACTIVE_BEFORE")
            for (g in 1..7) {
                append("grade $g: ")
                appendLine(CORE_SUBJECTS.joinToString(", ") { s -> "$s=${activeBefore[g to s] ?: 0}" })
            }
        }.trimEnd())

        val deficitBefore = mutableMapOf<Pair<Int, String>, Int>()
        for (g in 1..7) {
            for (s in CORE_SUBJECTS) {
                val a = activeBefore[g to s] ?: 0
                deficitBefore[g to s] = (CORE_MIN_ACTIVE - a).coerceAtLeast(0)
            }
        }

        val causes = mutableMapOf<Pair<Int, String>, String>()
        for (g in 1..7) {
            for (s in CORE_SUBJECTS) {
                val def = deficitBefore[g to s] ?: 0
                if (def <= 0) continue
                val total = dao.countByGradeSubject(g, s)
                val inactive = dao.countInactiveByGradeSubject(g, s)
                val lgsActive = dao.countActiveLgsByGradeSubject(g, s)
                causes[g to s] = diagnose(
                    grade = g,
                    subject = s,
                    active = activeBefore[g to s] ?: 0,
                    totalRows = total,
                    inactiveRows = inactive,
                    activeLgs = lgsActive,
                    deficit = def
                )
            }
        }

        val insertedPerCell = mutableMapOf<Pair<Int, String>, Int>()

        val beforeTotal = dao.countAll()
        db.withTransaction {
            for (g in 1..7) {
                for (s in CORE_SUBJECTS) {
                    val current = dao.countActiveByGradeSubject(g, s)
                    val deficit = (CORE_MIN_ACTIVE - current).coerceAtLeast(0)
                    if (deficit <= 0) continue
                    val batch = QuotaSyntheticQuestions.generate(g, s, deficit)
                    if (batch.isNotEmpty()) {
                        dao.insertAll(batch)
                        insertedPerCell[g to s] = batch.size
                        Log.i(TAG, "Top-up grade=$g subject=$s inserted=${batch.size} (deficit was $deficit)")
                    }
                }
            }
        }
        val afterTotal = dao.countAll()
        val addedTotal = (afterTotal - beforeTotal).coerceAtLeast(0)
        Log.i(TAG, "QuotaAudit: before=$beforeTotal after=$afterTotal added=$addedTotal")

        val activeAfter = snapshotActiveSuspend()
        val deficitAfter = mutableMapOf<Pair<Int, String>, Int>()
        for (g in 1..7) {
            for (s in CORE_SUBJECTS) {
                val a = activeAfter[g to s] ?: 0
                deficitAfter[g to s] = (CORE_MIN_ACTIVE - a).coerceAtLeast(0)
            }
        }

        val allOk = CORE_SUBJECTS.all { s ->
            (1..7).all { g -> (activeAfter[g to s] ?: 0) >= CORE_MIN_ACTIVE }
        }

        val report = QuotaReport(
            totalRowCountBefore = beforeTotal,
            totalRowCountAfter = afterTotal,
            totalRowsAdded = addedTotal,
            activeBefore = activeBefore,
            deficitBefore = deficitBefore.toMap(),
            causes = causes.toMap(),
            insertedPerCell = insertedPerCell.toMap(),
            activeAfter = activeAfter,
            deficitAfter = deficitAfter.toMap(),
            allCoreCellsSatisfied = allOk
        )
        lastReport = report

        Log.i(TAG, report.formatActiveTable("ACTIVE_AFTER", report.activeAfter))
        Log.i(TAG, "allCoreCellsSatisfied=${report.allCoreCellsSatisfied}")
        report
    }

    private fun diagnose(
        grade: Int,
        subject: String,
        active: Int,
        totalRows: Int,
        inactiveRows: Int,
        activeLgs: Int,
        deficit: Int
    ): String {
        val parts = mutableListOf<String>()
        if (totalRows < CORE_MIN_ACTIVE) {
            parts += "fewer_than_${CORE_MIN_ACTIVE}_rows_in_DB(total=$totalRows); likely_insufficient_assets_or_dedup_for_cell"
        }
        if (inactiveRows > 0) {
            parts += "inactive=$inactiveRows (often QuestionQualityGate or import flags)"
        }
        if (activeLgs > 0 && grade == 7) {
            parts += "active_LGS_in_cell=$activeLgs (mixes with GENERAL in same grade/subject bucket)"
        }
        if (parts.isEmpty()) {
            parts += "below_target_active=$active vs $CORE_MIN_ACTIVE; synthetic_top_up_applied"
        }
        return parts.joinToString("; ")
    }
}
