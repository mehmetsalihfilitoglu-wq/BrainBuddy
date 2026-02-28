package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.brainbuddy.app.quiz.QuizActivity

class SimpleHomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_home)

        val status = findViewById<TextView>(R.id.status)
        val btnOpenAcc = findViewById<Button>(R.id.btnOpenAccessibility)
        val btnParent = findViewById<Button>(R.id.btnParent)
        val btnQuiz = findViewById<Button>(R.id.btnQuiz)

        status.text = "BrainBuddy hazır ✅"

        btnOpenAcc.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnParent.setOnClickListener {
            status.text = "Ebeveyn paneli (PIN) sonraki adımda eklenecek."
        }

        btnQuiz.setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java))
        }
    }
}