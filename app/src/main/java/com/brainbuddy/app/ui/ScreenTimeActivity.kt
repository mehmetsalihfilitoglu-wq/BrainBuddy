package com.brainbuddy.app.ui

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.core.SleepPrefs
import com.brainbuddy.app.core.TimeLimitPrefs

class ScreenTimeActivity : AppCompatActivity() {

    private lateinit var timeLimitPrefs: TimeLimitPrefs
    private lateinit var sleepPrefs: SleepPrefs
    private lateinit var premiumStore: PremiumStore

    private var quickAllowMinutes: Int = 10
    private var quickAllowTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, ScreenTimeActivity::class.java)) return

        setContentView(R.layout.activity_screen_time)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Ekran Süresi"

        timeLimitPrefs = TimeLimitPrefs(this)
        sleepPrefs = SleepPrefs(this)
        premiumStore = PremiumStore(this)

        val seekBar = findViewById<SeekBar>(R.id.seekMinutes)
        val tvValue = findViewById<TextView>(R.id.tvMinutesValue)

        val tvTodayUsageValue = findViewById<TextView>(R.id.tvTodayUsageValue)
        val tvTodayUsageRemaining = findViewById<TextView>(R.id.tvTodayUsageRemaining)
        val progressTodayUsage =
            findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(
                R.id.progressTodayUsage
            )

        val tvSleepStart = findViewById<TextView>(R.id.tvSleepStart)
        val tvSleepEnd = findViewById<TextView>(R.id.tvSleepEnd)
        val tvSleepStartStatus = findViewById<TextView>(R.id.tvSleepStartStatus)
        val tvSleepEndStatus = findViewById<TextView>(R.id.tvSleepEndStatus)

        val tvQuickAllowCountdown = findViewById<TextView>(R.id.tvQuickAllowCountdown)
        val layoutQuickAllowPremiumOptions =
            findViewById<View>(R.id.layoutQuickAllowPremiumOptions)
        val tvQuickAllowPremiumHint =
            findViewById<TextView>(R.id.tvQuickAllowPremiumHint)
        val btnGrantQuick =
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGrantQuick)

        val chipQuick5 =
            findViewById<com.google.android.material.chip.Chip?>(R.id.chipQuick5)
        val chipQuick10 =
            findViewById<com.google.android.material.chip.Chip?>(R.id.chipQuick10)
        val chipQuick15 =
            findViewById<com.google.android.material.chip.Chip?>(R.id.chipQuick15)
        val chipQuick30 =
            findViewById<com.google.android.material.chip.Chip?>(R.id.chipQuick30)

        seekBar.max = 180
        seekBar.progress = timeLimitPrefs.dailyMinutes()
        tvValue.text = "${timeLimitPrefs.dailyMinutes()} dk"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    val mins = value
                    tvValue.text = "$mins dk"
                    timeLimitPrefs.setDailyMinutes(mins)
                    Toast.makeText(
                        this@ScreenTimeActivity,
                        "Günlük limit kaydedildi",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })

        updateTodayUsageCard(
            tvTodayUsageValue,
            tvTodayUsageRemaining,
            progressTodayUsage
        )

        updateSleepLabels(tvSleepStart, tvSleepEnd, tvSleepStartStatus, tvSleepEndStatus)
        setupQuickAllow(
            tvQuickAllowCountdown,
            layoutQuickAllowPremiumOptions,
            tvQuickAllowPremiumHint,
            btnGrantQuick,
            chipQuick5,
            chipQuick10,
            chipQuick15,
            chipQuick30
        )

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSleepStart).setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    sleepPrefs.setSleepStart(h, m)
                    updateSleepLabels(tvSleepStart, tvSleepEnd, tvSleepStartStatus, tvSleepEndStatus)
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
                    updateSleepLabels(tvSleepStart, tvSleepEnd, tvSleepStartStatus, tvSleepEndStatus)
                },
                sleepPrefs.sleepEndHour(),
                sleepPrefs.sleepEndMinute(),
                true
            ).show()
        }

        btnGrantQuick.setOnClickListener {
            showQuickAllowDialog(tvQuickAllowCountdown)
        }

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun updateTodayUsageCard(
        tvValue: TextView,
        tvRemaining: TextView,
        progress: com.google.android.material.progressindicator.LinearProgressIndicator
    ) {
        val usedMinutes = estimateTodayMinutes()
        val limit = timeLimitPrefs.dailyMinutes().coerceAtLeast(0)
        tvValue.text = "$usedMinutes / $limit dk"
        val remaining = (limit - usedMinutes).coerceAtLeast(0)
        tvRemaining.text = "Kalan: $remaining dk"
        val pct = if (limit > 0) {
            (usedMinutes * 100 / limit).coerceIn(0, 100)
        } else 0
        progress.progress = pct
    }

    private fun estimateTodayMinutes(): Int {
        // Şu an için test sayısına göre basit tahmin (ParentHub ile uyumlu)
        val analytics = com.brainbuddy.app.core.AnalyticsStore(this)
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val perfsToday = analytics.getTestPerformances()
            .filter { it.tsMs in start..now }
        val todayTests = perfsToday.size
        return todayTests * 5
    }

    private fun updateSleepLabels(
        tvSleepStart: TextView,
        tvSleepEnd: TextView,
        tvSleepStartStatus: TextView,
        tvSleepEndStatus: TextView
    ) {
        val startH = sleepPrefs.sleepStartHour()
        val startM = sleepPrefs.sleepStartMinute()
        val endH = sleepPrefs.sleepEndHour()
        val endM = sleepPrefs.sleepEndMinute()

        tvSleepStart.text = "%02d:%02d".format(startH, startM)
        tvSleepEnd.text = "%02d:%02d".format(endH, endM)

        val now = java.util.Calendar.getInstance()
        val nowMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                now.get(java.util.Calendar.MINUTE)
        val startTotal = startH * 60 + startM
        val endTotal = endH * 60 + endM

        val active = if (startTotal <= endTotal) {
            nowMinutes in startTotal until endTotal
        } else {
            nowMinutes >= startTotal || nowMinutes < endTotal
        }

        val statusText = if (active) "AKTİF" else "PASİF"
        val statusColor = if (active) R.color.bb_primary else R.color.bb_text_muted

        tvSleepStartStatus.text = statusText
        tvSleepEndStatus.text = statusText
        tvSleepStartStatus.setTextColor(resources.getColor(statusColor, theme))
        tvSleepEndStatus.setTextColor(resources.getColor(statusColor, theme))
    }

    private fun setupQuickAllow(
        tvCountdown: TextView,
        premiumOptionsLayout: View,
        premiumHint: TextView,
        btnGrantQuick: com.google.android.material.button.MaterialButton,
        chip5: com.google.android.material.chip.Chip?,
        chip10: com.google.android.material.chip.Chip?,
        chip15: com.google.android.material.chip.Chip?,
        chip30: com.google.android.material.chip.Chip?
    ) {
        val isPremium = premiumStore.isPremium()

        if (isPremium) {
            premiumOptionsLayout.visibility = View.VISIBLE
            premiumHint.visibility = View.GONE
        } else {
            premiumOptionsLayout.visibility = View.GONE
            premiumHint.visibility = View.VISIBLE
            quickAllowMinutes = 10
        }

        chip10?.isChecked = true

        val chipClickListener = View.OnClickListener { v ->
            chip5?.isChecked = v.id == R.id.chipQuick5
            chip10?.isChecked = v.id == R.id.chipQuick10
            chip15?.isChecked = v.id == R.id.chipQuick15
            chip30?.isChecked = v.id == R.id.chipQuick30

            quickAllowMinutes = when (v.id) {
                R.id.chipQuick5 -> 5
                R.id.chipQuick10 -> 10
                R.id.chipQuick15 -> 15
                R.id.chipQuick30 -> 30
                else -> 10
            }
            btnGrantQuick.text = "Şimdi ${quickAllowMinutes} dk izin ver"
        }

        if (isPremium) {
            chip5?.setOnClickListener(chipClickListener)
            chip10?.setOnClickListener(chipClickListener)
            chip15?.setOnClickListener(chipClickListener)
            chip30?.setOnClickListener(chipClickListener)
        }

        if (!isPremium) {
            btnGrantQuick.text = "Şimdi 10 dk izin ver"
        } else {
            btnGrantQuick.text = "Şimdi ${quickAllowMinutes} dk izin ver"
        }

        restoreQuickAllowCountdown(tvCountdown)
    }

    private fun showQuickAllowDialog(tvCountdown: TextView) {
        AlertDialog.Builder(this)
            .setTitle("${quickAllowMinutes} dakika izin verilsin mi?")
            .setMessage("Bu süre boyunca ekran kısıtlamaları geçici olarak kaldırılacak.")
            .setPositiveButton("Evet") { _, _ ->
                startQuickAllow(tvCountdown)
            }
            .setNegativeButton("Vazgeç") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun startQuickAllow(tvCountdown: TextView) {
        val now = System.currentTimeMillis()
        val until = now + quickAllowMinutes * 60_000L
        sleepPrefs.setExtraMinutesGrantedAt(until)
        Toast.makeText(
            this,
            "$quickAllowMinutes dakika izin verildi",
            Toast.LENGTH_SHORT
        ).show()
        startCountdown(until, tvCountdown)
    }

    private fun restoreQuickAllowCountdown(tvCountdown: TextView) {
        val until = sleepPrefs.extraMinutesGrantedAtMs()
        if (until <= 0L) {
            tvCountdown.text = "Aktif izin yok"
            return
        }
        val remainingMs = until - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            tvCountdown.text = "Aktif izin yok"
            return
        }
        startCountdown(until, tvCountdown)
    }

    private fun startCountdown(until: Long, tvCountdown: TextView) {
        quickAllowTimer?.cancel()
        val remainingMs = (until - System.currentTimeMillis()).coerceAtLeast(0L)
        quickAllowTimer = object : CountDownTimer(remainingMs, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                tvCountdown.text = "Aktif izin: ${minutes} dk ${seconds.toString().padStart(2, '0')} sn kaldı"
            }

            override fun onFinish() {
                tvCountdown.text = "Aktif izin yok"
            }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        quickAllowTimer?.cancel()
    }
}
