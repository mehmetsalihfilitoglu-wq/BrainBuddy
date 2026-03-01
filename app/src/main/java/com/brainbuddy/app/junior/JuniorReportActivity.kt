package com.brainbuddy.app.junior

import android.os.Bundle
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

        val sb = StringBuilder()
        for (p in profiles) {
            if (!JuniorPrefs(this).isJuniorEnabledForProfile(p.id)) continue
            val report = progressStore.getReportForProfile(p.id)
            sb.append("Profil: ${p.name}\n\n")
            sb.append("Öğrenilen harfler: ${report.learnedLetters.sorted().joinToString(", ").ifEmpty { "-" }}\n\n")
            sb.append("Öğrenilen kelimeler: ${report.wordsLearned.joinToString(", ").ifEmpty { "-" }}\n\n")
            val rate = if (report.syllableAttempts > 0) (report.syllableCorrect * 100 / report.syllableAttempts) else 0
            sb.append("Hece doğruluğu: %$rate (${report.syllableCorrect}/${report.syllableAttempts})\n\n")
            sb.append("Bugün kullanılan süre: ${report.todayMinutesUsed} dakika\n\n")
            if (rate < 70 && report.syllableAttempts > 5) {
                sb.append("⚠ Hece alıştırmalarına daha fazla zaman ayırın.\n\n")
            }
            sb.append("---\n\n")
        }
        findViewById<android.widget.TextView>(R.id.tvReport).text = sb.toString().ifEmpty { "Henüz veri yok." }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
