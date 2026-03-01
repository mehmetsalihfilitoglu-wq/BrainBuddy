package com.brainbuddy.app.junior

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R

/**
 * Letter tracing - finger trace. No strict scoring, optional.
 */
class LetterTracingActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var curriculum: JuniorCurriculum.CurriculumData
    private var currentLetter: String = ""
    private var traceView: LetterTraceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_junior_tracing)
        audioManager = AudioManager(this)
        curriculum = JuniorCurriculum.load(this)

        audioManager.init {
            nextLetter()
        }
        traceView = findViewById(R.id.traceView)
        findViewById<View>(R.id.btnNextLetter).setOnClickListener { nextLetter() }
        findViewById<View>(R.id.btnSpeakLetter).setOnClickListener {
            audioManager.speak(curriculum.letterNames[currentLetter] ?: currentLetter)
        }
    }

    private fun nextLetter() {
        currentLetter = curriculum.letterOrder.random()
        traceView?.setLetter(currentLetter)
        findViewById<android.widget.TextView>(R.id.tvLetterHint).text = currentLetter
        audioManager.speak("$currentLetter harfini çiz. ${curriculum.letterNames[currentLetter] ?: currentLetter}")
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
    }
}

class LetterTraceView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(com.brainbuddy.app.R.color.bb_turquoise_dark)
        strokeWidth = 24f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = android.graphics.Path()
    private var letter: String = "E"

    fun setLetter(l: String) {
        letter = l
        path.reset()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (width * 0.4f).coerceAtMost(height * 0.4f)
            color = Color.LTGRAY
            textAlign = Paint.Align.CENTER
        }
        val bounds = android.graphics.Rect()
        textPaint.getTextBounds(letter, 0, letter.length, bounds)
        canvas.drawText(letter, centerX, centerY + bounds.height() / 2f, textPaint)
        canvas.drawPath(path, tracePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> path.moveTo(event.x, event.y)
            MotionEvent.ACTION_MOVE -> path.lineTo(event.x, event.y)
        }
        invalidate()
        return true
    }
}
