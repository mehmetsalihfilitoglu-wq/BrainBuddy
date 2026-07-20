package com.edumio.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.edumio.app.BuildConfig
import com.edumio.app.MainActivity
import com.edumio.app.R
import com.edumio.app.auth.AuthProvider
import com.edumio.app.core.BackupManager
import com.edumio.app.core.NotificationPrefs

/**
 * MVP settings — four focused sections only: Hesap (signed-in email + sign out), Bildirimler
 * (the single reminder toggle), Gizlilik ve Veri (privacy policy, data export/restore, account
 * deletion) and Uygulama (version). No premium, no reports, no cloud-sync entry, no emoji.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        // ── Hesap: real signed-in email + sign out ──
        val email = AuthProvider.currentUser(this)?.email
        findViewById<android.widget.TextView>(R.id.tvAccountEmail).text =
            email?.takeIf { it.isNotBlank() } ?: "—"
        findViewById<android.view.View>(R.id.cardSignOut).setOnClickListener { confirmSignOut() }

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

    override fun onResume() {
        super.onResume()
        // Keep the email fresh (e.g. after a re-auth elsewhere).
        val email = AuthProvider.currentUser(this)?.email
        findViewById<android.widget.TextView>(R.id.tvAccountEmail).text =
            email?.takeIf { it.isNotBlank() } ?: "—"
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_signout_confirm_title)
            .setMessage(R.string.settings_signout_confirm_message)
            .setNegativeButton(R.string.data_rights_cancel, null)
            .setPositiveButton(R.string.settings_signout) { _, _ -> doSignOut() }
            .show()
    }

    private fun doSignOut() {
        AuthProvider.repository(this).signOut()
        // Re-route through the launcher: signed out + onboarding done → the sign-in screen.
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
        finish()
    }
}
