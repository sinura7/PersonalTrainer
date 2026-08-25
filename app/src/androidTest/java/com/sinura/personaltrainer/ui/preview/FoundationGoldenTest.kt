package com.sinura.personaltrainer.ui.preview

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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FoundationGoldenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun galleryMatchesCommittedApi29Golden() {
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
