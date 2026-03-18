package com.brainbuddy.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.BuildConfig
import com.brainbuddy.app.db.DbSeeder

/**
 * DEBUG-ONLY screen that shows the real runtime DB seeding result.
 *
 * This activity is launched automatically from BrainBuddyApp *after* DbSeeder.seedIfNeeded(...)
 * completes (debug builds only). It does not affect release builds or normal UX.
 */
class DebugSeedStatusActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }

        val total = intent.getIntExtra(EXTRA_TOTAL, -1)
        val active = intent.getIntExtra(EXTRA_ACTIVE, -1)
        val g6Mat = intent.getIntExtra(EXTRA_G6_MAT, -1)
        val g4Ing = intent.getIntExtra(EXTRA_G4_ING, -1)
        val lgsMat = intent.getIntExtra(EXTRA_LGS_MAT, -1)

        val tv = TextView(this).apply {
            val textLines = buildString {
                appendLine("RUNTIME_SEED_DB")
                appendLine("total=$total")
                appendLine("active=$active")
                appendLine("g6_mat=$g6Mat")
                appendLine("g4_ing=$g4Ing")
                appendLine("lgs_mat=$lgsMat")

                val diag = DbSeeder.debugLastSeedDiagnostics()
                appendLine()
                appendLine("SEED_SOURCE_COUNTS_AND_NORMALIZE_DEBUG")
                appendLine("loaded_root_general=${diag?.loaded_root_general ?: -1}")
                appendLine("loaded_packs=${diag?.loaded_packs ?: -1}")
                appendLine("loaded_grade_based=${diag?.loaded_grade_based ?: -1}")
                appendLine("loaded_lgs_exam=${diag?.loaded_lgs_exam ?: -1}")
                appendLine("loaded_synthetic=${diag?.loaded_synthetic ?: -1}")
                appendLine("discovered_grade_based_dirs=${diag?.discovered_grade_based_dirs ?: -1}")
                appendLine("discovered_grade_based_json_files=${diag?.discovered_grade_based_json_files ?: -1}")
                appendLine("total_before_normalize=${diag?.total_before_normalize ?: -1}")
                appendLine("total_after_normalize=${diag?.total_after_normalize ?: -1}")
                appendLine("invalid_grade_before_normalize=${diag?.invalid_grade_before_normalize ?: -1}")
                appendLine("invalid_grade_after_normalize=${diag?.invalid_grade_after_normalize ?: -1}")
                appendLine("final_inserted=${diag?.final_inserted ?: -1}")
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
        private const val EXTRA_G6_MAT = "g6_mat"
        private const val EXTRA_G4_ING = "g4_ing"
        private const val EXTRA_LGS_MAT = "lgs_mat"

        fun launch(context: Context, total: Int, active: Int, g6Mat: Int, g4Ing: Int, lgsMat: Int) {
            if (!BuildConfig.DEBUG) return
            val i = Intent(context, DebugSeedStatusActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_TOTAL, total)
                putExtra(EXTRA_ACTIVE, active)
                putExtra(EXTRA_G6_MAT, g6Mat)
                putExtra(EXTRA_G4_ING, g4Ing)
                putExtra(EXTRA_LGS_MAT, lgsMat)
            }
            context.startActivity(i)
        }
    }
}

