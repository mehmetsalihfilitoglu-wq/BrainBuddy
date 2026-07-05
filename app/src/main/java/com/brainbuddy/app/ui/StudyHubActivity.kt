package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.quiz.QuizActivity
import com.brainbuddy.app.quiz.WrongPoolLauncher
import com.brainbuddy.app.quiz.WrongQuestionPoolStore

class StudyHubActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_hub)

        findViewById<android.widget.Button>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardTest).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardWrongPool).setOnClickListener {
            WrongPoolLauncher.launch(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updateWrongPoolCardState()
    }

    private fun updateWrongPoolCardState() {
        val card = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardWrongPool)
        val subtitle = findViewById<android.widget.TextView>(R.id.tvWrongPoolSubtitle)
        val hasItems = WrongQuestionPoolStore(this).isNotEmpty()
        card.isEnabled = hasItems
        card.alpha = if (hasItems) 1f else 0.45f
        subtitle.text = if (hasItems) {
            getString(R.string.wrong_pool_home_sub)
        } else {
            getString(R.string.wrong_pool_card_empty_sub)
        }
    }
}
