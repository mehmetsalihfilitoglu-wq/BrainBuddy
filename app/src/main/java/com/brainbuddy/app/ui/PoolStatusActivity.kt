package com.brainbuddy.app.ui

import android.app.AlertDialog
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
import com.brainbuddy.app.db.DbSeeder
import com.brainbuddy.app.db.RoomQuizDataStore
import com.brainbuddy.app.quiz.formatLgsMatSummaryText
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

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        updatePoolStatusTitle()
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val layoutDebugFix = findViewById<View>(R.id.layoutDebugFixButtons)
        layoutDebugFix.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE

        setupFixButtons()
        renderStatus()
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
                        .setMessage(formatLgsMatSummaryText(summary))
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
                    withContext(Dispatchers.IO) {
                        DbSeeder.forceReseedGeneralBanks(this@PoolStatusActivity)
                    }
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
        val gradePrefs = GradePrefs(this)
        val quizPrefs = QuizPrefs(this)
        val selectedGrade = gradePrefs.getSelectedGrade()
        val difficulty = quizPrefs.difficulty()

        lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) {
                RoomQuizDataStore(this@PoolStatusActivity).ensureSeeded()
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

                // 4b) LGS pool (when LGS mode selected)
                if (mode == LevelMode.LGS) {
                    val lgsTotal = dao.countLgsActive()
                    val lgsBySubject = dao.getLgsCountsBySubject().associate { it.subject to it.count }
                    val lgsByDiff = dao.getLgsCountsByDifficulty().associate { it.difficulty to it.count }
                    val required = mapOf("mat" to 4, "turkce" to 4, "fen" to 4, "inkilap" to 3, "din" to 3, "ing" to 2)
                    val subjects = listOf("mat", "turkce", "fen", "inkilap", "din", "ing")
                    sb.append("LGS havuzu (examType=LGS):\n")
                    sb.append("  Toplam aktif: $lgsTotal\n")
                    sb.append("  Ders bazında: ")
                    sb.append(subjects.joinToString(" ") { "$it=${lgsBySubject[it] ?: 0}" })
                    sb.append("\n")
                    sb.append("  Zorluk (0=Kolay 1=Orta 2=Zor): ")
                    sb.append(listOf(0, 1, 2).joinToString(" ") { "diff$it=${lgsByDiff[it] ?: 0}" })
                    sb.append("\n")
                    val enough = subjects.all { (lgsBySubject[it] ?: 0) >= (required[it] ?: 0) }
                    sb.append(if (enough) "  ✓ 20 soruluk LGS testi için yeterli\n"
                        else "  ⚠ 20 soruluk LGS testi için YETERSİZ (MAT≥4 TURKCE≥4 FEN≥4 INKILAP≥3 DIN≥3 ING≥2)\n")
                    val inactiveLow = dao.countLgsInactiveLowQuality()
                    if (inactiveLow > 0 || lgsTotal > 0) {
                        val avgBySubj = dao.getLgsAvgQualityBySubject()
                        val newGenBySubj = dao.getLgsNewGenCountBySubject()
                        sb.append("  LGS Kalite: aktif=$lgsTotal, pasif(düşük)=$inactiveLow\n")
                        if (avgBySubj.isNotEmpty()) {
                            sb.append("  Ort. qualityScore: ")
                            sb.append(avgBySubj.joinToString(" ") { "${it.subject}=${it.avgQualityScore.toInt()}" })
                            sb.append("\n")
                        }
                        if (newGenBySubj.isNotEmpty()) {
                            sb.append("  Yeni nesil oranı: ")
                            sb.append(newGenBySubj.joinToString(" ") { row ->
                                val pct = if (row.totalCount > 0) (row.newGenCount * 100 / row.totalCount) else 0
                                "${row.subject}=${pct}%"
                            })
                            sb.append("\n")
                        }
                    }
                    // MAT LGS pool (math-only summary)
                    val matActive = lgsBySubject["mat"] ?: 0
                    val matByDiff = dao.getLgsCountsByDifficultyForSubject("mat").associate { it.difficulty to it.count }
                    val matByType = dao.getLgsCountsByQuestionTypeForSubject("mat")
                    val matAvgQuality = dao.getLgsAvgQualityBySubject().firstOrNull { it.subject == "mat" }?.avgQualityScore
                    val matNewGen = dao.getLgsNewGenCountBySubject().firstOrNull { it.subject == "mat" }
                    val matInactive = dao.countLgsInactiveLowQualityBySubject("mat")
                    sb.append("  MAT LGS (math-only): aktif=$matActive, pasif=$matInactive\n")
                    sb.append("  MAT zorluk: ")
                    sb.append(listOf(0, 1, 2).joinToString(" ") { "diff$it=${matByDiff[it] ?: 0}" })
                    sb.append("\n")
                    sb.append("  MAT questionType: ")
                    sb.append(matByType.joinToString(" ") { "${it.questionType}=${it.count}" }.ifEmpty { "(yok)" })
                    sb.append("\n")
                    if (matAvgQuality != null) sb.append("  MAT ort. qualityScore: ${matAvgQuality.toInt()}\n")
                    if (matNewGen != null && matNewGen.totalCount > 0) {
                        val ratio = matNewGen.newGenCount * 100 / matNewGen.totalCount
                        sb.append("  MAT yeni nesil oranı: ${ratio}%\n")
                    } else if (matActive > 0) sb.append("  MAT yeni nesil oranı: 0%\n")
                    sb.append("\n")
                }

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
