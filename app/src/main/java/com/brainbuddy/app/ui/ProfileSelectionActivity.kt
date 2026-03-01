package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import com.brainbuddy.app.R
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.HomeActivity
import com.brainbuddy.app.core.ProfileStore

class ProfileSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_selection)

        val profileStore = ProfileStore(this)
        val profiles = profileStore.getProfiles()
        if (profiles.size == 1) {
            profileStore.setCurrentProfileId(profiles[0].id)
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        val list = findViewById<android.widget.LinearLayout>(R.id.profileList)
        for (p in profiles) {
            val btn = android.widget.Button(this).apply {
                text = p.name
                setOnClickListener {
                    profileStore.setCurrentProfileId(p.id)
                    startActivity(Intent(this@ProfileSelectionActivity, HomeActivity::class.java))
                    finish()
                }
            }
            list.addView(btn)
        }
    }
}
