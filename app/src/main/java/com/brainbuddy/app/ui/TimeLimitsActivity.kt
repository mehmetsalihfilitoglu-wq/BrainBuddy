package com.brainbuddy.app.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.TimeLimitPrefs

class TimeLimitsActivity : AppCompatActivity() {

    private lateinit var prefs: TimeLimitPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_limits)

        prefs = TimeLimitPrefs(this)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Zaman Limitleri"

        val seekBar = findViewById<SeekBar>(R.id.seekMinutes)
        val tvValue = findViewById<TextView>(R.id.tvMinutesValue)

        seekBar.max = 180
        seekBar.progress = prefs.dailyMinutes()
        tvValue.text = "${prefs.dailyMinutes()} dakika"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    val mins = value
                    tvValue.text = "$mins dakika"
                    prefs.setDailyMinutes(mins)
                }
            }
            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
