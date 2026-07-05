package com.mioacademy.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mioacademy.app.databinding.ActivityCrashBinding

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityCrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val crashText = getCrashTextFromIntent()
        binding.tvCrashText.text = crashText
    }

    private fun getCrashTextFromIntent(): String {
        return try {
            val i = intent ?: return "Unknown error"
            val keys = listOf(
                "crash_text",
                "stack_trace",
                "EXTRA_STACK_TRACE",
                "error_details"
            )
            for (key in keys) {
                val v = i.getStringExtra(key)
                if (!v.isNullOrBlank()) return v.take(50_000)
            }
            "No crash details available"
        } catch (_: Exception) {
            "Failed to load crash report"
        }
    }
}