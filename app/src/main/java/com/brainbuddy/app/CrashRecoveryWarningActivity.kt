package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.CrashRecoveryPrefs
import com.brainbuddy.app.databinding.ActivityCrashRecoveryWarningBinding

/** Shown after 3+ crashes in 5 min: protection auto-disabled, parent warning. */
class CrashRecoveryWarningActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityCrashRecoveryWarningBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnOk.setOnClickListener {
            CrashRecoveryPrefs.setProtectionDisabledByCrash(this, false)
            CrashRecoveryPrefs.clearCrashHistory(this)
            startActivity(Intent(this, HomeActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            ))
            finish()
        }
    }
}
