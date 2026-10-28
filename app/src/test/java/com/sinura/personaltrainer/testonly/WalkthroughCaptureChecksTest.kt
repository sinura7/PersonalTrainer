package com.sinura.personaltrainer.testonly

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkthroughCaptureChecksTest {
    @Test
    fun nearUniformDetectsSolidBlackFrame() {
        val range = WalkthroughCaptureChecks.luminanceRange(1080, 1920) { _, _ -> 0xFF000000.toInt() }
        assertTrue(range < WalkthroughCaptureChecks.MIN_LUMINANCE_RANGE)
    }

    @Test
    fun visibleContentDetectsContrastStrip() {
        val range = WalkthroughCaptureChecks.luminanceRange(200, 200) { x, _ ->
            if (x < 100) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        assertTrue(range >= WalkthroughCaptureChecks.MIN_LUMINANCE_RANGE)
        assertFalse(range < WalkthroughCaptureChecks.MIN_LUMINANCE_RANGE)
    }
}
