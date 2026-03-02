package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.core.QuizRetryPolicy

class TestSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, TestSettingsActivity::class.java)) return

        setContentView(R.layout.activity_test_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_test_settings)

        val retryPolicy = QuizRetryPolicy(this)
        val premium = PremiumStore(this)
        val retrySummary = buildString {
            if (premium.isPremium()) {
                append("• Premium: Sınırsız tekrar, reklam yok\n")
            } else {
                append("• Ücretsiz: Test başarısız olunca 3 reklam hakkı\n")
                append("• Haklar bitince 30 dk bekleme\n")
                append("• Bekleme sonrası 1 ücretsiz tekrar\n")
                val mode = retryPolicy.getStartMode()
                when (mode) {
                    QuizRetryPolicy.StartMode.REQUIRE_AD ->
                        append("• Şu an: Reklam izleyerek tekrar deneyebilirsiniz")
                    QuizRetryPolicy.StartMode.WAIT_COOLDOWN ->
                        append("• Şu an: Bekleme süresi dolana kadar tekrar yok")
                    else ->
                        append("• Şu an: Tekrar deneyebilirsiniz")
                }
            }
        }

        findViewById<android.widget.TextView>(R.id.tvRetryRule).text = retrySummary

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExamPacks).setOnClickListener {
            startActivity(Intent(this, ExamPackActivity::class.java))
        }

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
