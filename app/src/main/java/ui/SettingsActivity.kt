package com.brainbuddy.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.StudentLevel
import com.brainbuddy.app.databinding.ActivitySettingsBinding

class SettingsActivity : ComponentActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val prefs = ProtectionPrefs(this)
        val blockedStore = BlockedAppsStore(this)

        b.switchProtection.isChecked = prefs.isProtectionEnabled()

        when (prefs.studentLevel()) {
            StudentLevel.AGE_3_5 -> b.levelAges3to5.isChecked = true
            StudentLevel.GRADES_1_4 -> b.levelGrades1to4.isChecked = true
            StudentLevel.GRADES_5_8 -> b.levelGrades5to8.isChecked = true
            StudentLevel.GRADES_9_12 -> b.levelGrades9to12.isChecked = true
        }

        b.blockedPackages.setText(blockedStore.getBlockedPackages().sorted().joinToString("\n"))

        b.saveBtn.setOnClickListener {
            prefs.setProtectionEnabled(b.switchProtection.isChecked)

            val level = when (b.levelGroup.checkedRadioButtonId) {
                b.levelGrades1to4.id -> StudentLevel.GRADES_1_4
                b.levelGrades5to8.id -> StudentLevel.GRADES_5_8
                b.levelGrades9to12.id -> StudentLevel.GRADES_9_12
                else -> StudentLevel.AGE_3_5
            }
            prefs.setStudentLevel(level)

            val pkgs = b.blockedPackages.text?.toString()
                .orEmpty()
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()

            blockedStore.setBlockedPackages(pkgs)
            b.saveStatus.text = "Kaydedildi. Cooldown: 15 dakika."
        }
    }
}