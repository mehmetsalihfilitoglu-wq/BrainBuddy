package com.edumio.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.edumio.app.BuildConfig
import com.edumio.app.R
import com.edumio.app.core.BackupManager
import com.edumio.app.core.NotificationPrefs

/**
 * MVP settings — account-free. Three focused sections only: Bildirimler (the single reminder toggle),
 * Gizlilik ve Veri (privacy policy, on-device data export/restore, local data deletion) and Uygulama
 * (version). v1 has no user account, so there is no e-mail, sign-out, delete-account or cloud/sync entry.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        // ── Bildirimler: single reminder toggle ──
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

        // ── Gizlilik ve Veri ──
        findViewById<android.view.View>(R.id.cardPrivacyPolicy)?.setOnClickListener {
            startActivity(Intent(this, com.edumio.app.legal.LegalHubActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardBackup)?.setOnClickListener {
            BackupManager.exportBackup(this)
        }
        findViewById<android.view.View>(R.id.cardRestore)?.setOnClickListener {
            startActivity(Intent(this, BackupImportActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardDataRights)?.setOnClickListener {
            startActivity(Intent(this, DataRightsActivity::class.java))
        }

        // ── Uygulama: version ──
        findViewById<android.widget.TextView>(R.id.tvVersionValue).text = BuildConfig.VERSION_NAME
    }
}
