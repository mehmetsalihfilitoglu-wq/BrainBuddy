package com.brainbuddy.app.quiz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.databinding.ActivityQuestionImportBinding
import org.json.JSONArray
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

        refreshStats()
    }

    private fun importFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val json = InputStreamReader(input, Charset.forName("UTF-8")).readText()
                val arr = JSONArray(json)
                val repo = QuestionRepository(this)
                val imported = repo.mergeImportedQuestions(arr)
                Toast.makeText(this, "$imported soru içe aktarıldı.", Toast.LENGTH_LONG).show()
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
        val summary = repo.buildQualityDebugSummary()
        b.tvStats.text = summary
    }
}
