package com.sinura.personaltrainer.ui.components

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * "End workout?" with a set open for changes carries a warning, a way back, the body, the notes
 * and two buttons. At the largest text on a small phone, and on a phone turned sideways, that is
 * more than the dialog's text slot holds, and the slot did not scroll: the rest of the warning,
 * the body and the notes were cut off (R2-5 reviews). It scrolls now; the buttons stay in view.
 *
 * The font scale is the system's, set before the test's window exists: a dialog is its own
 * window and takes its density from there, not from a composition local set around it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EndWorkoutDialogLargeTextRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2.0f)
    fun atTheLargestTextEverythingCanBeReachedOnASmallPhone() =
        assertAllReachable(fontScale = 2.0f, mustScroll = false)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun sidewaysEverythingCanBeReached() = assertAllReachable(fontScale = 1.0f, mustScroll = true)

    /**
     * [mustScroll]: the content is taller than the slot, so the scroll is what makes it readable.
     * Sideways it is. At font 2.0 on a small phone the theme's type ceilings keep it inside the
     * slot today; the scroll is there for when they move.
     */
    private fun assertAllReachable(fontScale: Float, mustScroll: Boolean) {
        assertTrue(
            "the system font scale is $fontScale before anything is drawn",
            RuntimeEnvironment.getApplication().resources.configuration.fontScale == fontScale,
        )
        compose.setContent {
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
        if (mustScroll) {
            val scrollable = compose.onNode(hasScrollAction()).fetchSemanticsNode()
            val range = scrollable.config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("the content overflows, so the scroll is what makes it readable", range.maxValue() > 0f)
        }
        compose.onNodeWithTag(EndWorkoutTags.EDIT_OPEN).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(EndWorkoutTags.BACK_TO_EDIT).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Session notes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(EndWorkoutTags.SAVE).assertIsDisplayed()
        compose.onNodeWithTag(EndWorkoutTags.DISCARD).assertIsDisplayed()
    }
}
