package com.brainbuddy.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashText = try {
            (intent?.getStringExtra("crash_text") ?: "Bilinmeyen hata").take(50_000)
        } catch (_: Exception) { "Hata raporu yüklenemedi" }
        val tv = TextView(this)
        tv.textSize = 14f
        tv.setPadding(24, 24, 24, 24)
        tv.text = crashText
        setContentView(tv)
    }
}