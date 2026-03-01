package com.brainbuddy.app.junior

import android.media.ToneGenerator
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import kotlin.random.Random

/**
 * "Dinle ve seç" - Play letter sound, child picks from 2-4 options.
 */
class LetterSoundMatchActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var curriculum: JuniorCurriculum.CurriculumData
    private lateinit var progressStore: JuniorProgressStore
    private var correctLetter: String = ""
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_junior_letter_match)
        audioManager = AudioManager(this)
        curriculum = JuniorCurriculum.load(this)
        progressStore = JuniorProgressStore(this)

        audioManager.init {
            nextQuestion()
        }
        try {
            toneGenerator = ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
        } catch (_: Exception) {}
        findViewById<View>(R.id.btnSpeakAgain).setOnClickListener { playQuestion() }
    }

    private fun nextQuestion() {
        val learned = curriculum.letterOrder.take(6).ifEmpty { curriculum.letterOrder }
        correctLetter = learned.random()
        val options = (learned - correctLetter).shuffled().take(3) + correctLetter
        val shuffled = options.shuffled()

        val btn1 = findViewById<android.widget.Button>(R.id.btnOption1)
        val btn2 = findViewById<android.widget.Button>(R.id.btnOption2)
        val btn3 = findViewById<android.widget.Button>(R.id.btnOption3)
        val btn4 = findViewById<android.widget.Button>(R.id.btnOption4)
        val buttons = listOf(btn1, btn2, btn3, btn4)
        buttons.forEach { it.visibility = View.GONE }
        shuffled.take(4).forEachIndexed { i, letter ->
            buttons[i].visibility = View.VISIBLE
            buttons[i].text = letter
            buttons[i].textSize = 32f
            buttons[i].setOnClickListener { onAnswer(letter, buttons) }
        }
        playQuestion()
    }

    private fun playQuestion() {
        audioManager.speak("Hangi harf? ${curriculum.letterNames[correctLetter] ?: correctLetter}")
    }

    private fun onAnswer(selected: String, buttons: List<android.widget.Button>) {
        buttons.forEach { it.isEnabled = false }
        if (selected == correctLetter) {
            try { toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150) } catch (_: Exception) {}
            progressStore.addLearnedLetter(selected)
            progressStore.addTodayLearned(selected)
            progressStore.addStars(1)
            Toast.makeText(this, "Doğru! 🌟", Toast.LENGTH_SHORT).show()
            android.os.Handler(mainLooper).postDelayed({ nextQuestion(); buttons.forEach { it.isEnabled = true } }, 800)
        } else {
            try { toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 100) } catch (_: Exception) {}
            audioManager.speak("Tekrar dene")
            android.os.Handler(mainLooper).postDelayed({ buttons.forEach { it.isEnabled = true }; playQuestion() }, 1200)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
        toneGenerator?.release()
    }
}
