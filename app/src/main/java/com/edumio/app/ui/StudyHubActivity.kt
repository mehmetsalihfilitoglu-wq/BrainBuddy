package com.edumio.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.edumio.app.R
import com.edumio.app.core.AnalyticsStore
import com.edumio.app.core.CareerPath
import com.edumio.app.core.UserGoalPrefs
import com.edumio.app.quiz.QuizActivity
import com.edumio.app.quiz.WrongPoolLauncher
import com.edumio.app.quiz.WrongQuestionPoolStore
import kotlin.math.roundToInt

class StudyHubActivity : AppCompatActivity() {

    private lateinit var analytics: AnalyticsStore
    private lateinit var goalPrefs: UserGoalPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_hub)

        analytics = AnalyticsStore(this)
        goalPrefs = UserGoalPrefs(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
        // v1 has exactly one new-question action — the Daily Challenge. The generic "Pratik Yap" here
        // started a separate quiz session, so it is removed; Öğren keeps the coach tip and wrong-question
        // review (neither creates new questions).
        findViewById<MaterialButton>(R.id.btnPracticeAll).visibility = View.GONE
        findViewById<MaterialCardView>(R.id.cardCoachTip).onTap {
            startActivity(Intent(this, com.edumio.app.coach.CoachScreen::class.java))
        }
        // V1 focus: university discovery + info/tools live on web (Instagram/blog/ISEEmio),
        // not in the exam-prep app. DiscoverActivity / ToolsActivity are kept in the codebase
        // for a future major version; only their in-app entry points are removed.

        val content = findViewById<View>(R.id.scrollContent)
        val origBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, origBottom + navBottom)
            insets
        }

        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        refreshWrongPool()
    }

    private fun refreshAll() {
        refreshExamContext()
        refreshStats()
        refreshCoachTip()
        refreshWrongPool()
    }

    private fun refreshExamContext() {
        // v1 is exam-framed: show ONLY the active exam (IMAT / TIL-I / CEnT-S). Never the career name
        // ("Mühendislik") or the Italian degree/exam name — consistent with Home and Profile.
        val exam = goalPrefs.getGoal().careerPath.examType
        findViewById<TextView>(R.id.tvCareerLabel).text = "🇮🇹 ${exam.code}"
        findViewById<TextView>(R.id.tvExamLabel).visibility = android.view.View.GONE
    }

    private fun refreshStats() {
        val counts = analytics.getOverallCounts()
        val testCount = analytics.getTestPerformances().size
        val accuracy = analytics.getOverallAccuracy()

        if (counts.total > 0) {
            findViewById<TextView>(R.id.tvStatsAccuracy).text = "%${accuracy.roundToInt()}"
            findViewById<TextView>(R.id.tvStatsTests).text = "$testCount"
            findViewById<TextView>(R.id.tvStatsQuestions).text = "${counts.total}"
        } else {
            listOf(R.id.tvStatsAccuracy, R.id.tvStatsTests, R.id.tvStatsQuestions).forEach {
                findViewById<TextView>(it).text = "—"
            }
        }
    }

    private fun refreshCoachTip() {
        // The card is always visible as the single entry to the study coach.
        // When there is enough data, preview the weakest-topic tip; otherwise
        // show a neutral description of what the coach offers.
        val weakest = analytics.getWeakestTopicsWithCounts(1)
            .firstOrNull { it.second.total >= 5 }
        findViewById<TextView>(R.id.tvCoachMessage).text = if (weakest != null) {
            getString(R.string.study_hub_coach_tip, weakest.first, weakest.second.accuracy.roundToInt())
        } else {
            getString(R.string.study_hub_coach_generic)
        }
    }

    private fun refreshWrongPool() {
        val card = findViewById<MaterialCardView>(R.id.cardWrongPool)
        val pool = WrongQuestionPoolStore(this)
        card.visibility = if (pool.isNotEmpty()) View.VISIBLE else View.GONE
        if (pool.isNotEmpty()) {
            card.setOnClickListener { WrongPoolLauncher.launch(this) }
        }
    }

}
