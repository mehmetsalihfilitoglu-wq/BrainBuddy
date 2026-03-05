package com.brainbuddy.app.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.db.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        renderStatus()
    }

    private fun renderStatus() {
        val tv = findViewById<TextView>(R.id.tvPoolStatus)
        lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) {
                val db = DatabaseProvider.get(this@PoolStatusActivity)
                val dao = db.questionDao()
                val grade = 6
                val subjects = listOf(
                    "mat" to "Matematik",
                    "turkce" to "Türkçe",
                    "fen" to "Fen Bilimleri",
                    "sosyal" to "Sosyal Bilgiler",
                    "ing" to "İngilizce"
                )
                val difficultyLabels = mapOf(
                    0 to getString(R.string.pool_status_diff_easy),
                    1 to getString(R.string.pool_status_diff_medium),
                    2 to getString(R.string.pool_status_diff_hard)
                )

                val sb = StringBuilder()
                sb.append(getString(R.string.pool_status_header, grade)).append("\n\n")

                for ((key, name) in subjects) {
                    sb.append(name).append("\n")
                    for (diff in 0..2) {
                        val total = dao.countByGradeSubjectDifficulty(grade, key, diff)
                        val active = dao.countActiveByGradeSubjectDifficulty(grade, key, diff)
                        val label = difficultyLabels[diff] ?: "d=$diff"
                        sb.append("  ")
                            .append(label)
                            .append(": ")
                            .append("TOTAL=")
                            .append(total)
                            .append(" / ACTIVE=")
                            .append(active)
                            .append("\n")
                    }
                    sb.append("\n")
                }
                sb.toString().trimEnd()
            }
            tv.text = summary
        }
    }
}

