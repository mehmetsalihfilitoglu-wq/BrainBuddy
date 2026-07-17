package com.edumio.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.edumio.app.core.CareerPath
import com.edumio.app.core.StudyAreaManager
import com.edumio.app.dailychallenge.DailyChallengeActivity
import com.edumio.app.dailychallenge.DailyChallengeBlueprint
import com.edumio.app.dailychallenge.DailyChallengeController
import com.edumio.app.dailychallenge.DailyChallengeHomePresenter
import com.edumio.app.dailychallenge.DailyChallengeUser
import com.edumio.app.quiz.QuizActivity
import com.edumio.app.ui.onTap
import com.edumio.app.core.GamificationStore
import com.edumio.app.core.UserGoalPrefs
import kotlinx.coroutines.launch
import com.edumio.app.quiz.WrongPoolLauncher
import com.edumio.app.quiz.WrongQuestionPoolStore
import com.edumio.app.ui.GrowthHubActivity
import com.edumio.app.ui.StudentProfileActivity
import com.edumio.app.ui.StudyHubActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var goalPrefs: UserGoalPrefs
    private lateinit var gam: GamificationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        goalPrefs = UserGoalPrefs(this)
        gam = GamificationStore(this)

        setupBackPress()
        setupNavigation()
        // Notification permission is requested contextually (after the first challenge completion),
        // not on launch — see DailyChallengeResultActivity.

        val content = findViewById<android.view.View>(R.id.scrollContent)
        val origBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, origBottom + navBottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun refreshAll() {
        // Rebind profile-scoped stores: after an in-place area switch the active
        // profile changed, so this must point at the new area's namespace.
        gam = GamificationStore(this)
        refreshHeader()
        refreshDailyChallenge()
        refreshNextStep()
        refreshIdentityHero()
        refreshInsight()
        refreshWrongPool()
    }

    /** Binds the "Günün Görevi" (Today's Challenge) hero card from the tested engine. */
    private fun refreshDailyChallenge() {
        val card = findViewById<MaterialCardView>(R.id.dcHomeCard)
        val state = findViewById<TextView>(R.id.dcHomeState)
        val meta = findViewById<TextView>(R.id.dcHomeMeta)
        val countdown = findViewById<TextView>(R.id.dcHomeCountdown)
        val progressBar = findViewById<ProgressBar>(R.id.dcHomeProgressBar)
        val cta = findViewById<Button>(R.id.dcHomeCta)

        val exam = StudyAreaManager.getActiveArea(this).career.examType
        if (!DailyChallengeBlueprint.isSupported(exam)) {
            state.setText(R.string.dc_state_unavailable)
            progressBar.visibility = View.GONE
            meta.visibility = View.GONE
            countdown.visibility = View.GONE
            cta.visibility = View.GONE
            card.setOnClickListener(null)
            return
        }
        cta.visibility = View.VISIBLE

        val controller = DailyChallengeController(this)
        val userId = DailyChallengeUser.resolve(this)
        lifecycleScope.launch {
            val ui = try { controller.today(userId, exam) } catch (_: Throwable) { null }
            val streak = try { controller.streak(userId) } catch (_: Throwable) { 0 }
            val streakText = if (streak > 0) getString(R.string.dc_streak_label, streak)
            else getString(R.string.dc_streak_none)

            if (ui == null) {
                // No unseen questions could be formed today (pool exhausted / edge case).
                state.setText(R.string.dc_empty_today)
                progressBar.visibility = View.GONE
                meta.visibility = View.VISIBLE; meta.text = streakText
                countdown.visibility = View.GONE
                cta.isEnabled = false; cta.setText(R.string.dc_cta_done)
                card.setOnClickListener(null)
                return@launch
            }

            val answered = ui.answered
            val total = ui.total
            val open = View.OnClickListener { startActivity(DailyChallengeActivity.intent(this@HomeActivity)) }
            progressBar.visibility = View.VISIBLE
            progressBar.max = total; progressBar.progress = answered
            meta.visibility = View.VISIBLE

            when (DailyChallengeHomePresenter.cardState(true, answered, total, ui.completed)) {
                DailyChallengeHomePresenter.CardState.COMPLETED -> {
                    state.setText(R.string.dc_state_completed)
                    meta.text = streakText
                    val cd = DailyChallengeHomePresenter.countdownText(ui.nextUnlockAtMs, System.currentTimeMillis())
                    if (cd.isNotEmpty()) {
                        countdown.visibility = View.VISIBLE
                        countdown.text = getString(R.string.dc_next_unlock, cd)
                    } else countdown.visibility = View.GONE
                    // Completed: no new questions today — offer review instead when the queue has items.
                    val reviewCount = try { controller.reviewCount(userId, exam) } catch (_: Throwable) { 0 }
                    if (reviewCount > 0) {
                        cta.isEnabled = true; cta.setText(R.string.dc_cta_review)
                        val openReview = View.OnClickListener {
                            startActivity(com.edumio.app.dailychallenge.DailyChallengeReviewActivity.intent(this@HomeActivity))
                        }
                        cta.onTap { openReview.onClick(it) }; card.onTap { openReview.onClick(it) }
                    } else {
                        cta.isEnabled = false; cta.setText(R.string.dc_cta_done)
                        card.setOnClickListener(null)
                    }
                }
                DailyChallengeHomePresenter.CardState.IN_PROGRESS -> {
                    state.setText(R.string.dc_state_in_progress)
                    meta.text = getString(
                        R.string.dc_meta_fmt,
                        DailyChallengeHomePresenter.progressText(answered, total),
                        DailyChallengeHomePresenter.estimatedDurationText(total), streakText,
                    )
                    countdown.visibility = View.GONE
                    cta.isEnabled = true; cta.setText(R.string.dc_cta_continue)
                    cta.onTap { open.onClick(it) }; card.onTap { open.onClick(it) }
                }
                else -> { // AVAILABLE
                    state.setText(R.string.dc_state_available)
                    meta.text = getString(
                        R.string.dc_meta_fmt,
                        DailyChallengeHomePresenter.progressText(answered, total),
                        DailyChallengeHomePresenter.estimatedDurationText(total), streakText,
                    )
                    countdown.visibility = View.GONE
                    cta.isEnabled = true; cta.setText(R.string.dc_cta_start)
                    cta.onTap { open.onClick(it) }; card.onTap { open.onClick(it) }
                }
            }
        }
    }

    private fun refreshHeader() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val salutation = when {
            hour in 5..11 -> "Buongiorno"
            hour in 12..17 -> "Buon pomeriggio"
            else -> "Buonasera"
        }
        val name = goalPrefs.getStudentName().trim()
        val greeting = if (name.isNotBlank()) "$salutation, $name!" else "$salutation!"
        findViewById<TextView>(R.id.greeting).text = greeting

        val dayFmt = SimpleDateFormat("d MMMM · EEEE", Locale("tr"))
        findViewById<TextView>(R.id.tvDate).text = dayFmt.format(Date())
    }

    private fun refreshIdentityHero() {
        val goal = goalPrefs.getGoal()
        val career = goal.careerPath

        val freeze = if (gam.freezeTokens() > 0) " 🧊" else ""
        val streakDays = gam.streakDays()

        findViewById<TextView>(R.id.tvCareerIdentity).text =
            "${career.emoji} ${journeyTitleFor(career)}"
        findViewById<TextView>(R.id.tvItalianDegree).text = career.italianDegreeName

        // Never punish a zero/broken streak — invite instead of shaming.
        val streakLabel = when {
            streakDays <= 0 -> getString(R.string.home_streak_start)
            streakDays == 1 -> "1 gün$freeze"
            else -> "$streakDays gün$freeze"
        }
        findViewById<TextView>(R.id.streakBadge).text = "🔥 $streakLabel"
        findViewById<TextView>(R.id.pointsBadge).text = "⭐ ${gam.xp()} XP"
        findViewById<TextView>(R.id.levelBadge).text = "Lv. ${gam.level()}"
    }

    /**
     * The decided next action — the app already knows what to study. Computed
     * from real, area-scoped data (weakest topic / pending reviews / mission).
     */
    private fun refreshNextStep() {
        val step = com.edumio.app.core.NextStepEngine(this).compute()

        findViewById<TextView>(R.id.tvStepTitle).text = step.title
        findViewById<TextView>(R.id.tvStepMeta).text = step.meta

        val target = step.missionTarget.coerceAtLeast(1)
        val done = step.missionDone.coerceIn(0, target)
        findViewById<ProgressBar>(R.id.missionProgressBar).apply {
            max = target
            progress = done
        }
        findViewById<TextView>(R.id.tvMissionProgress).text =
            getString(R.string.home_mission_progress, done, target)

        val btn = findViewById<MaterialButton>(R.id.btnStepStart)
        btn.text = step.actionLabel
        btn.onTap {
            when (step.kind) {
                com.edumio.app.core.NextStepEngine.Kind.REVIEW -> WrongPoolLauncher.launch(this)
                else -> startActivity(Intent(this, QuizActivity::class.java).also { i ->
                    step.subjectFilter?.let { i.putExtra(QuizActivity.EXTRA_SUBJECT_FILTER, it) }
                    step.missionCategories?.takeIf { it.isNotEmpty() }?.let {
                        i.putExtra(QuizActivity.EXTRA_MISSION_CATEGORIES, it.joinToString(","))
                    }
                })
            }
        }
    }

    /** One compact, real-data insight; the card is hidden when there is no data. */
    private fun refreshInsight() {
        val card = findViewById<MaterialCardView>(R.id.cardHomeInsight)
        val tv = findViewById<TextView>(R.id.tvHomeInsight)
        val insight = com.edumio.app.core.ProgressInsights(this).homeInsight()
        if (insight == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        tv.text = insight.text
        val warning = insight.tone == com.edumio.app.core.ProgressInsights.Tone.WARNING
        card.setCardBackgroundColor(
            androidx.core.content.ContextCompat.getColor(this, if (warning) R.color.warning_soft else R.color.emeraldSoft))
        tv.setTextColor(
            androidx.core.content.ContextCompat.getColor(this, if (warning) R.color.warning_text else R.color.emeraldDark))
    }

    private fun refreshWrongPool() {
        val pool = WrongQuestionPoolStore(this)
        val card = findViewById<MaterialCardView>(R.id.cardWrongPool)
        card.visibility = if (pool.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupNavigation() {
        findViewById<MaterialCardView>(R.id.cardIdentityHero).onTap {
            com.edumio.app.ui.AreaSwitcher.show(this) { refreshAll() }
        }
        findViewById<MaterialCardView>(R.id.cardWrongPool).onTap {
            WrongPoolLauncher.launch(this)
        }
        findViewById<View>(R.id.btnSettings).onTap {
            startActivity(Intent(this, com.edumio.app.ui.SettingsActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardStudy).onTap {
            startActivity(Intent(this, StudyHubActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardProgress).onTap {
            startActivity(Intent(this, GrowthHubActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardProfile).onTap {
            startActivity(Intent(this, StudentProfileActivity::class.java))
        }
    }

    private fun setupBackPress() {
        var lastBackPressMs = 0L
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs < 3000) {
                    finishAffinity()
                } else {
                    lastBackPressMs = now
                    android.widget.Toast.makeText(
                        this@HomeActivity,
                        getString(R.string.back_exit_hint),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun journeyTitleFor(career: CareerPath): String = when (career) {
        CareerPath.MEDICINE -> "Tıp Yolculuğu"
        CareerPath.DENTISTRY -> "Diş Hekimliği Yolculuğu"
        CareerPath.ENGINEERING -> "Mühendislik Yolculuğu"
        CareerPath.COMPUTER_SCIENCE -> "Bilgisayar Bilimi Yolculuğu"
        CareerPath.ARCHITECTURE -> "Mimarlık Yolculuğu"
        CareerPath.ECONOMICS -> "Ekonomi Yolculuğu"
        CareerPath.LAW -> "Hukuk Yolculuğu"
        CareerPath.PHARMACY -> "Eczacılık Yolculuğu"
        CareerPath.BIOLOGY -> "Biyoloji Yolculuğu"
        CareerPath.PSYCHOLOGY -> "Psikoloji Yolculuğu"
        CareerPath.VETERINARY -> "Veteriner Yolculuğu"
        CareerPath.MATHEMATICS -> "Matematik Yolculuğu"
        CareerPath.DESIGN -> "Tasarım Yolculuğu"
        CareerPath.OTHER -> "İtalya Yolculuğu"
    }
}
