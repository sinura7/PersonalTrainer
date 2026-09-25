package com.sinura.personaltrainer.ui.components

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "End workout?" with a set open for changes carries a warning, the body, the notes and three
 * buttons. At the largest text on a small phone, and on a phone turned sideways, that is more
 * than the dialog's text slot holds, and the slot did not scroll: the rest of the warning, the
 * body and the notes were cut off (R2-5 reviews). It scrolls now; the buttons stay in view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EndWorkoutDialogLargeTextRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun atTheLargestTextEverythingCanBeReachedOnASmallPhone() = assertAllReachable(fontScale = 2.0f)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun sidewaysEverythingCanBeReached() = assertAllReachable(fontScale = 1.0f)

    private fun assertAllReachable(fontScale: Float) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = fontScale)) {
                PersonalTrainerTheme {
                    EndWorkoutDialog(
                        loggedSets = 12,
                        onSave = {},
                        onDiscardInstead = {},
                        onDismiss = {},
                        editOpen = true,
                    )
                }
            }
        }
        compose.onNodeWithTag(EndWorkoutTags.EDIT_OPEN).assertIsDisplayed()
        compose.onNodeWithText("Session notes").performScrollTo().assertIsDisplayed()
        listOf(EndWorkoutTags.BACK_TO_EDIT, EndWorkoutTags.SAVE, EndWorkoutTags.DISCARD).forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
    }
}
