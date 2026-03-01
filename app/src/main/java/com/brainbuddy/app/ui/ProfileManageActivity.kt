package com.brainbuddy.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProfileStore

class ProfileManageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        setContentView(R.layout.activity_profile_manage)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val profileStore = ProfileStore(this)
        val profiles = profileStore.getProfiles()
        findViewById<android.widget.TextView>(R.id.tvProfilesList).text =
            profiles.joinToString("\n") { "${it.name} (${it.id})" }

        findViewById<android.widget.Button>(R.id.btnAddProfile).setOnClickListener {
            val id = "profile_${System.currentTimeMillis()}"
            profileStore.addProfile(ProfileStore.Profile(id, "Yeni Profil", true))
            recreate()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
