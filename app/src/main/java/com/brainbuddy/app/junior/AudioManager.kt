package com.brainbuddy.app.junior

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue

/**
 * TTS wrapper for Turkish voice guidance. Caches/queues speech to avoid overlap.
 */
class AudioManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private val queue = LinkedBlockingQueue<String>()
    private var isProcessing = false
    private var initCallback: (() -> Unit)? = null

    fun init(onReady: (() -> Unit)? = null) {
        if (tts != null) {
            onReady?.invoke()
            return
        }
        initCallback = onReady
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("tr", "TR")) ?: TextToSpeech.LANG_MISSING_DATA
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts?.setSpeechRate(0.9f)
                }
                initCallback?.invoke()
                initCallback = null
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        queue.offer(text)
        processQueue()
    }

    fun speakImmediate(text: String) {
        if (text.isBlank()) return
        stop()
        queue.clear()
        queue.offer(text)
        isProcessing = false
        processQueue()
    }

    private fun processQueue() {
        if (isProcessing || queue.isEmpty()) return
        val ttsRef = tts ?: return
        if (ttsRef.isSpeaking) return
        val next = queue.poll() ?: return
        isProcessing = true
        val utteranceId = "junior_${System.currentTimeMillis()}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ttsRef.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    isProcessing = false
                    processQueue()
                }
                override fun onError(id: String?) {
                    isProcessing = false
                    processQueue()
                }
            })
            ttsRef.speak(next, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            ttsRef.speak(next, TextToSpeech.QUEUE_FLUSH, hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utteranceId))
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                isProcessing = false
                processQueue()
            }, (next.length * 80L).coerceAtLeast(500))
        }
    }

    fun stop() {
        tts?.stop()
        isProcessing = false
    }

    fun release() {
        stop()
        queue.clear()
        tts?.shutdown()
        tts = null
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true
}
