package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.BackupManager
import com.brainbuddy.app.core.NotificationPrefs

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val notifPrefs = NotificationPrefs(this)
        findViewById<android.widget.Switch>(R.id.switchMotivationNotifications)?.apply {
            isChecked = notifPrefs.areMotivationNotificationsEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                notifPrefs.setMotivationNotificationsEnabled(isChecked)
            }
        }

        findViewById<android.view.View>(R.id.cardBackup)?.setOnClickListener {
            BackupManager.exportBackup(this)
        }
        findViewById<android.view.View>(R.id.cardRestore)?.setOnClickListener {
            startActivity(Intent(this, BackupImportActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardRewardContracts)?.setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.reward.RewardContractActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardPrivacyPolicy)?.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
