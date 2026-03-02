package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.NotificationPrefs
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.BackupManager
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.StudentLevel

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: ProtectionPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        setContentView(R.layout.activity_settings)

        prefs = ProtectionPrefs(this)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val switchProtection = findViewById<android.widget.Switch>(R.id.switchProtection)
        val levelGroup = findViewById<android.widget.RadioGroup>(R.id.levelGroup)
        val intervalGroup = findViewById<android.widget.RadioGroup>(R.id.intervalGroup)

        switchProtection.isChecked = prefs.isProtectionEnabled()
        when (prefs.quizIntervalMinutes()) {
            45 -> intervalGroup.check(R.id.interval45)
            60 -> intervalGroup.check(R.id.interval60)
            else -> intervalGroup.check(R.id.interval30)
        }
        when (prefs.studentLevel()) {
            StudentLevel.AGE_3_5 -> levelGroup.check(R.id.levelAges3to5)
            StudentLevel.GRADES_1_4 -> levelGroup.check(R.id.levelGrades1to4)
            StudentLevel.GRADES_5_8 -> levelGroup.check(R.id.levelGrades5to8)
            StudentLevel.GRADES_9_12 -> levelGroup.check(R.id.levelGrades9to12)
        }

        switchProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.setProtectionEnabled(isChecked)
        }

        levelGroup.setOnCheckedChangeListener { _, id ->
            val level = when (id) {
                R.id.levelGrades1to4 -> StudentLevel.GRADES_1_4
                R.id.levelGrades5to8 -> StudentLevel.GRADES_5_8
                R.id.levelGrades9to12 -> StudentLevel.GRADES_9_12
                else -> StudentLevel.AGE_3_5
            }
            prefs.setStudentLevel(level)
        }

        intervalGroup.setOnCheckedChangeListener { _, id ->
            val mins = when (id) {
                R.id.interval45 -> 45
                R.id.interval60 -> 60
                else -> 30
            }
            prefs.setQuizIntervalMinutes(mins)
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardPermissions).setOnClickListener {
            startActivity(Intent(this, PermissionsChecklistActivity::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBlockedApps).setOnClickListener {
            startActivity(Intent(this, BlockedAppsActivity::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardTimeLimits).setOnClickListener {
            startActivity(Intent(this, TimeLimitsActivity::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardQuizSettings).setOnClickListener {
            startActivity(Intent(this, QuizSettingsActivity::class.java))
        }
        findViewById<View>(R.id.cardExamPacks)?.setOnClickListener {
            startActivity(Intent(this, ExamPackActivity::class.java))
        }
        findViewById<View>(R.id.cardReports)?.setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        findViewById<View>(R.id.cardSchedules)?.setOnClickListener {
            startActivity(Intent(this, SchedulesActivity::class.java))
        }
        findViewById<View>(R.id.cardProfiles)?.setOnClickListener {
            startActivity(Intent(this, ProfileManageActivity::class.java))
        }
        findViewById<View>(R.id.cardJuniorModule)?.setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.junior.JuniorSettingsActivity::class.java))
        }
        findViewById<View>(R.id.cardPrivacyPolicy)?.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
        findViewById<View>(R.id.cardAccessibilityUsage)?.setOnClickListener {
            startActivity(Intent(this, AccessibilityUsageActivity::class.java))
        }
        findViewById<View>(R.id.cardBackup)?.setOnClickListener {
            BackupManager.exportBackup(this)
        }
        findViewById<View>(R.id.cardRestore)?.setOnClickListener {
            startActivity(Intent(this, BackupImportActivity::class.java))
        }
        findViewById<View>(R.id.cardRewardContracts)?.setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.reward.RewardContractActivity::class.java))
        }
        findViewById<View>(R.id.cardSystemHealth)?.setOnClickListener {
            startActivity(Intent(this, SystemHealthActivity::class.java))
        }

        val notifPrefs = NotificationPrefs(this)
        findViewById<android.widget.Switch>(R.id.switchMotivationNotifications).apply {
            isChecked = notifPrefs.areMotivationNotificationsEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                notifPrefs.setMotivationNotificationsEnabled(isChecked)
            }
        }
        val switchShopDisabled = findViewById<android.widget.Switch>(R.id.switchShopDisabled)
        val avatarStore = com.brainbuddy.app.avatar.AvatarStore(this)
        switchShopDisabled.isChecked = avatarStore.isShopDisabledByParent()
        switchShopDisabled.setOnCheckedChangeListener { _, isChecked ->
            avatarStore.setShopDisabledByParent(isChecked)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
