package com.brainbuddy.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            textSize = 14f
            setPadding(24, 24, 24, 24)
            text = intent.getStringExtra("crash_text") ?: "Bilinmeyen hata"
        }
        setContentView(tv)
    }
}