package com.mioacademy.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mioacademy.app.core.CareerPath
import com.mioacademy.app.quiz.QuizActivity
import com.mioacademy.app.ui.onTap
import com.mioacademy.app.core.DailyMissionManager
import com.mioacademy.app.core.exam.AdmissionExamRegistry
import com.mioacademy.app.core.GamificationStore
import com.mioacademy.app.core.UserGoalPrefs
import com.mioacademy.app.quiz.WrongPoolLauncher
import com.mioacademy.app.quiz.WrongQuestionPoolStore
import com.mioacademy.app.ui.GrowthHubActivity
import com.mioacademy.app.ui.StudentProfileActivity
import com.mioacademy.app.ui.StudyHubActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        maybeRequestNotificationPermission()

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
        // profile changed, so these must point at the new area's namespace.
        gam = GamificationStore(this)
        missionManager = DailyMissionManager(this)
        refreshHeader()
        refreshIdentityHero()
        refreshInsight()
        refreshDailyMission()
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

        val freeze = if (gam.freezeTokens() > 0) " 🧊" else ""
        val streakDays = gam.streakDays()

        findViewById<TextView>(R.id.tvCareerIdentity).text =
            "${career.emoji} ${journeyTitleFor(career)}"
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
        findViewById<TextView>(R.id.tvMissionSubject).text =
            AdmissionExamRegistry.get(career.examType).subjectDisplaySummary
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

    /** One compact, real-data insight; the card is hidden when there is no data. */
    private fun refreshInsight() {
        val card = findViewById<MaterialCardView>(R.id.cardHomeInsight)
        val tv = findViewById<TextView>(R.id.tvHomeInsight)
        val insight = com.mioacademy.app.core.ProgressInsights(this).homeInsight()
        if (insight == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        tv.text = insight.text
        val warning = insight.tone == com.mioacademy.app.core.ProgressInsights.Tone.WARNING
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
            com.mioacademy.app.ui.AreaSwitcher.show(this) { refreshAll() }
        }
        findViewById<MaterialCardView>(R.id.cardWrongPool).onTap {
            WrongPoolLauncher.launch(this)
        }
        findViewById<MaterialButton>(R.id.btnStartMission).onTap {
            startActivity(Intent(this, QuizActivity::class.java))
        }
        findViewById<View>(R.id.btnSettings).onTap {
            startActivity(Intent(this, com.mioacademy.app.ui.SettingsActivity::class.java))
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

    /** Ask for POST_NOTIFICATIONS once (Android 13+), so reminders can be shown. */
    private fun maybeRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val prefs = com.mioacademy.app.core.NotificationPrefs(this)
        if (prefs.wasPermissionRequested()) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            prefs.setPermissionRequested()
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4001)
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
