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
import com.brainbuddy.app.db.PoolQuotaEnforcer
import com.brainbuddy.app.quiz.QuestionPackImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        // seedIfNeeded runs async in Application; first paint can be pre-seed — refresh shortly after open.
        lifecycleScope.launch {
            delay(450)
            renderStatus()
        }
    }

    override fun onResume() {
        super.onResume()
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
                    val ok = withContext(Dispatchers.IO) {
                        DbSeeder.forceReseedGeneralBanks(this@PoolStatusActivity)
                    }
                    Toast.makeText(
                        this@PoolStatusActivity,
                        if (ok) "Reseed successful" else "Reseed failed, old database preserved",
                        Toast.LENGTH_LONG
                    ).show()
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
                val db = DatabaseProvider.get(this@PoolStatusActivity)
                val dao = db.questionDao()

                val total = dao.countAll()
                val active = dao.countAllActive()
                val invalidGrades = dao.countInvalidGrades()
                val diffOutOfRange = dao.countDifficultyOutOfRange()
                val duplicates = dao.getTopDuplicateStemHashes()
                val activeByGradeSubjectDiff = dao.getActiveCountsByGradeSubjectDifficulty()

                val sb = StringBuilder()

                // 1) TOTAL / ACTIVE — full table (all examType rows); not LGS-only.
                sb.append("TOTAL / ACTIVE (tüm questions tablosu)\n")
                sb.append("$total / $active\n\n")

                // DEBUG seed diagnostics (runtime).
                if (BuildConfig.DEBUG) {
                    val quotaReport = PoolQuotaEnforcer.lastReport()
                    sb.append("CORE_CELL_MEASUREMENTS (device DB + last PoolQuotaEnforcer report)\n")
                    sb.append("Cells: grades 1..7 × mat,turkce,fen,sosyal,ing\n")
                    if (quotaReport == null) {
                        sb.append("(quota_* fields n/a until enforceCoreQuotas has run this session)\n")
                    }
                    sb.append("Fields: active_count inactive_count quota_deficit_before quota_deficit_after gate_loss_count quota_topup_count\n\n")
                    for (g in 1..7) {
                        for (subj in PoolQuotaEnforcer.CORE_SUBJECTS) {
                            val activeC = dao.countActiveByGradeSubject(g, subj)
                            val inactiveC = dao.countInactiveByGradeSubject(g, subj)
                            val gateLoss = dao.countInactiveQualityGateByGradeSubject(g, subj)
                            val defB = quotaReport?.deficitBefore?.get(g to subj)
                            val defA = quotaReport?.deficitAfter?.get(g to subj)
                            val topUp = if (quotaReport == null) null else (quotaReport.insertedPerCell[g to subj] ?: 0)
                            val defBStr = defB?.toString() ?: "n/a"
                            val defAStr = defA?.toString() ?: "n/a"
                            val topUpStr = topUp?.toString() ?: "n/a"
                            sb.append("g${g}_$subj: active_count=$activeC inactive_count=$inactiveC quota_deficit_before=$defBStr quota_deficit_after=$defAStr gate_loss_count=$gateLoss quota_topup_count=$topUpStr\n")
                        }
                    }
                    sb.append("\n")

                    val pipe = DbSeeder.debugMat6PipelineDiagnostics()
                    val mat6Inserted = dao.countGrade6MatGeneral()
                    val mat6Active = dao.countGrade6MatGeneralActive()
                    val mat6Inactive = dao.countGrade6MatGeneralInactive()
                    val mat6QGateInactive = dao.countGrade6MatInactiveQualityGateReasons()
                    val mat6TopReasons = dao.getGrade6MatTopInactiveReasons()
                    val mat6DupGroups = dao.getGrade6MatDuplicateStemGroups()
                    val dupExtraRows = mat6DupGroups.sumOf { row -> (row.cnt - 1).coerceAtLeast(0) }
                    val generated = pipe?.generatedMat6Total ?: 0
                    val rejectedNotActive = (generated - mat6Active).coerceAtLeast(0)

                    sb.append("MAT6_MAT_DEBUG (subject=mat, grade=6, examType=GENERAL)\n")
                    sb.append("generated_mat6_total=$generated\n")
                    sb.append("parsed_mat6_total=${pipe?.parsedMat6Total ?: 0}\n")
                    sb.append("inserted_mat6_total=$mat6Inserted\n")
                    sb.append("active_mat6_total=$mat6Active\n")
                    sb.append("inactive_mat6_total=$mat6Inactive\n")
                    sb.append("duplicate_mat6_total=${(pipe?.mat6DedupDropped ?: 0) + dupExtraRows}\n")
                    sb.append("rejected_mat6_total=$rejectedNotActive\n")
                    sb.append("\n")
                    sb.append("reason_quality_gate=${mat6QGateInactive}\n")
                    sb.append("reason_duplicate=${pipe?.mat6DedupDropped ?: 0}\n")
                    sb.append("reason_missing_fields=${pipe?.parseMissingFields ?: 0}\n")
                    sb.append("reason_invalid_schema=${pipe?.parseInvalidSchema ?: 0}\n")
                    val otherInactive =
                        (mat6Inactive - mat6QGateInactive).coerceAtLeast(0)
                    sb.append(
                        "reason_other=${(pipe?.parseOther ?: 0) + otherInactive}\n"
                    )
                    sb.append("\n")
                    sb.append("TOP_5_DEACTIVATION_REASONS (g6 mat GENERAL inactive)\n")
                    if (mat6TopReasons.isEmpty()) {
                        sb.append("  (yok)\n")
                    } else {
                        mat6TopReasons.forEachIndexed { i, row ->
                            val r = row.reason?.ifBlank { "(null)" } ?: "(null)"
                            sb.append("  ${i + 1}. $r → ${row.cnt}\n")
                        }
                    }
                    sb.append("\n")
                    sb.append("TOP_10_DUPLICATE_STEM_HASH (g6 mat GENERAL)\n")
                    if (mat6DupGroups.isEmpty()) {
                        sb.append("  (yok)\n")
                    } else {
                        mat6DupGroups.forEachIndexed { i, row ->
                            val ids = dao.getSampleIdsForMat6StemHash(row.stemHash)
                            val idPart = ids.joinToString(", ").ifBlank { "-" }
                            sb.append(
                                "  ${i + 1}. hash=${row.stemHash.take(16)}… cnt=${row.cnt} ids=[$idPart]\n"
                            )
                        }
                    }
                    sb.append("\n")

                    val g6Pack = DbSeeder.GRADE6_MAT_SOURCE_PACK
                    val g6Ingest = DbSeeder.debugGrade6MatFileIngest()
                    val g6Inserted = dao.countBySourcePack(g6Pack)
                    val g6Active = dao.countActiveBySourcePack(g6Pack)
                    val g6Inactive = dao.countInactiveBySourcePack(g6Pack)
                    val g6DistinctStem = dao.countDistinctStemHashBySourcePack(g6Pack)
                    val g6Dup = (g6Inserted - g6DistinctStem).coerceAtLeast(0)
                    val g6Rejected = dao.countRejectedQualityGateBySourcePack(g6Pack)
                    sb.append("GRADE6_MAT_FILE_INGEST (asset → sourcePack=$g6Pack)\n")
                    sb.append("mat6_file_seen=${if (g6Ingest?.fileSeen == true) "yes" else "no"}\n")
                    sb.append("mat6_file_parsed_count=${g6Ingest?.lastParsedCount ?: 0}\n")
                    sb.append("mat6_file_inserted_count=$g6Inserted\n")
                    sb.append("mat6_file_active_count=$g6Active\n")
                    sb.append("mat6_file_inactive_count=$g6Inactive\n")
                    sb.append("mat6_file_duplicate_count=$g6Dup\n")
                    sb.append("mat6_file_rejected_count=$g6Rejected\n")
                    sb.append("\n")

                    val diag = DbSeeder.debugLastSeedDiagnostics()
                    if (diag != null) {
                        sb.append("SEED_SOURCE_COUNTS_AND_NORMALIZE_DEBUG\n")
                        sb.append("loaded_root_general=${diag.loaded_root_general}\n")
                        sb.append("loaded_packs=${diag.loaded_packs}\n")
                        sb.append("loaded_grade_based=${diag.loaded_grade_based}\n")
                        sb.append("loaded_lgs_exam=${diag.loaded_lgs_exam}\n")
                        sb.append("loaded_synthetic=${diag.loaded_synthetic}\n")
                        sb.append("discovered_grade_based_dirs=${diag.discovered_grade_based_dirs}\n")
                        sb.append("discovered_grade_based_json_files=${diag.discovered_grade_based_json_files}\n")
                        sb.append("total_before_normalize=${diag.total_before_normalize}\n")
                        sb.append("total_after_normalize=${diag.total_after_normalize}\n")
                        sb.append("invalid_grade_before_normalize=${diag.invalid_grade_before_normalize}\n")
                        sb.append("invalid_grade_after_normalize=${diag.invalid_grade_after_normalize}\n")
                        sb.append("normalization_applied=${if (diag.normalization_applied) "yes" else "no"}\n")
                        sb.append("invalid_after_normalize=${diag.invalid_after_normalize}\n")
                        sb.append("final_inserted=${diag.final_inserted}\n\n")

                        sb.append("DB_CHECK\n")
                        sb.append("dbcheck_total_rows=${diag.dbcheck_total_rows}\n")
                        sb.append("dbcheck_invalid_rows=${diag.dbcheck_invalid_rows}\n")
                        sb.append("dbcheck_valid_rows=${diag.dbcheck_valid_rows}\n")
                        sb.append("dbcheck_row1=${diag.dbcheck_row1}\n")
                        sb.append("dbcheck_row2=${diag.dbcheck_row2}\n")
                        sb.append("dbcheck_row3=${diag.dbcheck_row3}\n")
                        sb.append("dbcheck_row4=${diag.dbcheck_row4}\n")
                        sb.append("dbcheck_row5=${diag.dbcheck_row5}\n\n")
                    }
                }

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
