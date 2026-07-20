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
        val mascot = findViewById<android.widget.ImageView>(R.id.dcHomeMascot)
        fun setMascot(e: com.edumio.app.ui.EduMascot.Expression) =
            mascot.setImageResource(com.edumio.app.ui.EduMascot.drawable(e))

        // The active exam is used ONLY to generate today's challenge if none exists yet. If a challenge
        // already exists (possibly generated from a different exam), today() returns it regardless — so
        // switching exams never hides or regenerates it. today() is null only when there is genuinely
        // nothing to show (unsupported active exam AND no existing challenge, or pool exhausted).
        val exam = StudyAreaManager.getActiveArea(this).career.examType
        cta.visibility = View.VISIBLE

        val controller = DailyChallengeController(this)
        lifecycleScope.launch {
            // First sign-in of a previously-anonymous session: adopt the anon challenge into the account
            // BEFORE resolving the id, so signing in never regenerates today's challenge.
            com.edumio.app.dailychallenge.DailyChallengeAccountLink.linkIfNeeded(this@HomeActivity)
            val userId = DailyChallengeUser.resolve(this@HomeActivity)
            val ui = try { controller.today(userId, exam) } catch (_: Throwable) { null }
            val streak = try { controller.streak(userId) } catch (_: Throwable) { 0 }
            val streakText = if (streak > 0) getString(R.string.dc_streak_label, streak)
            else getString(R.string.dc_streak_none)

            if (ui == null) {
                progressBar.visibility = View.GONE
                meta.visibility = View.VISIBLE; meta.text = streakText
                countdown.visibility = View.GONE
                setMascot(com.edumio.app.ui.EduMascot.Expression.SLEEPING)
                if (!com.edumio.app.dailychallenge.DailyChallengeBlueprint.isSupported(exam)) {
                    // The active study area has no daily-challenge blueprint yet: this is NOT "completed" —
                    // invite the student to free practice instead of showing a misleading "done" state.
                    state.setText(R.string.dc_state_unavailable)
                    cta.isEnabled = true; cta.setText(R.string.dc_cta_practice)
                    val practice = View.OnClickListener { startActivity(Intent(this@HomeActivity, QuizActivity::class.java)) }
                    cta.onTap { practice.onClick(it) }; card.onTap { practice.onClick(it) }
                } else {
                    // Supported exam but genuinely no unseen questions left today (pool exhausted).
                    state.setText(R.string.dc_empty_today)
                    cta.isEnabled = false; cta.setText(R.string.dc_cta_done)
                    card.setOnClickListener(null)
                }
                return@launch
            }
            setMascot(com.edumio.app.ui.EduMascot.forHome(available = !ui.completed, completed = ui.completed))

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
                    // Review is scoped to the exam the challenge belongs to (ui.exam), not necessarily the active one.
                    val reviewCount = try { controller.reviewCount(userId, ui.exam) } catch (_: Throwable) { 0 }
                    if (reviewCount > 0) {
                        cta.isEnabled = true; cta.setText(R.string.dc_cta_review)
                        val openReview = View.OnClickListener {
                            // Premium: the two-path wrong-question hub. Free: the capped review flow.
                            startActivity(
                                if (com.edumio.app.dailychallenge.DailyChallengeEntitlement.isPremiumForSolutions(this@HomeActivity))
                                    com.edumio.app.dailychallenge.WrongQuestionsActivity.intent(this@HomeActivity)
                                else
                                    com.edumio.app.dailychallenge.DailyChallengeReviewActivity.intent(this@HomeActivity, ui.exam)
                            )
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

        val streakDays = gam.streakDays()

        findViewById<TextView>(R.id.tvCareerIdentity).text =
            "${career.emoji} ${journeyTitleFor(career)}"
        findViewById<TextView>(R.id.tvItalianDegree).text = career.italianDegreeName

        // Never punish a zero/broken streak — invite instead of shaming.
        val streakLabel = when {
            streakDays <= 0 -> getString(R.string.home_streak_start)
            streakDays == 1 -> "1 gün"
            else -> "$streakDays gün"
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

        // The daily task and its progress live on the Günün Görevi (Daily Challenge) hero above; this
        // "next step" card is the single secondary action, so it no longer shows a separate mission
        // counter (the old "0/2 tamamlandı", which read as a confusing second daily goal).
        findViewById<ProgressBar>(R.id.missionProgressBar).visibility = View.GONE
        findViewById<TextView>(R.id.tvMissionProgress).visibility = View.GONE

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
        // Active study area is INFO-ONLY on Home (no quick "Değiştir"). Switching areas is a deliberate
        // setting under Profile → Çalışma Alanlarım (StudyAreasActivity.setActiveArea).
        findViewById<MaterialCardView>(R.id.cardWrongPool).onTap {
            WrongPoolLauncher.launch(this)
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
