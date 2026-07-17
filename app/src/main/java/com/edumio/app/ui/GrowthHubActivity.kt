package com.edumio.app.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.edumio.app.R
import com.edumio.app.StatsActivity
import com.edumio.app.core.AchievementEngine
import com.edumio.app.core.AnalyticsStore
import com.edumio.app.core.GamificationStore
import com.edumio.app.core.ProgressInsights
import com.edumio.app.core.WeeklyRewardStore
import com.edumio.app.quiz.WrongPoolLauncher
import com.edumio.app.quiz.WrongQuestionPoolStore
import com.edumio.app.social.LeagueScreen
import kotlin.math.roundToInt

class GrowthHubActivity : AppCompatActivity() {

    private lateinit var gam: GamificationStore
    private lateinit var analytics: AnalyticsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_growth_hub)

        gam = GamificationStore(this)
        analytics = AnalyticsStore(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        val content = findViewById<View>(R.id.scrollContent)
        val origBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, origBottom + navBottom)
            insets
        }

        setupNavigation()
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshHeroStats()
        buildWeeklyPanel()
        refreshWrongPool()
    }

    private fun refreshAll() {
        refreshHeroStats()
        buildWeeklyPanel()
        refreshTopics()
        refreshWrongPool()
    }

    // ── Real-data weekly panel + insights + achievements ───────────────────────

    private val dp get() = resources.displayMetrics.density
    private fun dpi(v: Float) = (v * dp + 0.5f).toInt()

    private fun buildWeeklyPanel() {
        val container = findViewById<LinearLayout>(R.id.weeklyPanelContainer)
        container.removeAllViews()
        // Fresh engines each build → active-area scoped, picks up the current area.
        val insights = ProgressInsights(this)

        // Warm, long-horizon lead — "how far you've come" — before anything else.
        if (insights.hasAnyData()) {
            com.edumio.app.core.ProgressAffirmations(this).headline()?.let {
                container.addView(headlineCard("✨  $it"))
            }
        }

        container.addView(sectionLabel(getString(R.string.progress_week_section)))
        if (!insights.hasAnyData()) {
            container.addView(infoCard(getString(R.string.progress_week_empty)))
            return
        }

        // Felt growth first — a human sentence — then the numbers as support.
        insights.weeklyHeadline()?.let { container.addView(headlineCard(it)) }
        container.addView(weeklyStatsCard(insights.weekly()))

        buildPremiumSection(container, insights)

        val ach = AchievementEngine(this)
        val unlocked = ach.unlocked()
        val next = ach.nextMilestone()
        container.addView(sectionLabel(getString(R.string.progress_achievements_section)))
        if (unlocked.isEmpty() && next == null) {
            container.addView(infoCard(getString(R.string.progress_no_data_yet)))
        } else {
            if (unlocked.isNotEmpty()) container.addView(achievementsCard(unlocked))
            next?.let { container.addView(nextMilestoneCard(it)) }
        }
    }

    private fun weeklyStatsCard(w: ProgressInsights.WeeklyStats): MaterialCardView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(statCol("${w.questions}", getString(R.string.progress_week_questions), deltaText(w.questionsDelta, w.questionsPrev > 0)))
        row.addView(divider())
        row.addView(statCol("${w.xp}", getString(R.string.progress_week_xp), null))
        row.addView(divider())
        row.addView(statCol(w.accuracy?.let { "%$it" } ?: "—", getString(R.string.progress_week_accuracy),
            w.accuracyDelta?.let { deltaText(it, true, suffix = "%") }))
        return cardWrap(row)
    }

    /** e.g. "geçen haftaya göre +8"; null when there's nothing meaningful to compare. */
    private fun deltaText(delta: Int, hasPrev: Boolean, suffix: String = ""): CharSequence? {
        if (!hasPrev || delta == 0) return null
        val sign = if (delta > 0) "+" else ""
        return getString(R.string.progress_vs_last_week, "$sign$delta$suffix")
    }

    private fun statCol(value: String, label: String, delta: CharSequence?): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(text(value, 20f, R.color.textPrimary, bold = true, gravity = Gravity.CENTER))
        col.addView(text(label, 11f, R.color.textSecondary, topMargin = dpi(2f), gravity = Gravity.CENTER))
        if (delta != null) {
            col.addView(text(delta.toString(), 10f, R.color.emeraldDark, topMargin = dpi(2f), gravity = Gravity.CENTER))
        }
        return col
    }

    /**
     * Premium intelligence: Exam Readiness + long-window interpretive analysis.
     * Free users see a value-focused (not pushy) upsell; premium sees the real,
     * data-computed insights. Everything active-area scoped.
     */
    private fun buildPremiumSection(container: LinearLayout, insights: ProgressInsights) {
        container.addView(sectionLabel(getString(R.string.premium_analysis_section)))
        if (!com.edumio.app.core.PremiumStore(this).isPremium()) {
            container.addView(upsellCard())
            return
        }
        val r = com.edumio.app.core.ExamReadinessEngine(this).compute()
        if (r.hasEnoughData) {
            // Record today's readiness so reports/coach can show honest change over time.
            com.edumio.app.core.ReadinessSnapshotStore(this).captureIfNewDay(r.score)
            container.addView(readinessCard(r))
        } else container.addView(infoCard(getString(R.string.readiness_not_enough)))
        insights.premiumAnalysis().take(3).forEach { i ->
            container.addView(headlineToneCard(i))
        }
        buildCoachTools(container)
    }

    /**
     * The personal-coach surfaces: today's ordered plan, where each question sits on
     * its mastery path, a real study heatmap, and the learning-journey timeline.
     * All derived from the active area's real activity; each panel hides itself when
     * there's nothing real to show yet.
     */
    private fun buildCoachTools(container: LinearLayout) {
        val plan = com.edumio.app.core.StudyPlanEngine(this).today()
        if (plan.isNotEmpty()) {
            container.addView(sectionLabel(getString(R.string.plan_today_section)))
            container.addView(planCard(plan))
        }

        val states = com.edumio.app.quiz.WrongQuestionScheduler(this).learningStates()
        if (states.total > 0) {
            container.addView(sectionLabel(getString(R.string.learning_states_section)))
            container.addView(learningStatesCard(states))
        }

        val heat = com.edumio.app.core.StudyHeatmap.compute(this)
        if (heat.activeDaysTotal > 0) {
            container.addView(sectionLabel(getString(R.string.heatmap_section)))
            container.addView(heatmapCard(heat))
        }

        container.addView(sectionLabel(getString(R.string.journey_section)))
        container.addView(journeyCard(com.edumio.app.core.LearningJourney.milestones(this)))
    }

    private fun planCard(steps: List<com.edumio.app.core.StudyPlanEngine.Step>): MaterialCardView {
        val col = paddedCol()
        steps.forEachIndexed { idx, s ->
            val rowV = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { if (idx > 0) it.topMargin = dpi(12f) }
            }
            rowV.addView(text(if (s.done) "✓" else "${s.index}", 14f,
                if (s.done) R.color.emeraldDark else R.color.textSecondary, bold = true))
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dpi(12f) }
            }
            c.addView(text(s.title, 14f, if (s.done) R.color.textSecondary else R.color.textPrimary, bold = !s.done))
            c.addView(text(s.detail, 12f, R.color.textSecondary, topMargin = dpi(1f)))
            rowV.addView(c)
            col.addView(rowV)
        }
        return cardWrap(col)
    }

    private fun learningStatesCard(s: com.edumio.app.quiz.WrongQuestionScheduler.LearningStates): MaterialCardView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(statCol("${s.wrong}", getString(R.string.learning_state_wrong), null))
        row.addView(divider())
        row.addView(statCol("${s.reviewing}", getString(R.string.learning_state_reviewing), null))
        row.addView(divider())
        row.addView(statCol("${s.mastered}", getString(R.string.learning_state_mastered), null))
        return cardWrap(row)
    }

    private fun heatmapCard(d: com.edumio.app.core.StudyHeatmap.Data): MaterialCardView {
        val col = paddedCol()
        col.addView(text(getString(R.string.heatmap_summary, d.longestStreak, d.activeDaysThisMonth),
            12f, R.color.textSecondary))
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(10f) }
        }
        val cell = dpi(11f)
        val gap = dpi(3f)
        d.weeks.forEach { week ->
            val colV = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            week.forEach { day ->
                colV.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(cell, cell).also { it.setMargins(0, 0, gap, gap) }
                    background = roundedSquare(heatColor(day?.level ?: -1))
                })
            }
            grid.addView(colV)
        }
        col.addView(grid)
        col.addView(heatLegend())
        return cardWrap(col)
    }

    private fun heatLegend(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(10f) }
        }
        row.addView(text(getString(R.string.heatmap_less), 10f, R.color.textSecondary))
        for (lvl in 0..4) {
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpi(10f), dpi(10f))
                    .also { it.setMargins(dpi(3f), 0, dpi(3f), 0) }
                background = roundedSquare(heatColor(lvl))
            })
        }
        row.addView(text(getString(R.string.heatmap_more), 10f, R.color.textSecondary))
        return row
    }

    private fun heatColor(level: Int): Int {
        val base = resources.getColor(R.color.emerald, theme)
        return when (level) {
            -1 -> resources.getColor(R.color.divider, theme)   // outside range / future
            0 -> resources.getColor(R.color.emeraldSoft, theme) // studied nothing that day
            1 -> withAlpha(base, 0x55)
            2 -> withAlpha(base, 0x99)
            3 -> withAlpha(base, 0xCC)
            else -> base
        }
    }

    private fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a shl 24)

    private fun roundedSquare(color: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = 3f * dp
        }

    private fun journeyCard(milestones: List<com.edumio.app.core.LearningJourney.Milestone>): MaterialCardView {
        val col = paddedCol()
        milestones.forEachIndexed { idx, m ->
            val rowV = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { if (idx > 0) it.topMargin = dpi(12f) }
            }
            rowV.addView(text(if (m.reached) "●" else "○", 14f,
                if (m.reached) R.color.emeraldDark else R.color.textSecondary, bold = true))
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dpi(12f) }
            }
            c.addView(text(m.label, 14f, if (m.reached) R.color.textPrimary else R.color.textSecondary, bold = m.reached))
            c.addView(text(m.detail, 12f, R.color.textSecondary, topMargin = dpi(1f)))
            rowV.addView(c)
            col.addView(rowV)
        }
        return cardWrap(col)
    }

    private fun upsellCard(): MaterialCardView {
        val col = paddedCol()
        col.addView(text("✦ ${getString(R.string.premium_upsell_title)}", 15f, R.color.emeraldDark, bold = true))
        col.addView(text(getString(R.string.premium_upsell_body), 13f, R.color.textPrimary,
            topMargin = dpi(6f), lineMultiplier = 1.5f))
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(8f) }
            radius = 16 * dp
            cardElevation = 0f
            strokeWidth = dpi(1f)
            setStrokeColor(resources.getColor(R.color.emerald, theme))
            setCardBackgroundColor(resources.getColor(R.color.emeraldSoft, theme))
            isClickable = true; isFocusable = true
            setOnClickListener {
                com.edumio.app.quiz.PremiumPaywallSheet().show(supportFragmentManager, com.edumio.app.quiz.PremiumPaywallSheet.TAG)
            }
            addView(col)
        }
        return card
    }

    private fun readinessCard(r: com.edumio.app.core.ExamReadinessEngine.Readiness): MaterialCardView {
        val col = paddedCol()
        col.addView(text(getString(R.string.readiness_title), 10f, R.color.emeraldDark, bold = true, letterSpacing = 0.12f))
        // Big score
        val scoreRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(4f) }
        }
        scoreRow.addView(text("${r.score}", 40f, R.color.emeraldDark, bold = true))
        scoreRow.addView(text(" / 100", 16f, R.color.textSecondary).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dpi(6f)
        })
        col.addView(scoreRow)
        col.addView(text(r.headline, 13f, R.color.textPrimary, topMargin = dpi(4f), lineMultiplier = 1.4f))
        // Weekly change teaches the score: is readiness rising?
        val weekAgo = com.edumio.app.core.ReadinessSnapshotStore(this)
            .scoreOnOrBefore(System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(6))
        if (weekAgo != null && weekAgo != r.score) {
            val delta = r.score - weekAgo
            col.addView(text(getString(R.string.readiness_week_change, (if (delta > 0) "+" else "") + delta),
                12f, if (delta > 0) R.color.emeraldDark else R.color.textSecondary, bold = true, topMargin = dpi(6f)))
        }
        // Factor rows
        r.factors.forEach { f ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dpi(6f) }
            }
            row.addView(text(f.label, 12f, R.color.textSecondary).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(text("%${f.percent}", 12f, R.color.textPrimary, bold = true))
            col.addView(row)
        }
        r.biggestOpportunity?.let {
            col.addView(text(getString(R.string.readiness_opportunity, it), 12f, R.color.emeraldDark,
                bold = true, topMargin = dpi(10f)))
        }
        return cardWrap(col)
    }

    private fun headlineToneCard(i: ProgressInsights.Insight): MaterialCardView {
        val fg = if (i.tone == ProgressInsights.Tone.WARNING) R.color.warning_text else R.color.textPrimary
        return cardWrap(text(i.text, 13f, fg, lineMultiplier = 1.45f).apply {
            setPadding(dpi(16f), dpi(14f), dpi(16f), dpi(14f))
        })
    }

    /** Prominent, human weekly summary sentence — the "felt growth" lead. */
    private fun headlineCard(sentence: String): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            radius = 16 * dp
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(resources.getColor(R.color.emeraldSoft, theme))
        }
        val tv = text(sentence, 15f, R.color.emeraldDark, bold = true, lineMultiplier = 1.4f).apply {
            setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        }
        card.addView(tv)
        return card
    }

    private fun achievementsCard(list: List<AchievementEngine.Achievement>): MaterialCardView {
        val col = paddedCol()
        list.forEachIndexed { idx, a ->
            val rowV = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { if (idx > 0) it.topMargin = dpi(10f) }
            }
            rowV.addView(text(a.icon, 18f, R.color.textPrimary))
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dpi(12f) }
            }
            c.addView(text(a.title, 14f, R.color.textPrimary, bold = true))
            c.addView(text(a.description, 12f, R.color.textSecondary, topMargin = dpi(1f)))
            rowV.addView(c)
            rowV.addView(text("✓", 16f, R.color.emeraldDark, bold = true))
            col.addView(rowV)
        }
        return cardWrap(col)
    }

    private fun nextMilestoneCard(a: AchievementEngine.Achievement): MaterialCardView {
        val col = paddedCol()
        col.addView(text(getString(R.string.progress_next_milestone), 10f, R.color.emeraldDark, bold = true, letterSpacing = 0.12f))
        val rowV = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(8f) }
        }
        rowV.addView(text(a.icon, 18f, R.color.textPrimary))
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginStart = dpi(12f) }
        }
        c.addView(text(a.title, 14f, R.color.textPrimary, bold = true))
        c.addView(text(a.description, 12f, R.color.textSecondary, topMargin = dpi(1f)))
        rowV.addView(c)
        rowV.addView(text("${a.current}/${a.target}", 13f, R.color.emeraldDark, bold = true))
        col.addView(rowV)
        return cardWrap(col.also { it.setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f)) })
    }

    // ── small view builders ──
    private fun sectionLabel(t: String) = text(t, 10f, R.color.emeraldDark, bold = true, letterSpacing = 0.12f,
        topMargin = dpi(24f), bottomMargin = dpi(12f))

    private fun infoCard(t: String): MaterialCardView =
        cardWrap(text(t, 13f, R.color.textSecondary, lineMultiplier = 1.45f).apply {
            setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        })

    private fun cardWrap(child: View): MaterialCardView = MaterialCardView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpi(8f) }
        radius = 16 * dp
        cardElevation = 0f
        strokeWidth = dpi(1f)
        setStrokeColor(resources.getColor(R.color.border, theme))
        setCardBackgroundColor(resources.getColor(R.color.white, theme))
        addView(child)
    }

    private fun paddedCol(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dpi(1f), dpi(28f)).also {
            it.topMargin = dpi(16f); it.bottomMargin = dpi(16f)
        }
        setBackgroundColor(resources.getColor(R.color.divider, theme))
    }

    private fun text(
        t: String, size: Float, colorRes: Int, bold: Boolean = false,
        topMargin: Int = 0, bottomMargin: Int = 0, gravity: Int = Gravity.NO_GRAVITY,
        letterSpacing: Float = 0f, lineMultiplier: Float = 1.1f
    ): TextView = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(resources.getColor(colorRes, theme))
        if (bold) setTypeface(null, Typeface.BOLD)
        if (gravity != Gravity.NO_GRAVITY) this.gravity = gravity
        if (letterSpacing > 0) this.letterSpacing = letterSpacing
        setLineSpacing(0f, lineMultiplier)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = topMargin; it.bottomMargin = bottomMargin }
    }

    private fun refreshHeroStats() {
        val freezeSuffix = if (gam.freezeTokens() > 0) " 🧊" else ""
        findViewById<TextView>(R.id.tvHeroStreak).text = "${gam.streakDays()}$freezeSuffix"
        findViewById<TextView>(R.id.tvHeroXp).text = "${gam.xp()}"
        findViewById<TextView>(R.id.tvHeroLevel).text = "${gam.level()}"

        val counts = analytics.getOverallCounts()
        val accuracy = analytics.getOverallAccuracy()
        findViewById<TextView>(R.id.tvHeroAccuracy).text =
            if (counts.total > 0) "%${accuracy.roundToInt()}" else "—"
    }

    private fun refreshTopics() {
        val strongest = analytics.getStrongestTopicsWithCounts(3)
        val weakest = analytics.getWeakestTopicsWithCounts(3).filter { it.second.total >= 3 }

        val chipGroupStrong = findViewById<ChipGroup>(R.id.chipGroupStrong)
        val tvStrongEmpty = findViewById<TextView>(R.id.tvStrongEmpty)
        chipGroupStrong.removeAllViews()
        if (strongest.isEmpty()) {
            tvStrongEmpty.visibility = View.VISIBLE
        } else {
            tvStrongEmpty.visibility = View.GONE
            strongest.forEach { (topic, tc) ->
                chipGroupStrong.addView(Chip(this, null, R.style.Widget_EDUmio_Chip_Stat).apply {
                    text = "$topic  ${tc.correct}/${tc.total}"
                    isClickable = false
                    isCheckable = false
                })
            }
        }

        val chipGroupWeak = findViewById<ChipGroup>(R.id.chipGroupWeak)
        val tvWeakEmpty = findViewById<TextView>(R.id.tvWeakEmpty)
        chipGroupWeak.removeAllViews()
        if (weakest.isEmpty()) {
            tvWeakEmpty.visibility = View.VISIBLE
        } else {
            tvWeakEmpty.visibility = View.GONE
            weakest.forEach { (topic, tc) ->
                chipGroupWeak.addView(Chip(this, null, R.style.Widget_EDUmio_Chip_Stat).apply {
                    text = "$topic  ${tc.correct}/${tc.total}"
                    isClickable = false
                    isCheckable = false
                })
            }
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

    private fun setupNavigation() {
        val weeklyReward = WeeklyRewardStore(this)

        findViewById<MaterialCardView>(R.id.cardDetailedStats).onTap {
            startActivity(Intent(this, StatsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        findViewById<MaterialCardView>(R.id.cardLeague).onTap {
            startActivity(Intent(this, LeagueScreen::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardWeeklyChest).onTap {
            val tokens = weeklyReward.claimWeeklyChest()
            if (tokens > 0) {
                android.widget.Toast.makeText(this, "+$tokens donma jetonu!", android.widget.Toast.LENGTH_SHORT).show()
            } else if (weeklyReward.canClaimWeeklyChest()) {
                android.widget.Toast.makeText(this, getString(R.string.progress_chest_xp_hint), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
