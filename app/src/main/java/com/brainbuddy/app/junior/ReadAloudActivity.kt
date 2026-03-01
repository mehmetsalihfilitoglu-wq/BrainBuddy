package com.brainbuddy.app.junior

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.brainbuddy.app.R
import java.util.Locale

/**
 * "Çocuk okusun" - Show word, play "Şimdi sen oku", capture speech, compare.
 * Falls back gracefully if SpeechRecognizer not available.
 */
class ReadAloudActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var curriculum: JuniorCurriculum.CurriculumData
    private lateinit var progressStore: JuniorProgressStore
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentWord: JuniorCurriculum.CurriculumWord? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        startListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_junior_read_aloud)
        audioManager = AudioManager(this)
        curriculum = JuniorCurriculum.load(this)
        progressStore = JuniorProgressStore(this)

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Ses tanıma bu cihazda kullanılamıyor.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        audioManager.init {
            nextWord()
        }
    }

    private fun nextWord() {
        val words = curriculum.words
        if (words.isEmpty()) {
            finish()
            return
        }
        currentWord = words.random()
        findViewById<android.widget.TextView>(R.id.tvWordToRead).text = currentWord!!.word
        findViewById<android.widget.TextView>(R.id.tvWordToRead).textSize = 48f
        findViewById<android.widget.Button>(R.id.btnListen).setOnClickListener {
            audioManager.speak("Şimdi sen oku. ${currentWord!!.word}")
        }
        findViewById<android.widget.Button>(R.id.btnRead).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
            } else {
                startListening()
            }
        }
        audioManager.speak("Bu kelimeyi oku: ${currentWord!!.word}. Hazır olunca mikrofona bas.")
    }

    private fun startListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                runOnUiThread {
                    if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(this@ReadAloudActivity, "Tekrar dene.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                val said = matches.firstOrNull()?.uppercase(Locale("tr"))?.replace(" ", "") ?: ""
                val expected = currentWord?.word?.uppercase(Locale("tr")) ?: ""
                runOnUiThread {
                    if (normalizeMatch(said, expected)) {
                        Toast.makeText(this@ReadAloudActivity, "Harika! 🌟", Toast.LENGTH_SHORT).show()
                        progressStore.addWordLearned(currentWord!!.word)
                        progressStore.addStars(2)
                        audioManager.speak("Süper okudun!")
                        android.os.Handler(mainLooper).postDelayed({ nextWord() }, 1500)
                    } else {
                        Toast.makeText(this@ReadAloudActivity, "Tekrar dene.", Toast.LENGTH_SHORT).show()
                        audioManager.speak("Tekrar oku. ${currentWord!!.word}")
                    }
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun normalizeMatch(said: String, expected: String): Boolean {
        if (said == expected) return true
        val s = said.replace(Regex("[^A-Za-zÇĞİÖŞÜçğıöşü]"), "")
        val e = expected.replace(Regex("[^A-Za-zÇĞİÖŞÜçğıöşü]"), "")
        if (s.equals(e, ignoreCase = true)) return true
        return s.contains(e, ignoreCase = true) || e.contains(s, ignoreCase = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        audioManager.release()
    }
}
