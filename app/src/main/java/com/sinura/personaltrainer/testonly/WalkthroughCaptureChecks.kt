package com.sinura.personaltrainer.testonly

import android.graphics.Bitmap

/** Shared walkthrough screenshot sanity checks (instrumented harness + JVM tests). */
object WalkthroughCaptureChecks {
    const val MIN_LUMINANCE_RANGE = 20

    fun luminanceRange(bitmap: Bitmap): Int =
        luminanceRange(bitmap.width, bitmap.height) { x, y -> bitmap.getPixel(x, y) }

    fun luminanceRange(width: Int, height: Int, pixelAt: (Int, Int) -> Int): Int {
        val stepX = (width / 16).coerceAtLeast(1)
        val stepY = (height / 16).coerceAtLeast(1)
        var min = 255
        var max = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val luma = lumaFromArgb(pixelAt(x, y))
                min = minOf(min, luma)
                max = maxOf(max, luma)
                x += stepX
            }
            y += stepY
        }
        return max - min
    }

    fun lumaFromArgb(argb: Int): Int {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
    }

    fun isNearUniform(bitmap: Bitmap): Boolean = luminanceRange(bitmap) < MIN_LUMINANCE_RANGE

    fun hasVisibleContent(bitmap: Bitmap): Boolean = luminanceRange(bitmap) >= MIN_LUMINANCE_RANGE
}
