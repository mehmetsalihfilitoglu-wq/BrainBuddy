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
import com.edumio.app.core.ExamType
import com.edumio.app.core.StudyAreaManager
import com.edumio.app.db.DbSeeder
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
            val supported = com.edumio.app.dailychallenge.DailyChallengeBlueprint.isSupported(exam)
            var ui = try { controller.today(userId, exam) } catch (_: Throwable) { null }
            if (ui == null && supported) {
                // Fresh install: the exam's question bank may not have finished seeding yet — the banks are
                // seeded asynchronously at startup, TIL-I / CEnT-S LAST. Seed it idempotently and retry ONCE
                // so a first-time user lands on an AVAILABLE challenge, never a spurious "completed"/empty
                // state caused by the pool being momentarily empty.
                ensureExamBankSeeded(exam)
                ui = try { controller.today(userId, exam) } catch (_: Throwable) { null }
            }
            val streak = try { controller.streak(userId) } catch (_: Throwable) { 0 }
            val streakText = if (streak > 0) getString(R.string.dc_streak_label, streak)
            else getString(R.string.dc_streak_none)

            if (ui == null) {
                // today() == null is NEVER "completed": a completed challenge is a persisted row and always
                // comes back non-null, so null means today's challenge could not be built. Split the real
                // cases so a fresh user never sees "Tamamlandı" / "Bugünlük yeni soru kalmadı".
                progressBar.visibility = View.GONE
                meta.visibility = View.VISIBLE; meta.text = streakText
                countdown.visibility = View.GONE
                setMascot(com.edumio.app.ui.EduMascot.Expression.SLEEPING)
                when (DailyChallengeHomePresenter.emptyState(supported)) {
                    DailyChallengeHomePresenter.CardState.ERROR -> {
                        // Supported exam, but the bank could not be loaded/built → controlled error + retry.
                        state.setText(R.string.dc_state_error)
                        cta.visibility = View.VISIBLE
                        cta.isEnabled = true; cta.setText(R.string.dc_cta_retry)
                        val retry = View.OnClickListener { refreshDailyChallenge() }
                        cta.onTap { retry.onClick(it) }; card.onTap { retry.onClick(it) }
                    }
                    else -> {
                        // Unsupported active exam — no content for it yet. Not "completed"; nothing to start.
                        state.setText(R.string.dc_state_unavailable)
                        cta.visibility = View.GONE
                        card.setOnClickListener(null)
                    }
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
        val exam = goal.careerPath.examType

        val streakDays = gam.streakDays()

        // v1 is exam-framed: show ONLY the active exam (IMAT / TIL-I / CEnT-S). The old career-journey
        // title ("Mühendislik Yolculuğu") and degree name ("Ingegneria") are never shown.
        findViewById<TextView>(R.id.tvCareerIdentity).text = "🇮🇹 ${exam.code}"
        findViewById<TextView>(R.id.tvItalianDegree).visibility = View.GONE

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
        // v1's only new-question action is the Daily Challenge (the hero card above). This "next step"
        // card is now purely a shortcut to wrong-question REVIEW; for any other kind (which would have
        // started a separate practice session) it is hidden entirely rather than offering a second flow.
        val nextStepCard = findViewById<MaterialCardView>(R.id.cardNextStep)
        if (step.kind != com.edumio.app.core.NextStepEngine.Kind.REVIEW) {
            nextStepCard.visibility = View.GONE
            return
        }
        nextStepCard.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvStepMeta).text = step.meta
        findViewById<ProgressBar>(R.id.missionProgressBar).visibility = View.GONE
        findViewById<TextView>(R.id.tvMissionProgress).visibility = View.GONE

        val btn = findViewById<MaterialButton>(R.id.btnStepStart)
        btn.text = step.actionLabel
        btn.onTap {
            when (step.kind) {
                com.edumio.app.core.NextStepEngine.Kind.REVIEW -> WrongPoolLauncher.launch(this)
                else -> Unit
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

    /**
     * Idempotently seeds the active exam's Daily Challenge bank (no-op if already seeded / versioned).
     * Recovers the fresh-install race where Home renders before the async startup seed has finished for
     * TIL-I / CEnT-S (which seed last), so the first-time user still gets an AVAILABLE challenge.
     */
    private suspend fun ensureExamBankSeeded(exam: ExamType) {
        when (exam) {
            ExamType.IMAT -> DbSeeder.seedImatIfNeeded(this)
            ExamType.TIL_I -> DbSeeder.seedTilIIfNeeded(this)
            ExamType.CENT_S -> DbSeeder.seedCentsIfNeeded(this)
            else -> Unit
        }
    }
}
