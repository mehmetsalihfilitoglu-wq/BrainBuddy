package com.brainbuddy.app.junior

import android.media.ToneGenerator
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R

/**
 * A + L = AL. Audio supports each piece. Tap to hear letter/syllable.
 */
class SyllableBuilderActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var curriculum: JuniorCurriculum.CurriculumData
    private lateinit var progressStore: JuniorProgressStore
    private var currentSyllable: JuniorCurriculum.Syllable? = null
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_junior_syllable)
        audioManager = AudioManager(this)
        curriculum = JuniorCurriculum.load(this)
        progressStore = JuniorProgressStore(this)

        audioManager.init { nextQuestion() }
        try {
            toneGenerator = ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
        } catch (_: Exception) {}
    }

    private fun nextQuestion() {
        val syl = curriculum.syllables.randomOrNull() ?: return
        currentSyllable = syl

        val card1 = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardLetter1)
        val card2 = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardLetter2)
        val cardResult = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardResult)
        val tv1 = findViewById<android.widget.TextView>(R.id.tvLetter1)
        val tv2 = findViewById<android.widget.TextView>(R.id.tvLetter2)
        val tvResult = findViewById<android.widget.TextView>(R.id.tvResult)

        tv1.text = syl.first
        tv2.text = syl.second
        tvResult.text = "?"

        card1.setOnClickListener { audioManager.speak(curriculum.letterNames[syl.first] ?: syl.first) }
        card2.setOnClickListener { audioManager.speak(curriculum.letterNames[syl.second] ?: syl.second) }
        cardResult.setOnClickListener { audioManager.speak(syl.result) }

        val btn1 = findViewById<android.widget.Button>(R.id.btnOpt1)
        val btn2 = findViewById<android.widget.Button>(R.id.btnOpt2)
        val btn3 = findViewById<android.widget.Button>(R.id.btnOpt3)
        val options = listOf(syl.result) + curriculum.syllables.map { it.result }.filter { it != syl.result }.shuffled().take(2)
        val shuffled = options.shuffled()
        btn1.text = shuffled.getOrNull(0) ?: ""
        btn2.text = shuffled.getOrNull(1) ?: ""
        btn3.text = shuffled.getOrNull(2) ?: ""

        listOf(btn1, btn2, btn3).forEach { it.isEnabled = true }
        listOf(btn1, btn2, btn3).forEach { btn ->
            btn.setOnClickListener { onAnswer(btn.text.toString()) }
        }

        audioManager.speak("${curriculum.letterNames[syl.first] ?: syl.first} ve ${curriculum.letterNames[syl.second] ?: syl.second} birleşince ne olur?")
    }

    private fun onAnswer(selected: String) {
        val syl = currentSyllable ?: return
        findViewById<android.widget.Button>(R.id.btnOpt1).isEnabled = false
        findViewById<android.widget.Button>(R.id.btnOpt2).isEnabled = false
        findViewById<android.widget.Button>(R.id.btnOpt3).isEnabled = false

        if (selected == syl.result) {
            try { toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150) } catch (_: Exception) {}
            progressStore.recordSyllableAttempt(true)
            progressStore.addTodayLearned(syl.result)
            progressStore.addStars(1)
            findViewById<android.widget.TextView>(R.id.tvResult).text = syl.result
            Toast.makeText(this, "Süper! 🌟", Toast.LENGTH_SHORT).show()
            audioManager.speak("Doğru! $selected")
            android.os.Handler(mainLooper).postDelayed({
                nextQuestion()
            }, 1500)
        } else {
            try { toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 100) } catch (_: Exception) {}
            progressStore.recordSyllableAttempt(false)
            audioManager.speak("Tekrar dene")
            android.os.Handler(mainLooper).postDelayed({
                findViewById<android.widget.Button>(R.id.btnOpt1).isEnabled = true
                findViewById<android.widget.Button>(R.id.btnOpt2).isEnabled = true
                findViewById<android.widget.Button>(R.id.btnOpt3).isEnabled = true
            }, 1200)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
        toneGenerator?.release()
    }
}
