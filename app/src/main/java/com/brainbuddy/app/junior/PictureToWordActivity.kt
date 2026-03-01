package com.brainbuddy.app.junior

import android.media.ToneGenerator
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R

/**
 * Show word (with optional emoji hint), play audio, child selects from options.
 * Word is read aloud. Tap speaker to replay.
 */
class PictureToWordActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var curriculum: JuniorCurriculum.CurriculumData
    private lateinit var progressStore: JuniorProgressStore
    private var correctWord: JuniorCurriculum.CurriculumWord? = null
    private var toneGenerator: ToneGenerator? = null

    private val wordEmojis = mapOf(
        "EL" to "✋", "ELMA" to "🍎", "ANNE" to "👩", "AL" to "🔴", "EK" to "🌱",
        "KALE" to "🏰", "İNEK" to "🐄", "NİNE" to "👵", "OKUL" to "🏫", "KOL" to "💪",
        "UN" to "🌾", "TUT" to "✋", "ÜZÜM" to "🍇", "YE" to "🍽", "AY" to "🌙"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_junior_picture_word)
        audioManager = AudioManager(this)
        curriculum = JuniorCurriculum.load(this)
        progressStore = JuniorProgressStore(this)

        audioManager.init { nextQuestion() }
        try {
            toneGenerator = ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
        } catch (_: Exception) {}
    }

    private fun nextQuestion() {
        val words = curriculum.words
        if (words.isEmpty()) return
        correctWord = words.random()
        val options = listOf(correctWord!!.word) + words.filter { it.word != correctWord!!.word }.shuffled().map { it.word }.take(3)
        val shuffled = options.distinct().shuffled().take(4)

        val tvPicture = findViewById<android.widget.TextView>(R.id.tvPicture)
        val emoji = wordEmojis[correctWord!!.word] ?: "📖"
        tvPicture.text = emoji
        tvPicture.textSize = 72f

        val btnSpeak = findViewById<View>(R.id.btnSpeakWord)
        btnSpeak.setOnClickListener { playWord() }

        val btn1 = findViewById<android.widget.Button>(R.id.btnWord1)
        val btn2 = findViewById<android.widget.Button>(R.id.btnWord2)
        val btn3 = findViewById<android.widget.Button>(R.id.btnWord3)
        val btn4 = findViewById<android.widget.Button>(R.id.btnWord4)
        val buttons = listOf(btn1, btn2, btn3, btn4)
        buttons.forEach { it.visibility = View.GONE }
        shuffled.forEachIndexed { i, word ->
            buttons[i].visibility = View.VISIBLE
            buttons[i].text = word
            buttons[i].textSize = 24f
            buttons[i].isEnabled = true
            buttons[i].setOnClickListener { onAnswer(word, buttons) }
        }

        playWord()
    }

    private fun playWord() {
        correctWord?.let { audioManager.speak("Bu kelime ne? ${it.meaning}") }
    }

    private fun onAnswer(selected: String, buttons: List<android.widget.Button>) {
        buttons.forEach { it.isEnabled = false }
        val correct = correctWord ?: return
        if (selected == correct.word) {
            try { toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150) } catch (_: Exception) {}
            progressStore.addWordLearned(correct.word)
            progressStore.addTodayLearned(correct.word)
            progressStore.addStars(1)
            Toast.makeText(this, "Harika! 🌟", Toast.LENGTH_SHORT).show()
            audioManager.speak("Doğru! ${correct.meaning}")
            android.os.Handler(mainLooper).postDelayed({ nextQuestion() }, 1500)
        } else {
            try { toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 100) } catch (_: Exception) {}
            audioManager.speak("Tekrar dene")
            android.os.Handler(mainLooper).postDelayed({
                buttons.forEach { it.isEnabled = true }
                playWord()
            }, 1200)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
        toneGenerator?.release()
    }
}
