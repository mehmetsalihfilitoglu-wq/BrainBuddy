package com.brainbuddy.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.BuildConfig

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

