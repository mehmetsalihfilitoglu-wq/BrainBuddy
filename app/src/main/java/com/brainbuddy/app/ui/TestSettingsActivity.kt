package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.RadioGroup
import com.brainbuddy.app.R
import com.brainbuddy.app.core.GradePrefs
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.QuizPrefs
import com.brainbuddy.app.quiz.QuizActivity
import com.brainbuddy.app.quiz.QuizDifficulty

class TestSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, TestSettingsActivity::class.java)) return

        setContentView(R.layout.activity_test_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_test_settings)

        val protectionPrefs = ProtectionPrefs(this)
        val quizPrefs = QuizPrefs(this)
        val gradePrefs = GradePrefs(this)

        // BLOK 1: Test Ayarları
        setupGradeSelection(gradePrefs)
        setupDifficulty(quizPrefs)
        setupSuccessRate(protectionPrefs)
        setupQuizInterval(protectionPrefs)

        // BLOK 2: Soru / Sınav Paketleri
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExamPacks).setOnClickListener {
            startActivity(Intent(this, ExamPackActivity::class.java))
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPoolStatus)?.setOnClickListener {
            startActivity(Intent(this, PoolStatusActivity::class.java))
        }

        // BLOK 3: Eğitim Modülleri / İçerik
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnJuniorModule).setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.junior.JuniorSettingsActivity::class.java))
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnMiniTest).setOnClickListener {
            if (!gradePrefs.hasGradeSelected()) {
                android.widget.Toast.makeText(this, R.string.grade_required_toast, android.widget.Toast.LENGTH_LONG).show()
                findViewById<android.widget.RadioGroup>(R.id.testGradeGroup)?.requestFocus()
                return@setOnClickListener
            }
            startActivity(Intent(this, QuizActivity::class.java).apply {
                putExtra(QuizActivity.EXTRA_REMEDIAL, true)
            })
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWorkOnWrongs).setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java).apply {
                putExtra(ReportsActivity.EXTRA_OPEN_WRONG_REVIEW, true)
            })
        }

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupGradeSelection(gradePrefs: GradePrefs) {
        val group = findViewById<RadioGroup>(R.id.testGradeGroup)
        val tvSelected = findViewById<android.widget.TextView>(R.id.tvSelectedGrade)
        val gradeToId = mapOf(
            2 to R.id.testGrade2, 3 to R.id.testGrade3, 4 to R.id.testGrade4,
            5 to R.id.testGrade5, 6 to R.id.testGrade6, 7 to R.id.testGrade7, 8 to R.id.testGrade8
        )
        fun updateLabel() {
            val g = gradePrefs.getSelectedGrade()
            tvSelected.text = if (g in 2..8) getString(R.string.grade_selected_label, g) else "Seçili Sınıf: -"
        }
        val saved = gradePrefs.getSelectedGrade()
        gradeToId[saved]?.let { group.check(it) }
        updateLabel()
        group.setOnCheckedChangeListener { _, id ->
            val grade = gradeToId.entries.find { it.value == id }?.key ?: 0
            gradePrefs.setSelectedGrade(grade)
            updateLabel()
        }
    }

    private fun setupDifficulty(quizPrefs: QuizPrefs) {
        val group = findViewById<RadioGroup>(R.id.testDifficultyGroup)
        when (quizPrefs.difficulty()) {
            QuizDifficulty.EASY -> group.check(R.id.testDiffEasy)
            QuizDifficulty.MEDIUM -> group.check(R.id.testDiffMedium)
            QuizDifficulty.HARD -> group.check(R.id.testDiffHard)
        }
        group.setOnCheckedChangeListener { _, id ->
            val diff = when (id) {
                R.id.testDiffEasy -> QuizDifficulty.EASY
                R.id.testDiffMedium -> QuizDifficulty.MEDIUM
                R.id.testDiffHard -> QuizDifficulty.HARD
                else -> QuizDifficulty.MEDIUM
            }
            quizPrefs.setDifficulty(diff)
        }
    }

    private fun setupSuccessRate(protectionPrefs: ProtectionPrefs) {
        val group = findViewById<RadioGroup>(R.id.testSuccessRateGroup)
        when (protectionPrefs.minSuccessRatePercent()) {
            50 -> group.check(R.id.testSuccessRate50)
            70 -> group.check(R.id.testSuccessRate70)
            80 -> group.check(R.id.testSuccessRate80)
            else -> group.check(R.id.testSuccessRate60)
        }
        group.setOnCheckedChangeListener { _, id ->
            val pct = when (id) {
                R.id.testSuccessRate50 -> 50
                R.id.testSuccessRate70 -> 70
                R.id.testSuccessRate80 -> 80
                else -> 60
            }
            protectionPrefs.setMinSuccessRatePercent(pct)
        }
    }

    private fun setupQuizInterval(protectionPrefs: ProtectionPrefs) {
        val group = findViewById<RadioGroup>(R.id.testQuizIntervalGroup)
        when (protectionPrefs.quizIntervalMinutes()) {
            45 -> group.check(R.id.testInterval45)
            60 -> group.check(R.id.testInterval60)
            else -> group.check(R.id.testInterval30)
        }
        group.setOnCheckedChangeListener { _, id ->
            val mins = when (id) {
                R.id.testInterval45 -> 45
                R.id.testInterval60 -> 60
                else -> 30
            }
            protectionPrefs.setQuizIntervalMinutes(mins)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
