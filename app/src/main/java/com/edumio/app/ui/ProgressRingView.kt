package com.edumio.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.edumio.app.R

/**
 * Circular progress ring for overall accuracy (0-100%).
 */
class ProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 24f
        color = context.getColor(R.color.bb_text_muted).let { android.graphics.Color.argb(60, android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it)) }
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 24f
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.bb_primary)
    }
    private val rect = RectF()
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 100f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = progressPaint.strokeWidth / 2
        rect.set(pad, pad, width - pad, height - pad)
        canvas.drawArc(rect, 0f, 360f, false, bgPaint)
        canvas.drawArc(rect, -90f, 3.6f * progress, false, progressPaint)
    }
}
