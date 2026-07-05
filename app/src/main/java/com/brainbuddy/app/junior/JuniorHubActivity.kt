package com.brainbuddy.app.junior

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.HomeActivity

/**
 * Student hub for BrainBuddy Junior. Shows lessons/games + progress.
 * No parent settings, no league. Entry from Home when Junior enabled for profile.
 */
class JuniorHubActivity : AppCompatActivity() {

    private lateinit var juniorPrefs: JuniorPrefs
    private lateinit var progressStore: JuniorProgressStore
    private var sessionStartMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        juniorPrefs = JuniorPrefs(this)
        if (!juniorPrefs.isJuniorEnabled()) {
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
            return
        }
        setContentView(R.layout.activity_junior_hub)
        progressStore = JuniorProgressStore(this)
        sessionStartMs = System.currentTimeMillis()

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Barjin Junior"
        supportActionBar?.setBackgroundDrawable(resources.getDrawable(R.drawable.bg_header_turquoise, null))

        val tvStars = findViewById<android.widget.TextView>(R.id.tvJuniorStars)
        val tvMinutes = findViewById<android.widget.TextView>(R.id.tvJuniorMinutes)
        val tvTodayLearned = findViewById<android.widget.TextView>(R.id.tvTodayLearned)
        val limit = juniorPrefs.getJuniorDailyMinutes()
        val used = progressStore.getTodayMinutesUsed()
        tvStars.text = "⭐ ${progressStore.getStarsEarnedToday()} yıldız"
        tvMinutes.text = "Bugün: ${used}/${limit} dk"
        val learned = progressStore.getTodayLearned()
        tvTodayLearned.text = if (learned.isEmpty()) "Bugün öğrendiklerin: -" else "Bugün: ${learned.take(5).joinToString(", ")}${if (learned.size > 5) "..." else ""}"

        val cardLetterMatch = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardLetterSoundMatch)
        val cardSyllable = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSyllableBuilder)
        val cardPictureWord = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardPictureToWord)
        val cardTracing = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardLetterTracing)
        val cardReadAloud = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardReadAloud)

        cardLetterMatch.setOnClickListener { startGame(LetterSoundMatchActivity::class.java) }
        cardSyllable.setOnClickListener { startGame(SyllableBuilderActivity::class.java) }
        cardPictureWord.setOnClickListener { startGame(PictureToWordActivity::class.java) }
        cardTracing.setOnClickListener { startGame(LetterTracingActivity::class.java) }
        if (juniorPrefs.isMicEnabled()) {
            cardReadAloud.visibility = View.VISIBLE
            cardReadAloud.setOnClickListener { startGame(ReadAloudActivity::class.java) }
        } else {
            cardReadAloud.visibility = View.GONE
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardJuniorProgress).setOnClickListener {
            startActivity(Intent(this, JuniorProgressActivity::class.java))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@JuniorHubActivity)
                    .setTitle("Çıkış")
                    .setMessage("Barjin Junior'dan çıkmak istiyor musun?")
                    .setPositiveButton("Evet") { _, _ -> isEnabled = false; finish() }
                    .setNegativeButton("Hayır", null)
                    .show()
            }
        })
    }

    private fun startGame(activityClass: Class<*>) {
        val limit = juniorPrefs.getJuniorDailyMinutes()
        val used = progressStore.getTodayMinutesUsed()
        if (used >= limit) {
            android.widget.Toast.makeText(this, "Bugünkü süre doldu. Yarın tekrar dene! 🌟", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, activityClass))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onPause() {
        super.onPause()
        val elapsed = ((System.currentTimeMillis() - sessionStartMs) / 60_000).toInt().coerceAtLeast(0)
        if (elapsed > 0) progressStore.addSessionMinutes(elapsed)
    }
}
