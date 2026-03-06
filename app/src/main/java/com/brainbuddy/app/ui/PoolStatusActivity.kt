package com.brainbuddy.app.ui

import android.os.Bundle
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

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.pool_status_title)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val layoutDebugFix = findViewById<View>(R.id.layoutDebugFixButtons)
        layoutDebugFix.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE

        setupFixButtons()
        renderStatus()
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
    }

    private fun renderStatus() {
        val tv = findViewById<TextView>(R.id.tvPoolStatus)
        val gradePrefs = GradePrefs(this)
        val quizPrefs = QuizPrefs(this)
        val selectedGrade = gradePrefs.getSelectedGrade()
        val difficulty = quizPrefs.difficulty()

        lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) {
                val db = DatabaseProvider.get(this@PoolStatusActivity)
                val dao = db.questionDao()

                val total = dao.countAll()
                val active = dao.countAllActive()
                val invalidGrades = dao.countInvalidGrades()
                val diffOutOfRange = dao.countDifficultyOutOfRange()
                val duplicates = dao.getTopDuplicateStemHashes()
                val activeByGradeSubjectDiff = dao.getActiveCountsByGradeSubjectDifficulty()

                val sb = StringBuilder()

                // 1) TOTAL / ACTIVE
                sb.append("TOTAL / ACTIVE\n")
                sb.append("$total / $active\n\n")

                // 2) Grade dağılımı (1..7)
                sb.append("Grade dağılımı (1..7):\n")
                for (g in 1..7) {
                    val gTotal = dao.countByGradeOnly(g)
                    val gActive = dao.countActiveByGradeOnly(g)
                    sb.append("  grade=$g total/active: $gTotal / $gActive\n")
                }
                if (invalidGrades > 0) {
                    sb.append("  ⚠ Geçersiz grade (0,8,9+): $invalidGrades\n")
                }
                sb.append("\n")

                // 3) grade+subject+diff ACTIVE sayıları
                sb.append("grade+subject+diff ACTIVE:\n")
                val subjects = listOf("mat", "turkce", "fen", "sosyal", "ing")
                for (g in 1..7) {
                    val subjRows = activeByGradeSubjectDiff.filter { it.grade == g }.groupBy { it.subject }
                    val line = subjects.joinToString("  ") { subj ->
                        val diffs = subjRows[subj].orEmpty()
                        val e = diffs.firstOrNull { it.difficulty == 0 }?.count ?: 0
                        val m = diffs.firstOrNull { it.difficulty == 1 }?.count ?: 0
                        val h = diffs.firstOrNull { it.difficulty == 2 }?.count ?: 0
                        "$subj(E=$e M=$m H=$h)"
                    }
                    sb.append("  grade=$g: $line\n")
                }
                sb.append("\n")

                // 4) Seçili mod / sınıf
                val mode = gradePrefs.getSelectedMode()
                sb.append(if (mode == LevelMode.LGS) "Seçili Mod: LGS"
                    else if (selectedGrade == com.brainbuddy.app.core.GradePrefs.GRADE_JUNIOR) "Seçili Sınıf: Junior"
                    else "Seçili Sınıf: $selectedGrade. Sınıf")
                sb.append(", zorluk: ${difficulty.name}\n\n")

                // 5) Zorluk aralık dışı
                if (diffOutOfRange > 0) {
                    sb.append("⚠ Zorluk aralık dışı (0–2 dışı): $diffOutOfRange soru\n\n")
                }

                // 6) TOP 20 duplicate stemHash
                sb.append("TOP 20 duplicate stemHash (aynı grade+subject içinde):\n")
                if (duplicates.isEmpty()) {
                    sb.append("  (yok)\n")
                } else {
                    duplicates.forEachIndexed { i, row ->
                        sb.append("  ${i + 1}. grade=${row.grade} ${row.subject} hash=${row.stemHash.take(12)}… cnt=${row.cnt}\n")
                    }
                }

                sb.toString().trimEnd()
            }
            tv.text = summary
        }
    }
}
