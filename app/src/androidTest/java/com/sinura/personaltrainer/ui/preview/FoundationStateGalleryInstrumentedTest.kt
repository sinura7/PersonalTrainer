package com.sinura.personaltrainer.ui.preview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.testutil.GoldenCapture
import org.junit.Rule
import org.junit.Test

/**
 * `PersonalTrainerTheme(reduceMotion = true)` reaches the composition: the gallery prints
 * what `LocalReducedMotion` holds. Kept from `FoundationGoldenTest` when its pixel checks
 * left in X3 (ADR-032).
 */
class FoundationStateGalleryInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun reducedMotionProfileIsObservableByTheFixture() {
        GoldenCapture.mountViewport(compose, width = 360.dp, height = 800.dp, reduceMotion = true) {
            FoundationStateGallery()
        }
        compose.onNodeWithText("Reduced motion").assertIsDisplayed()
    }
}
