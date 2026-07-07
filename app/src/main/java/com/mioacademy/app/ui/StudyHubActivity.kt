package com.mioacademy.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mioacademy.app.R
import com.mioacademy.app.core.AnalyticsStore
import com.mioacademy.app.core.CareerPath
import com.mioacademy.app.core.UserGoalPrefs
import com.mioacademy.app.core.exam.AdmissionExamRegistry
import com.mioacademy.app.quiz.QuizActivity
import com.mioacademy.app.quiz.WrongPoolLauncher
import com.mioacademy.app.quiz.WrongQuestionPoolStore
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
        findViewById<MaterialButton>(R.id.btnPracticeAll).onTap {
            startActivity(Intent(this, QuizActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardCoachTip).onTap {
            startActivity(Intent(this, com.mioacademy.app.coach.CoachScreen::class.java))
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
        buildSubjectCards()
        refreshCoachTip()
        refreshWrongPool()
    }

    private fun refreshExamContext() {
        val career = goalPrefs.getGoal().careerPath
        val exam = career.examType
        findViewById<TextView>(R.id.tvCareerLabel).text = "${career.emoji} ${career.displayNameTr}"
        findViewById<TextView>(R.id.tvExamLabel).text = "${exam.code} · ${exam.fullNameIt}"
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

    private fun buildSubjectCards() {
        val container = findViewById<LinearLayout>(R.id.subjectCardsContainer)
        container.removeAllViews()
        val career = goalPrefs.getGoal().careerPath
        val exam = AdmissionExamRegistry.get(career.examType)
        exam.subjects.forEachIndexed { index, subject ->
            container.addView(buildSubjectCard(subject.displayNameTr, addTopMargin = index > 0))
        }
    }

    private fun buildSubjectCard(subjectName: String, addTopMargin: Boolean): MaterialCardView {
        val dp = resources.displayMetrics.density
        val dp8 = (8 * dp + 0.5f).toInt()
        val dp12 = (12 * dp + 0.5f).toInt()
        val dp16 = (16 * dp + 0.5f).toInt()

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { if (addTopMargin) it.topMargin = dp8 }
            radius = 12 * dp
            cardElevation = 2 * dp
            setCardBackgroundColor(resources.getColor(R.color.white, theme))
            isClickable = true
            isFocusable = true
        }

        val inner = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp16, dp16, dp16, dp16)
        }

        val textContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }

        val titleView = TextView(this).apply {
            text = subjectName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(resources.getColor(R.color.textPrimary, theme))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val subtitleView = TextView(this).apply {
            text = getString(R.string.study_hub_subject_action)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(resources.getColor(R.color.textSecondary, theme))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (2 * dp + 0.5f).toInt() }
        }

        val arrow = TextView(this).apply {
            text = "›"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(resources.getColor(R.color.textSecondary, theme))
        }

        textContainer.addView(titleView)
        textContainer.addView(subtitleView)
        inner.addView(textContainer)
        inner.addView(arrow)
        card.addView(inner)

        card.onTap {
            val filter = com.mioacademy.app.quiz.SubjectFilter.forName(subjectName)
            startActivity(Intent(this, QuizActivity::class.java).also { intent ->
                filter?.let { intent.putExtra(QuizActivity.EXTRA_SUBJECT_FILTER, it) }
            })
        }

        return card
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
