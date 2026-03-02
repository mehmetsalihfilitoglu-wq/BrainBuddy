package com.brainbuddy.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard

class ScreenTimeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, ScreenTimeActivity::class.java)) return

        setContentView(R.layout.activity_screen_time)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Ekran Süresi"

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
