package com.brainbuddy.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brainbuddy.app.BuildConfig
import com.brainbuddy.app.R
import com.brainbuddy.app.core.GradePrefs
import com.brainbuddy.app.core.LevelMode
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.QuizPrefs
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.DbSeeder
import com.brainbuddy.app.db.RoomQuizDataStore
import com.brainbuddy.app.quiz.QuestionPackImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pool Status (Admin/Debug) – soru havuzu durumu.
 * Fix butonları sadece DEBUG build'de görünür.
 */
class PoolStatusActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, PoolStatusActivity::class.java)) return

        setContentView(R.layout.activity_pool_status)

        // Show latest SEED_AUDIT immediately on screen (no DB / renderStatus dependency).
        val tvSeedAudit = findViewById<TextView>(R.id.tvSeedAudit)
        val audit = DbSeeder.getLastSeedAudit()
        tvSeedAudit.text = audit ?: "NO AUDIT FOUND"
        tvSeedAudit.visibility = View.VISIBLE

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        updatePoolStatusTitle()
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val layoutDebugFix = findViewById<View>(R.id.layoutDebugFixButtons)
        layoutDebugFix.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE

        setupFixButtons()

        // Brute-force DB summary (bypass old summary builder completely).
        val tvSummary = findViewById<TextView>(R.id.tvPoolStatus)
        lifecycleScope.launch {
            val text = try {
                val (total, active, invalid) = withContext(Dispatchers.IO) {
                    val db = DatabaseProvider.get(this@PoolStatusActivity)
                    val dao = db.questionDao()
                    Triple(dao.countAll(), dao.countAllActive(), dao.countInvalidGrades())
                }
                seedDebugBlock() + "\n\n" +
                "TOTAL / ACTIVE\n" +
                    "$total / $active\n\n" +
                    "Invalid grade\n" +
                    "$invalid"
            } catch (e: Exception) {
                seedDebugBlock() + "\n\n" +
                    "DB READ FAILED: ${e.message ?: e.javaClass.simpleName}"
            }
            tvSummary.text = text
        }
    }

    private fun seedDebugBlock(): String {
        fun yn(v: Boolean?): String = when (v) {
            true -> "YES"
            false -> "NO"
            null -> "UNKNOWN"
        }
        fun intOrUnknown(v: Int?): String = v?.toString() ?: "UNKNOWN"
        return buildString {
            appendLine("SEED DEBUG")
            appendLine("seedIfNeeded triggered: ${yn(DbSeeder.getLastSeedIfNeededTriggered())}")
            appendLine("force reseed started: ${yn(DbSeeder.getLastForceReseedStarted())}")
            appendLine("force reseed ended: ${yn(DbSeeder.getLastForceReseedEnded())}")
            appendLine("about to insert size: ${intOrUnknown(DbSeeder.getLastAboutToInsertSize())}")
            append("after insert DB count: ${intOrUnknown(DbSeeder.getLastAfterInsertDbCount())}")
        }
    }

    private fun getPoolStatusGradeLabel(): String {
        val gradePrefs = GradePrefs(this)
        return when (gradePrefs.getSelectedMode()) {
            com.brainbuddy.app.core.LevelMode.LGS -> "LGS"
            com.brainbuddy.app.core.LevelMode.GRADE -> {
                val g = gradePrefs.getSelectedGrade()
                when {
                    g == GradePrefs.GRADE_JUNIOR -> "Junior"
                    g in 1..7 -> "$g. Sınıf"
                    else -> "-"
                }
            }
        }
    }

    private fun updatePoolStatusTitle() {
        val label = getPoolStatusGradeLabel()
        val title = getString(R.string.pool_status_title_format, label)
        supportActionBar?.title = title
        findViewById<android.widget.TextView>(R.id.tvPoolStatusTitle)?.text = title
    }

    private fun setupFixButtons() {
        if (!BuildConfig.DEBUG) return

        val gradePrefs = GradePrefs(this)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFixInvalidGrades)
            .setOnClickListener {
                if (gradePrefs.getSelectedMode() == LevelMode.LGS) {
                    Toast.makeText(this, "Fix Invalid Grades sadece Sınıf modunda kullanılır.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val target = gradePrefs.getSelectedGrade()
                if (target !in 1..7) {
                    Toast.makeText(this, "Önce 1–7 arası sınıf seçin (Test Ayarları).", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    val updated = withContext(Dispatchers.IO) {
                        val db = DatabaseProvider.get(this@PoolStatusActivity)
                        db.questionDao().fixInvalidGrades(target)
                    }
                    Toast.makeText(this@PoolStatusActivity, "$updated soru $target. sınıfa taşındı.", Toast.LENGTH_SHORT).show()
                    renderStatus()
                }
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFixDifficultyOutOfRange)
            .setOnClickListener {
                lifecycleScope.launch {
                    val (low, high) = withContext(Dispatchers.IO) {
                        val db = DatabaseProvider.get(this@PoolStatusActivity)
                        val dao = db.questionDao()
                        val lowCount = dao.fixDifficultyTooLow()
                        val highCount = dao.fixDifficultyTooHigh()
                        lowCount to highCount
                    }
                    val msg = when {
                        low + high == 0 -> "Zaten hepsi aralıkta."
                        else -> "Düzeltildi: $low (çok düşük) + $high (çok yüksek)."
                    }
                    Toast.makeText(this@PoolStatusActivity, msg, Toast.LENGTH_SHORT).show()
                    renderStatus()
                }
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnImportLgsPacks)
            .setOnClickListener {
                lifecycleScope.launch {
                    val summary = withContext(Dispatchers.IO) {
                        QuestionPackImporter.importAllLgsPacksFromAssets(this@PoolStatusActivity)
                    }
                    AlertDialog.Builder(this@PoolStatusActivity)
                        .setTitle("LGS Import Sonucu")
                        .setMessage(summary.summaryText)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                    renderStatus()
                }
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnImportLgsMatPacks)
            .setOnClickListener {
                lifecycleScope.launch {
                    val summary = withContext(Dispatchers.IO) {
                        QuestionPackImporter.importMatLgsPacksFromAssets(this@PoolStatusActivity)
                    }
                    AlertDialog.Builder(this@PoolStatusActivity)
                        .setTitle("LGS MAT Import Sonucu")
                        .setMessage(summary.toString())
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                    renderStatus()
                }
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnImportLgsFenPacks)
            .setOnClickListener {
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        QuestionPackImporter.importFenLgsPacksFromAssets(this@PoolStatusActivity)
                    }
                    AlertDialog.Builder(this@PoolStatusActivity)
                        .setTitle("LGS FEN Import Sonucu")
                        .setMessage(result.summary)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                    renderStatus()
                }
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnImportLgsEnglishPacks)
            .setOnClickListener {
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        QuestionPackImporter.importEnglishLgsPacksFromAssets(this@PoolStatusActivity)
                    }
                    AlertDialog.Builder(this@PoolStatusActivity)
                        .setTitle("LGS English Import Sonucu")
                        .setMessage(result.summary)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                    renderStatus()
                }
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnForceReseedGeneralBanks)
            .setOnClickListener {
                lifecycleScope.launch {
                    android.util.Log.d("SEED_DEBUG", "FORCE RESEED CLICKED")
                    android.util.Log.d("SEED_DEBUG", "Force reseed (GENERAL banks) START")
                    DbSeeder.markForceReseedStarted()
                    // This is awaited (suspends) — not fire-and-forget.
                    withContext(Dispatchers.IO) { DbSeeder.forceReseedGeneralBanks(this@PoolStatusActivity) }
                    android.util.Log.d("SEED_DEBUG", "Force reseed (GENERAL banks) END")
                    DbSeeder.markForceReseedEnded()
                    // Refresh the on-screen SEED DEBUG block immediately.
                    findViewById<TextView>(R.id.tvPoolStatus).text = seedDebugBlock()
                    val summary = withContext(Dispatchers.IO) {
                        val db = DatabaseProvider.get(this@PoolStatusActivity)
                        val dao = db.questionDao()
                        val total = dao.countAll()
                        val gradeLines = mutableListOf<String>()
                        for (g in 1..8) {
                            gradeLines.add("  Grade $g: ${dao.countByGradeOnly(g)}")
                        }
                        val byGrade = gradeLines.joinToString("\n")
                        val bySubject = dao.getCountsBySubject()
                            .joinToString("\n") { "  ${it.subject}: ${it.count}" }
                        buildString {
                            appendLine("Total DB count: $total")
                            appendLine()
                            appendLine("Counts by grade:")
                            appendLine(byGrade)
                            appendLine()
                            appendLine("Counts by subject:")
                            appendLine(bySubject)
                        }
                    }
                    AlertDialog.Builder(this@PoolStatusActivity)
                        .setTitle(getString(R.string.pool_status_force_reseed_title))
                        .setMessage(summary)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                    renderStatus()
                }
            }
    }

    private fun renderStatus() {
        val tv = findViewById<TextView>(R.id.tvPoolStatus)
        val tvAudit = findViewById<TextView>(R.id.tvSeedAudit)
        val btnCopyAudit = findViewById<Button>(R.id.btnCopySeedAudit)
        val gradePrefs = GradePrefs(this)
        val quizPrefs = QuizPrefs(this)
        val selectedGrade = gradePrefs.getSelectedGrade()
        val difficulty = quizPrefs.difficulty()

        lifecycleScope.launch {
            val (summary, auditText) = withContext(Dispatchers.IO) {
                val sb = StringBuilder()
                var auditText = ""
                try {
                    RoomQuizDataStore(this@PoolStatusActivity).ensureSeeded()
                    val db = DatabaseProvider.get(this@PoolStatusActivity)
                    val dao = db.questionDao()

                    // Minimal safe summary first (always show this)
                    val total = dao.countAll()
                    val active = dao.countAllActive()
                    val invalidGrades = dao.countInvalidGrades()

                    sb.append("TOTAL / ACTIVE\n")
                    sb.append("$total / $active\n")
                    sb.append("invalid grade: $invalidGrades\n")

                    // Temporarily disabled detailed sections:
                    // - grade dağılımı
                    // - grade+subject+diff
                    // - duplicate stemHash section

                    auditText = db.appMetaDao().get("seed_audit_latest") ?: ""
                } catch (e: Exception) {
                    if (sb.isEmpty()) {
                        sb.append("TOTAL / ACTIVE\n")
                        sb.append("- / -\n")
                        sb.append("invalid grade: -\n")
                    }
                    sb.append("\nERROR: ${e.message ?: e.javaClass.simpleName}")
                }
                sb.toString().trimEnd() to auditText
            }
            tv.text = summary
            if (!auditText.isNullOrBlank()) {
                tvAudit.visibility = View.VISIBLE
                btnCopyAudit.visibility = View.VISIBLE
                tvAudit.text = auditText
                btnCopyAudit.setOnClickListener {
                    val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                    val clip = android.content.ClipData.newPlainText("Seed Audit", auditText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@PoolStatusActivity, "Seed audit copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            } else {
                tvAudit.visibility = View.GONE
                btnCopyAudit.visibility = View.GONE
            }
        }
    }
}
