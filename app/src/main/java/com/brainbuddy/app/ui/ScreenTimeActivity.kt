package com.brainbuddy.app.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.SleepPrefs
import com.brainbuddy.app.core.TimeLimitPrefs

class ScreenTimeActivity : AppCompatActivity() {

    private lateinit var timeLimitPrefs: TimeLimitPrefs
    private lateinit var sleepPrefs: SleepPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, ScreenTimeActivity::class.java)) return

        setContentView(R.layout.activity_screen_time)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Ekran Süresi"

        timeLimitPrefs = TimeLimitPrefs(this)
        sleepPrefs = SleepPrefs(this)

        val seekBar = findViewById<SeekBar>(R.id.seekMinutes)
        val tvValue = findViewById<android.widget.TextView>(R.id.tvMinutesValue)

        seekBar.max = 180
        seekBar.progress = timeLimitPrefs.dailyMinutes()
        tvValue.text = "${timeLimitPrefs.dailyMinutes()} dakika"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    val mins = value
                    tvValue.text = "$mins dakika"
                    timeLimitPrefs.setDailyMinutes(mins)
                }
            }
            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })

        updateSleepLabels()

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSleepStart).setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    sleepPrefs.setSleepStart(h, m)
                    updateSleepLabels()
                },
                sleepPrefs.sleepStartHour(),
                sleepPrefs.sleepStartMinute(),
                true
            ).show()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSleepEnd).setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    sleepPrefs.setSleepEnd(h, m)
                    updateSleepLabels()
                },
                sleepPrefs.sleepEndHour(),
                sleepPrefs.sleepEndMinute(),
                true
            ).show()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGrant10Min).setOnClickListener {
            sleepPrefs.setExtraMinutesGrantedAt(System.currentTimeMillis())
            Toast.makeText(this, "10 dakika izin verildi", Toast.LENGTH_SHORT).show()
        }

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun updateSleepLabels() {
        findViewById<android.widget.TextView>(R.id.tvSleepStart).text =
            "%02d:%02d".format(sleepPrefs.sleepStartHour(), sleepPrefs.sleepStartMinute())
        findViewById<android.widget.TextView>(R.id.tvSleepEnd).text =
            "%02d:%02d".format(sleepPrefs.sleepEndHour(), sleepPrefs.sleepEndMinute())
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
