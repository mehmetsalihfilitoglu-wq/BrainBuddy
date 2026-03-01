package com.brainbuddy.app.junior

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R

/**
 * Student view: simple stars + "bugün öğrendiklerin"
 */
class JuniorProgressActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_junior_progress)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "İlerlemem"

        val progressStore = JuniorProgressStore(this)
        val juniorPrefs = JuniorPrefs(this)

        findViewById<android.widget.TextView>(R.id.tvStars).text = "⭐ ${progressStore.getStarsEarnedToday()} yıldız bugün"
        findViewById<android.widget.TextView>(R.id.tvLetters).text = "Öğrendiğim harfler: ${progressStore.getLearnedLetters().sorted().joinToString(", ").ifEmpty { "-" }}"
        findViewById<android.widget.TextView>(R.id.tvWords).text = "Öğrendiğim kelimeler: ${progressStore.getWordsLearned().joinToString(", ").ifEmpty { "-" }}"
        findViewById<android.widget.TextView>(R.id.tvToday).text = "Bugün: ${progressStore.getTodayLearned().joinToString(", ").ifEmpty { "-" }}"
        val rate = (progressStore.getSyllableSuccessRate() * 100).toInt()
        findViewById<android.widget.TextView>(R.id.tvSyllableRate).text = "Hece doğruluğu: %$rate"
        findViewById<android.widget.TextView>(R.id.tvMinutes).text = "Bugün kullandığın süre: ${progressStore.getTodayMinutesUsed()}/${juniorPrefs.getJuniorDailyMinutes()} dakika"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
