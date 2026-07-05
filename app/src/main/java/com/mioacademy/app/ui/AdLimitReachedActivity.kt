package com.mioacademy.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mioacademy.app.R

class AdLimitReachedActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad_limit_reached)

        val titleOverride = intent.getStringExtra(EXTRA_TITLE)
        val messageOverride = intent.getStringExtra(EXTRA_MESSAGE)

        findViewById<android.widget.TextView>(R.id.tvTitle).apply {
            text = titleOverride ?: getString(R.string.ad_limit_reached_title)
        }
        findViewById<android.widget.TextView>(R.id.tvMessage).apply {
            text = messageOverride ?: getString(R.string.ad_limit_reached_message)
        }

        findViewById<android.widget.Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.Button>(R.id.btnPremium).setOnClickListener {
            android.widget.Toast.makeText(this, "Premium yakında", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
