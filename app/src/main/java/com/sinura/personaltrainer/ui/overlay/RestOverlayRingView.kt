package com.sinura.personaltrainer.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.sinura.personaltrainer.ui.theme.Volt

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

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0x44FFFFFF
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = android.graphics.Color.argb(
            255,
            (Volt.red * 255).toInt(),
            (Volt.green * 255).toInt(),
            (Volt.blue * 255).toInt(),
        )
    }
    private val bounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = this.width
        val h = this.height
        val stroke = (w.coerceAtMost(h) * 0.08f).coerceAtLeast(3f)
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        val inset = stroke / 2f + 2f
        bounds.set(inset, inset, w - inset, h - inset)
        canvas.drawArc(bounds, 0f, 360f, false, trackPaint)
        canvas.drawArc(bounds, -90f, 360f * progress, false, arcPaint)
    }
}
