package com.brainbuddy.app.quiz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brainbuddy.app.R
import com.brainbuddy.app.core.GradePrefs
import com.brainbuddy.app.core.LevelMode
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.QuizPrefs
import com.brainbuddy.app.databinding.ActivityQuestionImportBinding
import com.brainbuddy.app.db.DbSeeder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Parent-only activity to import question packs from JSON files.
 * Merges imported questions with bundled pool; supports LGS/TYT/AYT format.
 */
class QuestionImportActivity : AppCompatActivity() {

    private lateinit var b: ActivityQuestionImportBinding

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        b = ActivityQuestionImportBinding.inflate(layoutInflater)
        setContentView(b.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        b.btnSelectFile.setOnClickListener {
            pickFile.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        b.btnReimportPacks.setOnClickListener {
            val ctx = this
            lifecycleScope.launch {
                b.btnReimportPacks.isEnabled = false
                try {
                    withContext(Dispatchers.IO) {
                        DbSeeder.forceReseed(ctx)
                    }
                    Toast.makeText(ctx, "Soru havuzu yeniden içe aktarıldı (asset + import).", Toast.LENGTH_LONG).show()
                    refreshStats()
                } catch (e: Exception) {
                    android.util.Log.e("QuestionImport", "Force reseed failed", e)
                    Toast.makeText(ctx, "Yeniden içe aktarma hatası: ${e.message ?: "bilinmiyor"}", Toast.LENGTH_LONG).show()
                } finally {
                    b.btnReimportPacks.isEnabled = true
                }
            }
        }

        refreshStats()
    }

    private fun importFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val json = InputStreamReader(input, Charset.forName("UTF-8")).readText()
                val result = QuestionPackImporter.importFromJson(this, json)
                b.tvImportResult.text = result.summary
                b.tvImportResult.visibility = android.view.View.VISIBLE
                Toast.makeText(this, result.summary, Toast.LENGTH_LONG).show()
                refreshStats()
            } ?: run {
                Toast.makeText(this, "Dosya okunamadı.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("QuestionImport", "Import failed", e)
            Toast.makeText(this, "İçe aktarma hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshStats() {
        val repo = QuestionRepository(this)
        val gradePrefs = GradePrefs(this)
        val quizPrefs = QuizPrefs(this)
        val mode = gradePrefs.getSelectedMode()
        val selectedGrade = gradePrefs.getSelectedGrade()

        if (mode == LevelMode.LGS) {
            val debugText = try {
                val activeCounts = repo.buildImportDebugActiveCounts()
                "Seçili Mod: LGS\n\n$activeCounts"
            } catch (e: Exception) {
                "Havuz durumu okunamadı: ${e.message ?: "bilinmiyor"}"
            }
            b.tvStats.text = debugText
            return
        }

        if (selectedGrade !in 1..7) {
            b.tvStats.text = "Havuz durumu için Sınıf modunda 1–7 arası bir sınıf seçin."
            return
        }

        val difficulty = quizPrefs.difficulty()
        val debugText = try {
            val poolDebug = repo.buildPoolDebugStatsForGrade(selectedGrade, difficulty).readableText
            val activeCounts = repo.buildImportDebugActiveCounts()
            "$poolDebug\n\n$activeCounts"
        } catch (e: Exception) {
            "Havuz durumu okunamadı: ${e.message ?: "bilinmiyor"}"
        }
        b.tvStats.text = debugText
    }
}
