package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.OnboardingPrefs

class OnboardingActivity : AppCompatActivity() {

    private var currentPage = 0
    private val pages = listOf(
        "Odak ve Koruma" to "Uygulamaları kontrol et, zaman sınırlarını belirle. Öğrenmeye odaklan!",
        "Ödüller ve Seri" to "Her testte puan kazan, serini koru, rozetler aç. Oyun gibi öğren!",
        "Hazır mısın?" to "Testlerle bilgini pekiştir. Zor soruları tekrarla. Başarı seninle!"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val pageTitle = findViewById<TextView>(R.id.pageTitle)
        val pageDesc = findViewById<TextView>(R.id.pageDesc)
        val btnNext = findViewById<Button>(R.id.btnNext)
        val dotsContainer = findViewById<LinearLayout>(R.id.dots)

        // Create dot indicators
        repeat(pages.size) { i ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.dot_size),
                    resources.getDimensionPixelSize(R.dimen.dot_size)
                ).apply {
                    if (i < pages.size - 1) marginEnd = 12
                }
                setBackgroundResource(
                    if (i == 0) R.drawable.dot_active else R.drawable.dot_inactive
                )
            }
            dotsContainer.addView(dot)
        }

        fun updateDots() {
            for (i in 0 until dotsContainer.childCount) {
                val dot = dotsContainer.getChildAt(i)
                dot.setBackgroundResource(
                    if (i == currentPage) R.drawable.dot_active else R.drawable.dot_inactive
                )
            }
        }

        fun showPage() {
            val (title, desc) = pages[currentPage]
            pageTitle.text = title
            pageDesc.text = desc
            btnNext.text = if (currentPage == pages.size - 1) "Başla" else "İleri"
            updateDots()
        }

        showPage()

        btnNext.setOnClickListener {
            if (currentPage < pages.size - 1) {
                currentPage++
                showPage()
            } else {
                OnboardingPrefs.setDone(this, true)
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
        }
    }
}
