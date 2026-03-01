package com.brainbuddy.app.junior

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProfileStore

/**
 * Parent-only settings for Junior module. PIN required.
 * Enable/disable Junior per profile, set daily time limit, mic toggle.
 */
class JuniorSettingsActivity : AppCompatActivity() {

    private lateinit var juniorPrefs: JuniorPrefs
    private lateinit var profileStore: ProfileStore
    private var selectedProfileId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, JuniorSettingsActivity::class.java)) return

        setContentView(R.layout.activity_junior_settings)
        juniorPrefs = JuniorPrefs(this)
        profileStore = ProfileStore(this)
        selectedProfileId = profileStore.getCurrentProfileId()

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "BrainBuddy Junior"

        val profiles = profileStore.getProfiles()
        val spinner = findViewById<Spinner>(R.id.spinnerProfile)
        if (profiles.size > 1) {
            spinner.visibility = View.VISIBLE
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, profiles.map { it.name })
            spinner.setSelection(profiles.indexOfFirst { it.id == selectedProfileId }.coerceAtLeast(0))
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    selectedProfileId = profiles[pos].id
                    refreshUiForProfile()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        } else {
            spinner.visibility = View.GONE
        }

        val switchEnabled = findViewById<Switch>(R.id.switchJuniorEnabled)
        refreshUiForProfile()
        switchEnabled.setOnCheckedChangeListener { _, checked ->
            juniorPrefs.setJuniorEnabled(selectedProfileId, checked)
            Toast.makeText(this, if (checked) "Junior modülü açıldı" else "Junior modülü kapatıldı", Toast.LENGTH_SHORT).show()
        }

        val seekBar = findViewById<SeekBar>(R.id.seekJuniorMinutes)
        val tvValue = findViewById<TextView>(R.id.tvJuniorMinutesValue)
        val minutes = juniorPrefs.getJuniorDailyMinutes(selectedProfileId)
        seekBar.progress = when (minutes) {
            10 -> 0
            20 -> 1
            30 -> 2
            else -> 1
        }
        seekBar.max = 2
        tvValue.text = "${juniorPrefs.getJuniorDailyMinutes(selectedProfileId)} dakika"
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    val mins = when (value) {
                        0 -> 10
                        2 -> 30
                        else -> 20
                    }
                    tvValue.text = "$mins dakika"
                    juniorPrefs.setJuniorDailyMinutes(selectedProfileId, mins)
                }
            }
            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })

        val switchMic = findViewById<Switch>(R.id.switchJuniorMic)
        switchMic.isChecked = juniorPrefs.isMicEnabled(selectedProfileId)
        switchMic.setOnCheckedChangeListener { _, checked ->
            juniorPrefs.setMicEnabled(selectedProfileId, checked)
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardJuniorReport).setOnClickListener {
            startActivity(android.content.Intent(this, JuniorReportActivity::class.java))
        }
    }

    private fun refreshUiForProfile() {
        findViewById<Switch>(R.id.switchJuniorEnabled).isChecked = juniorPrefs.isJuniorEnabledForProfile(selectedProfileId)
        val minutes = juniorPrefs.getJuniorDailyMinutes(selectedProfileId)
        findViewById<SeekBar>(R.id.seekJuniorMinutes).progress = when (minutes) {
            10 -> 0
            20 -> 1
            30 -> 2
            else -> 1
        }
        findViewById<TextView>(R.id.tvJuniorMinutesValue).text = "$minutes dakika"
        findViewById<Switch>(R.id.switchJuniorMic).isChecked = juniorPrefs.isMicEnabled(selectedProfileId)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
