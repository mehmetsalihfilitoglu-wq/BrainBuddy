package com.brainbuddy.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R

class AdLimitReachedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad_limit_reached)

        findViewById<android.widget.Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.Button>(R.id.btnPremium).setOnClickListener {
            // TODO: Open premium purchase flow
            android.widget.Toast.makeText(this, "Premium yakında", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
