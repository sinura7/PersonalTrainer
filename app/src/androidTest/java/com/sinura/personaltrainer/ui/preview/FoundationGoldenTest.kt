package com.sinura.personaltrainer.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.testutil.GoldenImageAssert
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
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
        compose.setContent {
            PersonalTrainerTheme(reduceMotion = true) {
                Box(Modifier.size(GalleryWidth, GalleryHeight)) {
                    FoundationStateGallery()
                }
            }
        }
        compose.onNodeWithText("Reduced motion").assertIsDisplayed()
    }

    private fun setGallery(accent: () -> androidx.compose.ui.graphics.Color = { Volt }) {
        compose.setContent {
            PersonalTrainerTheme {
                Box(Modifier.size(GalleryWidth, GalleryHeight)) {
                    FoundationStateGallery(accent = accent())
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(): ImageBitmap =
        compose.onNodeWithTag(FoundationGalleryTag).captureToImage()

    private companion object {
        const val GalleryGoldenName = "foundation-state-gallery-api29"
        val GalleryWidth = 360.dp
        val GalleryHeight = 800.dp
    }
}
