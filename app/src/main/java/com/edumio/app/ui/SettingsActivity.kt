package com.edumio.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.edumio.app.R
import com.edumio.app.core.BackupManager
import com.edumio.app.core.NotificationPrefs

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        val notifPrefs = NotificationPrefs(this)
        findViewById<android.widget.Switch>(R.id.switchMotivationNotifications)?.apply {
            isChecked = notifPrefs.areMotivationNotificationsEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                notifPrefs.setMotivationNotificationsEnabled(isChecked)
                com.edumio.app.notification.NotificationScheduler.reschedule(this@SettingsActivity)
                // Daily Challenge reminders share the same toggle.
                com.edumio.app.dailychallenge.DailyChallengeReminderScheduler.reschedule(this@SettingsActivity)
                // Recovery path: if reminders are on but the OS permission is denied, open settings so
                // the user can actually receive them.
                if (isChecked &&
                    !com.edumio.app.dailychallenge.NotificationPermission.isGranted(this@SettingsActivity)
                ) {
                    android.widget.Toast.makeText(
                        this@SettingsActivity, R.string.dc_perm_open_settings, android.widget.Toast.LENGTH_LONG,
                    ).show()
                    com.edumio.app.dailychallenge.NotificationPermission
                        .openAppNotificationSettings(this@SettingsActivity)
                }
            }
        }

        findViewById<android.view.View>(R.id.cardReports)?.setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardBackup)?.setOnClickListener {
            BackupManager.exportBackup(this)
        }
        findViewById<android.view.View>(R.id.cardRestore)?.setOnClickListener {
            startActivity(Intent(this, BackupImportActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardPrivacyPolicy)?.setOnClickListener {
            startActivity(Intent(this, com.edumio.app.legal.LegalHubActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardDataRights)?.setOnClickListener {
            startActivity(Intent(this, DataRightsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardAccountSync)?.setOnClickListener {
            startActivity(Intent(this, AccountSyncActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardPremium)?.setOnClickListener {
            com.edumio.app.quiz.PremiumPaywallSheet()
                .show(supportFragmentManager, com.edumio.app.quiz.PremiumPaywallSheet.TAG)
        }
    }

}
