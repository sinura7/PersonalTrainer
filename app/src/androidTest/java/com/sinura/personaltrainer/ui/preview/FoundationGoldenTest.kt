package com.sinura.personaltrainer.ui.preview

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.GoldenImageAssert
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.Warn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class FoundationGoldenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun galleryMatchesCommittedApi29Golden() {
        assumeTrue(
            "Committed gallery PNG is the API 29 temper-tests-api29 profile; this device is API ${Build.VERSION.SDK_INT}",
            Build.VERSION.SDK_INT == 29,
        )
        setGallery()
        GoldenImageAssert.assertMatches(GalleryGoldenName, capture())
    }

    @Test
    fun unchangedCapturesArePixelStable() {
        setGallery()
        val first = capture()
        compose.waitForIdle()
        val second = capture()
        assertEquals(0, GoldenImageAssert.compare(first, second).differentPixels)
    }

    @Test
    fun deliberateTokenChangeProducesSmallLocatedDiff() {
        var accent by mutableStateOf(Volt)
        setGallery(accent = { accent })
        val before = capture()
        compose.runOnIdle { accent = Warn }
        val after = capture()
        val diff = GoldenImageAssert.compare(before, after)

        assertTrue("token change must be visible", diff.differentPixels > 1_000)
        assertTrue("diff should be local, not repaint the page", diff.ratio in 0.001..0.02)
        assertTrue("diff bounds must be intelligible", diff.right > diff.left && diff.bottom > diff.top)
    }

    @Test
    fun reducedMotionProfileIsObservableByTheFixture() {
        GoldenCapture.mount(compose, reduceMotion = true) {
            FoundationStateGallery()
        }
        compose.onNodeWithText("Reduced motion").assertIsDisplayed()
    }

    /**
     * The rounding allowance: one level on one channel is the rasteriser, not a
     * change. Seventeen such pixels on the Volt button's corners are what made
     * this golden pass and fail on the same commit.
     */
    @Test
    fun oneLevelOfRasteriserRoundingIsNotAChange() {
        val before = solid(4, 4, 0xFF204060.toInt())
        val after = solid(4, 4, 0xFF204060.toInt()).apply { setPixel(1, 1, 0xFF204061.toInt()) }
        val diff = GoldenImageAssert.compare(before, after)
        assertEquals(0, diff.differentPixels)
        assertEquals(1, diff.roundingPixels)
        assertTrue("one level is rounding", diff.matches)
    }

    /** An allowance, not a tolerance: the second level is a change. */
    @Test
    fun twoLevelsIsAChange() {
        val before = solid(4, 4, 0xFF204060.toInt())
        val after = solid(4, 4, 0xFF204060.toInt()).apply { setPixel(1, 1, 0xFF204062.toInt()) }
        val diff = GoldenImageAssert.compare(before, after)
        assertEquals(1, diff.differentPixels)
        assertEquals(0, diff.roundingPixels)
        assertFalse("two levels is a change", diff.matches)
    }

    /** A whole surface nudged one level is an edit, so the allowance is capped. */
    @Test
    fun awholeSurfaceNudgedOneLevelExceedsTheAllowance() {
        val edge = 32
        val before = solid(edge, edge, 0xFF204060.toInt())
        val after = solid(edge, edge, 0xFF204061.toInt())
        val diff = GoldenImageAssert.compare(before, after)
        assertEquals(0, diff.differentPixels)
        assertEquals(edge * edge, diff.roundingPixels)
        assertTrue("over budget", diff.roundingPixels > GoldenImageAssert.ROUNDING_BUDGET)
        assertFalse("a surface-wide nudge is a change", diff.matches)
    }

    private fun solid(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun setGallery(accent: () -> androidx.compose.ui.graphics.Color = { Volt }) {
        GoldenCapture.mount(compose) {
            FoundationStateGallery(accent = accent())
        }
    }

    private fun capture(): ImageBitmap =
        GoldenCapture.capture(compose, FoundationGalleryTag)

    private companion object {
        const val GalleryGoldenName = "foundation-state-gallery-api29"
    }
}
