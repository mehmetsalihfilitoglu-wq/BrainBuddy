package com.edumio.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.edumio.app.BuildConfig
import com.edumio.app.db.DbSeeder

/**
 * DEBUG-ONLY screen that shows the real runtime DB seeding result.
 *
 * This activity is launched automatically from EDUmioApp *after* DbSeeder.seedIfNeeded(...)
 * completes (debug builds only). It does not affect release builds or normal UX.
 */
class DebugSeedStatusActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }

        val total = intent.getIntExtra(EXTRA_TOTAL, Int.MIN_VALUE)
        val active = intent.getIntExtra(EXTRA_ACTIVE, Int.MIN_VALUE)
        val inactive = intent.getIntExtra(EXTRA_INACTIVE, Int.MIN_VALUE)
        val candidateSampleSizeG6Mat = intent.getIntExtra(EXTRA_CAND_SAMPLE_G6_MAT, Int.MIN_VALUE)
        val candidateSampleSizeG4Ing = intent.getIntExtra(EXTRA_CAND_SAMPLE_G4_ING, Int.MIN_VALUE)
        val candidateSampleSizeLgsMat = intent.getIntExtra(EXTRA_CAND_SAMPLE_LGS_MAT, Int.MIN_VALUE)

        fun ni(n: Int) = if (n == Int.MIN_VALUE) "not available" else n.toString()

        val tv = TextView(this).apply {
            val textLines = buildString {
                appendLine("RUNTIME_SEED_DB")
                appendLine("total=${ni(total)}")
                appendLine("active=${ni(active)}")
                appendLine("inactive=${ni(inactive)}")
                appendLine("candidateSampleSize_g6_mat=${ni(candidateSampleSizeG6Mat)}")
                appendLine("candidateSampleSize_g4_ing=${ni(candidateSampleSizeG4Ing)}")
                appendLine("candidateSampleSize_lgs_mat=${ni(candidateSampleSizeLgsMat)}")

                val diag = DbSeeder.getLastSeedDiagnosticsSnapshot()
                appendLine()
                appendLine("SEED_SOURCE_COUNTS_AND_NORMALIZE_DEBUG")
                when {
                    diag == null -> appendLine("Seed diagnostics not recorded for this run.")
                    !diag.hasAnyRecordedValue() -> appendLine("No seed diagnostics captured in this run.")
                    else -> {
                        fun nix(x: Int?) = x?.toString() ?: "not available"
                        fun nb(b: Boolean?) = when (b) {
                            null -> "not available"
                            true -> "yes"
                            false -> "no"
                        }
                        appendLine("loaded_root_general=${nix(diag.loadedRootGeneral)}")
                        appendLine("loaded_packs=${nix(diag.loadedPacks)}")
                        appendLine("loaded_grade_based=${nix(diag.loadedGradeBased)}")
                        appendLine("loaded_lgs_exam=${nix(diag.loadedLgsExam)}")
                        appendLine("loaded_synthetic=${nix(diag.loadedSynthetic)}")
                        appendLine("discovered_grade_based_dirs=${nix(diag.discoveredGradeBasedDirs)}")
                        appendLine("discovered_grade_based_json_files=${nix(diag.discoveredGradeBasedJsonFiles)}")
                        appendLine("total_before_normalize=${nix(diag.totalBeforeNormalize)}")
                        appendLine("total_after_normalize=${nix(diag.totalAfterNormalize)}")
                        appendLine("invalid_grade_before_normalize=${nix(diag.invalidGradeBeforeNormalize)}")
                        appendLine("invalid_grade_after_normalize=${nix(diag.invalidGradeAfterNormalize)}")
                        appendLine("normalization_applied=${nb(diag.normalizationApplied)}")
                        appendLine("invalid_after_normalize=${nix(diag.invalidAfterNormalize)}")
                        appendLine("final_inserted=${nix(diag.finalInserted)}")
                        appendLine()
                        appendLine("DB_CHECK")
                        appendLine("dbcheck_total_rows=${nix(diag.dbCheckTotalRows)}")
                        appendLine("dbcheck_invalid_rows=${nix(diag.dbCheckInvalidRows)}")
                        appendLine("dbcheck_valid_rows=${nix(diag.dbCheckValidRows)}")
                        val samples = diag.dbCheckSampleRows
                        if (samples.isNullOrEmpty()) {
                            appendLine("dbcheck_sample_rows=not available")
                        } else {
                            appendLine("dbcheck_sample_rows:")
                            samples.forEachIndexed { i, row ->
                                appendLine("  ${i + 1}: $row")
                            }
                        }
                    }
                }
            }
            text = textLines
            textSize = 18f
            setPadding(48, 48, 48, 48)
        }
        val scroll = ScrollView(this).apply { addView(tv) }
        setContentView(scroll)
    }

    companion object {
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_ACTIVE = "active"
        private const val EXTRA_INACTIVE = "inactive"
        private const val EXTRA_CAND_SAMPLE_G6_MAT = "candidateSampleSize_g6_mat"
        private const val EXTRA_CAND_SAMPLE_G4_ING = "candidateSampleSize_g4_ing"
        private const val EXTRA_CAND_SAMPLE_LGS_MAT = "candidateSampleSize_lgs_mat"

        fun launch(
            context: Context,
            total: Int,
            active: Int,
            inactive: Int,
            candidateSampleSizeG6Mat: Int,
            candidateSampleSizeG4Ing: Int,
            candidateSampleSizeLgsMat: Int
        ) {
            if (!BuildConfig.DEBUG) return
            val i = Intent(context, DebugSeedStatusActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_TOTAL, total)
                putExtra(EXTRA_ACTIVE, active)
                putExtra(EXTRA_INACTIVE, inactive)
                putExtra(EXTRA_CAND_SAMPLE_G6_MAT, candidateSampleSizeG6Mat)
                putExtra(EXTRA_CAND_SAMPLE_G4_ING, candidateSampleSizeG4Ing)
                putExtra(EXTRA_CAND_SAMPLE_LGS_MAT, candidateSampleSizeLgsMat)
            }
            context.startActivity(i)
        }
    }
}
