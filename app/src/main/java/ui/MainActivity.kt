package ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.accessibility.AccessibilityUtils
import com.brainbuddy.app.accessibility.BrainBuddyAccessibilityService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val enabled = AccessibilityUtils.isServiceEnabled(
            this,
            BrainBuddyAccessibilityService::class.java
        )

        if (!enabled) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}