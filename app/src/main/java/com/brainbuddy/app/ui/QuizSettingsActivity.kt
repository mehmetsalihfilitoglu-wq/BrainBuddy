package com.brainbuddy.app.ui

import android.os.Bundle
import android.widget.CheckBox
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ExamPackStore
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.QuizPrefs
import com.brainbuddy.app.quiz.ExamType
import com.brainbuddy.app.quiz.QuizDifficulty

class QuizSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: QuizPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, QuizSettingsActivity::class.java)) return

        setContentView(R.layout.activity_quiz_settings)

        prefs = QuizPrefs(this)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Test Ayarları"

        val difficultyGroup = findViewById<RadioGroup>(R.id.difficultyGroup)
        val questionsGroup = findViewById<RadioGroup>(R.id.questionsGroup)

        when (prefs.difficulty()) {
            QuizDifficulty.EASY -> difficultyGroup.check(R.id.diffEasy)
            QuizDifficulty.MEDIUM -> difficultyGroup.check(R.id.diffMedium)
            QuizDifficulty.HARD -> difficultyGroup.check(R.id.diffHard)
        }

        when (prefs.questionsPerSession()) {
            5 -> questionsGroup.check(R.id.q5)
            15 -> questionsGroup.check(R.id.q15)
            else -> questionsGroup.check(R.id.q10)
        }

        difficultyGroup.setOnCheckedChangeListener { _, id ->
            val diff = when (id) {
                R.id.diffEasy -> QuizDifficulty.EASY
                R.id.diffHard -> QuizDifficulty.HARD
                else -> QuizDifficulty.MEDIUM
            }
            prefs.setDifficulty(diff)
        }

        questionsGroup.setOnCheckedChangeListener { _, id ->
            val count = when (id) {
                R.id.q5 -> 5
                R.id.q15 -> 15
                else -> 10
            }
            prefs.setQuestionsPerSession(count)
        }

        val examStore = ExamPackStore(this)
        val active = examStore.getActiveExamTypes()
        findViewById<CheckBox>(R.id.checkLGS).apply {
            isChecked = ExamType.LGS in active
            setOnCheckedChangeListener { _, _ ->
                updateExamPacks(examStore)
            }
        }
        findViewById<CheckBox>(R.id.checkTYT).apply {
            isChecked = ExamType.TYT in active
            setOnCheckedChangeListener { _, _ -> updateExamPacks(examStore) }
        }
        findViewById<CheckBox>(R.id.checkAYT).apply {
            isChecked = ExamType.AYT in active
            setOnCheckedChangeListener { _, _ -> updateExamPacks(examStore) }
        }
        findViewById<CheckBox>(R.id.checkGeneral).apply {
            isChecked = active.isEmpty() || ExamType.GENERAL in active
            setOnCheckedChangeListener { _, _ -> updateExamPacks(examStore) }
        }
    }

    private fun updateExamPacks(examStore: ExamPackStore) {
        val set = mutableSetOf<ExamType>()
        if (findViewById<CheckBox>(R.id.checkLGS).isChecked) set.add(ExamType.LGS)
        if (findViewById<CheckBox>(R.id.checkTYT).isChecked) set.add(ExamType.TYT)
        if (findViewById<CheckBox>(R.id.checkAYT).isChecked) set.add(ExamType.AYT)
        if (findViewById<CheckBox>(R.id.checkGeneral).isChecked) set.add(ExamType.GENERAL)
        examStore.setActiveExamTypes(if (set.isEmpty()) setOf(ExamType.GENERAL) else set)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
