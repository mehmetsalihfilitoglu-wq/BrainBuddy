package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.brainbuddy.app.core.CareerPath
import com.brainbuddy.app.core.DailyMissionManager
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ItalianMomentProvider
import com.brainbuddy.app.core.UserGoalPrefs
import com.brainbuddy.app.quiz.WrongPoolLauncher
import com.brainbuddy.app.quiz.WrongQuestionPoolStore
import com.brainbuddy.app.reward.RewardContractStore
import com.brainbuddy.app.ui.GrowthHubActivity
import com.brainbuddy.app.ui.StudentProfileActivity
import com.brainbuddy.app.ui.StudyHubActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    private lateinit var goalPrefs: UserGoalPrefs
    private lateinit var gam: GamificationStore
    private lateinit var missionManager: DailyMissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        goalPrefs = UserGoalPrefs(this)
        gam = GamificationStore(this)
        missionManager = DailyMissionManager(this)

        setupBackPress()
        setupNavigation()

        val contractStore = RewardContractStore(this)
        val pending = contractStore.getPendingReached()
        if (pending.isNotEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.home_reward_title))
                .setMessage(pending.joinToString("\n") { "${it.targetXP} XP: ${it.description}" })
                .setPositiveButton(getString(R.string.home_reward_ok), null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun refreshAll() {
        refreshHeader()
        refreshIdentityHero()
        refreshDailyMission()
        refreshItalianMoment()
        refreshWrongPool()
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

        val personTitle = personTitleFor(career)
        val freeze = if (gam.freezeTokens() > 0) " 🧊" else ""
        val streakDays = gam.streakDays()

        findViewById<TextView>(R.id.tvCareerIdentity).text =
            "${career.emoji} Gelecekteki $personTitle"
        findViewById<TextView>(R.id.tvItalianDegree).text = career.italianDegreeName

        val streakLabel = if (streakDays == 1) "1 gün$freeze" else "$streakDays gün$freeze"
        findViewById<TextView>(R.id.streakBadge).text = "🔥 $streakLabel"
        findViewById<TextView>(R.id.pointsBadge).text = "⭐ ${gam.xp()} XP"
        findViewById<TextView>(R.id.levelBadge).text = "Lv. ${gam.level()}"
    }

    private fun refreshDailyMission() {
        val goal = goalPrefs.getGoal()
        val mission = missionManager.getTodayMission()
        val target = mission.testsTarget
        val done = mission.testsDone
        val isCompleted = done >= target

        val career = goal.careerPath
        findViewById<TextView>(R.id.tvMissionSubject).text = career.subjectSummary
        findViewById<TextView>(R.id.tvMissionMeta).text =
            "$target test · ~${target * 10} dakika"

        val bar = findViewById<ProgressBar>(R.id.missionProgressBar)
        bar.max = target
        bar.progress = done

        findViewById<TextView>(R.id.tvMissionProgress).text = "$done / $target tamamlandı"

        val btn = findViewById<MaterialButton>(R.id.btnStartMission)
        when {
            isCompleted -> {
                btn.text = getString(R.string.home_mission_btn_done)
                btn.alpha = 0.6f
                btn.isEnabled = false
            }
            done > 0 -> {
                btn.text = getString(R.string.home_mission_btn_continue)
                btn.alpha = 1f
                btn.isEnabled = true
            }
            else -> {
                btn.text = getString(R.string.home_mission_btn_start)
                btn.alpha = 1f
                btn.isEnabled = true
            }
        }
    }

    private fun refreshItalianMoment() {
        val moment = ItalianMomentProvider.today()
        findViewById<TextView>(R.id.tvItalianPhrase).text = "🇮🇹  « ${moment.italian} »"
        findViewById<TextView>(R.id.tvItalianTranslation).text = "(${moment.turkish})"
    }

    private fun refreshWrongPool() {
        val pool = WrongQuestionPoolStore(this)
        val card = findViewById<MaterialCardView>(R.id.cardWrongPool)
        card.visibility = if (pool.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupNavigation() {
        findViewById<MaterialCardView>(R.id.cardWrongPool).setOnClickListener {
            WrongPoolLauncher.launch(this)
        }
        findViewById<MaterialButton>(R.id.btnStartMission).setOnClickListener {
            startActivity(Intent(this, StudyHubActivity::class.java))
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.ui.SettingsActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardStudy).setOnClickListener {
            startActivity(Intent(this, StudyHubActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardProgress).setOnClickListener {
            startActivity(Intent(this, GrowthHubActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardProfile).setOnClickListener {
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

    private fun personTitleFor(career: CareerPath): String = when (career) {
        CareerPath.MEDICINE -> "Doktor"
        CareerPath.DENTISTRY -> "Diş Hekimi"
        CareerPath.ENGINEERING -> "Mühendis"
        CareerPath.COMPUTER_SCIENCE -> "Yazılımcı"
        CareerPath.ARCHITECTURE -> "Mimar"
        CareerPath.ECONOMICS -> "Ekonomist"
        CareerPath.LAW -> "Avukat"
        CareerPath.PHARMACY -> "Eczacı"
        CareerPath.BIOLOGY -> "Biyolog"
        CareerPath.PSYCHOLOGY -> "Psikolog"
        CareerPath.VETERINARY -> "Veteriner"
        CareerPath.MATHEMATICS -> "Matematikçi"
        CareerPath.DESIGN -> "Tasarımcı"
        CareerPath.OTHER -> "Öğrenci"
    }
}
