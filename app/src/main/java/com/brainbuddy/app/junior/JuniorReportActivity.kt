package com.brainbuddy.app.junior

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProfileStore

/**
 * Parent view: detailed progress report + weak areas. PIN required.
 */
class JuniorReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, JuniorReportActivity::class.java)) return

        setContentView(R.layout.activity_junior_report)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Junior İlerleme Raporu"

        val profileStore = ProfileStore(this)
        val progressStore = JuniorProgressStore(this)
        val profiles = profileStore.getProfiles()

        val container = findViewById<LinearLayout>(R.id.containerProfiles)

        var cardsAdded = 0
        for (p in profiles) {
            if (!JuniorPrefs(this).isJuniorEnabledForProfile(p.id)) continue
            val report = progressStore.getReportForProfile(p.id)
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_junior_profile_card, container, false) as ViewGroup

            card.findViewById<TextView>(R.id.tvProfileName).text = p.name

            val lettersCount = report.learnedLetters.size
            card.findViewById<TextView>(R.id.tvLettersSummary).text =
                "Öğrenilen harfler: $lettersCount"

            val wordsCount = report.wordsLearned.size
            card.findViewById<TextView>(R.id.tvWordsSummary).text =
                "Öğrenilen kelimeler: $wordsCount"

            val rate = if (report.syllableAttempts > 0) {
                (report.syllableCorrect * 100 / report.syllableAttempts)
            } else {
                0
            }
            card.findViewById<ProgressBar>(R.id.progressAccuracy).progress = rate.coerceIn(0, 100)
            card.findViewById<TextView>(R.id.tvAccuracyDetail).text =
                "%$rate (${report.syllableCorrect}/${report.syllableAttempts})"

            card.findViewById<TextView>(R.id.tvTodayMinutes).text =
                "Bugün kullanılan süre: ${report.todayMinutesUsed} dakika"

            val warningView = card.findViewById<TextView>(R.id.tvWarning)
            if (rate < 70 && report.syllableAttempts > 5) {
                warningView.visibility = View.VISIBLE
            } else {
                warningView.visibility = View.GONE
            }

            container.addView(card)
            cardsAdded++

            if (cardsAdded >= 6) {
                break
            }
        }

        if (cardsAdded == 0) {
            val emptyCard = LayoutInflater.from(this)
                .inflate(R.layout.item_junior_profile_card, container, false) as ViewGroup
            emptyCard.findViewById<TextView>(R.id.tvProfileName).text = "Henüz veri yok"
            emptyCard.findViewById<TextView>(R.id.tvLettersSummary).visibility = View.GONE
            emptyCard.findViewById<TextView>(R.id.tvWordsSummary).visibility = View.GONE
            emptyCard.findViewById<ProgressBar>(R.id.progressAccuracy).visibility = View.GONE
            emptyCard.findViewById<TextView>(R.id.tvAccuracyDetail).visibility = View.GONE
            emptyCard.findViewById<TextView>(R.id.tvTodayMinutes).text =
                "Junior modu etkin olan öğrenci bulunamadı."
            emptyCard.findViewById<TextView>(R.id.tvWarning).visibility = View.GONE
            container.addView(emptyCard)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
