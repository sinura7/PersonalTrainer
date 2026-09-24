package com.sinura.personaltrainer.ui.workout

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * What the floor draws where no semantics can look: a ring's size and stroke, how much of it
 * is filled and in what colour, a chevron, a bar's fill. The window is drawn into a bitmap
 * (Robolectric never delivers the draw callback captureToImage waits on) and read in window
 * pixels, which is where a node's `boundsInWindow` lies. Needs native graphics
 * (`@GraphicsMode(NATIVE)`) and an activity rule.
 */

/** The window as it is drawn now. */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.drawWindow(): Bitmap = runOnIdle {
    val decor = activity.window.decorView
    val frame = Bitmap.createBitmap(decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    decor.draw(Canvas(frame))
    frame
}

/**
 * A window-pixel box from its four edges. Built from [Rect.Zero] rather than `Rect(…)`, which
 * the static argument checker would read as the unrelated `LiftPose.Rect`.
 */
internal fun box(left: Float, top: Float, right: Float, bottom: Float): Rect =
    Rect.Zero.copy(left = left, top = top, right = right, bottom = bottom)

/** Where this node lies in the window, in pixels. */
internal fun SemanticsNodeInteraction.windowBounds(): Rect = fetchSemanticsNode().boundsInWindow

/** A colour drawn with alpha over an opaque one, as the screen shows it. */
internal fun drawnOver(top: Color, under: Color): Color = top.compositeOver(under)

/** Whether [argb] is [color] to within [tolerance] on every channel (0–255). */
internal fun isNear(argb: Int, color: Color, tolerance: Int = COLOUR_TOLERANCE): Boolean {
    val want = color.toArgb()
    return channels().all { shift -> abs(((argb shr shift) and 0xff) - ((want shr shift) and 0xff)) <= tolerance }
}

private fun channels() = listOf(16, 8, 0)

/** How many pixels inside [box] are [color], give or take [tolerance]. */
internal fun Bitmap.count(box: Rect, color: Color, tolerance: Int = COLOUR_TOLERANCE): Int {
    var hits = 0
    for (y in box.top.toInt().coerceAtLeast(0) until box.bottom.toInt().coerceAtMost(this.height)) {
        for (x in box.left.toInt().coerceAtLeast(0) until box.right.toInt().coerceAtMost(this.width)) {
            if (isNear(getPixel(x, y), color, tolerance)) hits += 1
        }
    }
    return hits
}

/**
 * The pixels on a circle of [radius] around ([cx], [cy]), one per [stepDegrees], clockwise
 * from twelve o'clock: what a ring's stroke shows along its middle.
 */
internal fun Bitmap.around(cx: Float, cy: Float, radius: Float, stepDegrees: Int = 3): List<Int> =
    (0 until 360 step stepDegrees).map { degrees ->
        val angle = (degrees - 90) * PI / 180.0
        val x = (cx + radius * cos(angle)).toInt().coerceIn(0, this.width - 1)
        val y = (cy + radius * sin(angle)).toInt().coerceIn(0, this.height - 1)
        getPixel(x, y)
    }

/** The bounding box, in window pixels, of everything inside [box] that is not [background]. */
internal fun Bitmap.inkBox(box: Rect, background: Color, tolerance: Int = INK_TOLERANCE): Rect? {
    var left = Int.MAX_VALUE
    var top = Int.MAX_VALUE
    var right = -1
    var bottom = -1
    for (y in box.top.toInt().coerceAtLeast(0) until box.bottom.toInt().coerceAtMost(this.height)) {
        for (x in box.left.toInt().coerceAtLeast(0) until box.right.toInt().coerceAtMost(this.width)) {
            if (!isNear(getPixel(x, y), background, tolerance)) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
    }
    return if (right < 0) null else box(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
}

/** Colour distance a flat fill may drift through anti-aliasing and blending. */
private const val COLOUR_TOLERANCE = 24

/** How far from the background a pixel must be to count as drawn: past half an edge's coverage. */
private const val INK_TOLERANCE = 14
