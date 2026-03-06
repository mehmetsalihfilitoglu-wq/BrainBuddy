package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import android.widget.RadioGroup
import com.brainbuddy.app.R
import com.brainbuddy.app.core.GradePrefs
import com.brainbuddy.app.core.LevelMode
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.QuizPrefs
import com.brainbuddy.app.quiz.QuizActivity
import com.brainbuddy.app.quiz.QuizDifficulty
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

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
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnMiniTest).setOnClickListener {
            if (!gradePrefs.hasLevelSelected()) {
                android.widget.Toast.makeText(this, R.string.grade_required_toast, android.widget.Toast.LENGTH_LONG).show()
                findViewById<android.widget.RadioGroup>(R.id.testModeGroup)?.requestFocus()
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
        val modeGroup = findViewById<RadioGroup>(R.id.testModeGroup)
        val gradeChipGroup = findViewById<ChipGroup>(R.id.testGradeChipGroup)
        val tvSelected = findViewById<android.widget.TextView>(R.id.tvSelectedGrade)

        // Dinamik liste: Junior, 1, 2, 3, 4, 5, 6, 7 (LGS ayrı mod olarak)
        val gradeOptions = listOf("Junior", "1", "2", "3", "4", "5", "6", "7")

        val displayToValue = gradeOptions.mapIndexed { i, label ->
            label to (if (label == "Junior") GradePrefs.GRADE_JUNIOR else i)
        }.toMap()

        gradeChipGroup.removeAllViews()
        gradeOptions.forEach { label ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                layoutParams = ChipGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            gradeChipGroup.addView(chip)
        }

        fun updateLabel() {
            when (gradePrefs.getSelectedMode()) {
                LevelMode.GRADE -> {
                    val g = gradePrefs.getSelectedGrade()
                    tvSelected.text = when {
                        g == GradePrefs.GRADE_JUNIOR -> getString(R.string.grade_selected_junior)
                        g in 1..7 -> getString(R.string.grade_selected_label, g)
                        else -> getString(R.string.grade_selected_none)
                    }
                    gradeChipGroup.visibility = android.view.View.VISIBLE
                }
                LevelMode.LGS -> {
                    tvSelected.text = getString(R.string.mode_selected_lgs)
                    gradeChipGroup.visibility = android.view.View.GONE
                }
            }
        }

        fun selectChipForGrade(grade: Int) {
            for (i in 0 until gradeChipGroup.childCount) {
                val chip = gradeChipGroup.getChildAt(i) as? Chip ?: continue
                val value = displayToValue[chip.text.toString()]
                chip.isChecked = (value != null && value == grade)
            }
        }

        when (gradePrefs.getSelectedMode()) {
            LevelMode.GRADE -> modeGroup.check(R.id.testModeGrade)
            LevelMode.LGS -> modeGroup.check(R.id.testModeLGS)
        }
        selectChipForGrade(gradePrefs.getSelectedGrade())
        updateLabel()

        modeGroup.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.testModeLGS -> gradePrefs.setSelectedMode(LevelMode.LGS)
                else -> gradePrefs.setSelectedMode(LevelMode.GRADE)
            }
            updateLabel()
        }

        gradeChipGroup.setOnCheckedChangeListener { _, _ ->
            for (i in 0 until gradeChipGroup.childCount) {
                val chip = gradeChipGroup.getChildAt(i) as? Chip ?: continue
                if (chip.isChecked) {
                    val value = displayToValue[chip.text.toString()]
                    if (value != null) {
                        gradePrefs.setSelectedGrade(value)
                        updateLabel()
                    }
                    break
                }
            }
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
