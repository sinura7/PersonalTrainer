package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sinura.personaltrainer.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Blueprint 09_rest: one render per non-empty state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestStateRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingShowsRingAndShimmeringUpNext() {
        compose.showFloor {
            WorkoutRestPanel(
                remainingSeconds = 90,
                totalSeconds = 120,
                running = true,
                completed = false,
                upNextKicker = "Up next · set 2 of 3",
                upNextTarget = "70 kg × 10",
                upNextLoading = true,
                loggedLine = null,
                showFirstRestCaption = false,
                onNudge = {},
                onSkip = {},
                onUndoLastSet = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.REST_FLOOR_RING).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.UP_NEXT_CARD).assertIsDisplayed()
    }

    @Test
    fun firstTimeShowsRestCaption() {
        compose.showFloor {
            WorkoutRestPanel(
                remainingSeconds = 88,
                totalSeconds = 120,
                running = true,
                completed = false,
                upNextKicker = "Up next · set 2 of 3",
                upNextTarget = "70 kg × 10",
                upNextLoading = false,
                loggedLine = "Set 1 · 70 kg × 10 · effort 8",
                showFirstRestCaption = true,
                onNudge = {},
                onSkip = {},
                onUndoLastSet = {},
            )
        }
        compose.onNodeWithText(
            compose.activity.getString(R.string.rest_first_caption),
        ).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_UNDO_LAST).assertIsDisplayed()
    }

    @Test
    fun resumeShowsRunningRingAndControls() {
        compose.showFloor {
            WorkoutRestPanel(
                remainingSeconds = 46,
                totalSeconds = 90,
                running = true,
                completed = false,
                upNextKicker = "Up next · set 2 of 3",
                upNextTarget = "60 kg × 8",
                upNextLoading = false,
                loggedLine = "Set 1 · 60 kg × 8 · effort 8",
                showFirstRestCaption = false,
                onNudge = {},
                onSkip = {},
                onUndoLastSet = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.REST_MINUS).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_PLUS).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).assertIsDisplayed()
    }

    @Test
    fun completeUsesGoKicker() {
        compose.showFloor {
            WorkoutRestPanel(
                remainingSeconds = 0,
                totalSeconds = 90,
                running = false,
                completed = true,
                upNextKicker = "Up next · set 2 of 3",
                upNextTarget = "60 kg × 8",
                upNextLoading = false,
                loggedLine = null,
                showFirstRestCaption = false,
                onNudge = {},
                onSkip = {},
                onUndoLastSet = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.REST_FLOOR_RING).assertIsDisplayed()
    }
}
