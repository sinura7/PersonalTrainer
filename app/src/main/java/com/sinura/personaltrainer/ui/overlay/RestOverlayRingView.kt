package com.sinura.personaltrainer.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Remaining-rest arc (1 = full time left, 0 = done). */
class RestOverlayRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** Full volt ring when rest hits zero (brief done flash). */
    var emphasizeDone: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0x44FFFFFF
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = OVERLAY_VOLT_COLOR
    }
    private val bounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = this.width
        val h = this.height
        if (w <= 0 || h <= 0) return
        val stroke = (w.coerceAtMost(h) * 0.11f).coerceAtLeast(4f)
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        val inset = stroke / 2f + 2f
        bounds.set(inset, inset, w - inset, h - inset)
        canvas.drawArc(bounds, 0f, 360f, false, trackPaint)
        if (emphasizeDone) {
            canvas.drawArc(bounds, 0f, 360f, false, arcPaint)
        } else {
            canvas.drawArc(bounds, -90f, 360f * progress, false, arcPaint)
        }
    }
}
