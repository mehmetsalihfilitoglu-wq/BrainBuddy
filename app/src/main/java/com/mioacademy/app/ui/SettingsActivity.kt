package com.mioacademy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mioacademy.app.R
import com.mioacademy.app.core.BackupManager
import com.mioacademy.app.core.NotificationPrefs

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
                com.mioacademy.app.notification.NotificationScheduler.reschedule(this@SettingsActivity)
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
            startActivity(Intent(this, com.mioacademy.app.legal.LegalHubActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardDataRights)?.setOnClickListener {
            startActivity(Intent(this, DataRightsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardAccountSync)?.setOnClickListener {
            startActivity(Intent(this, AccountSyncActivity::class.java))
        }
    }

}
