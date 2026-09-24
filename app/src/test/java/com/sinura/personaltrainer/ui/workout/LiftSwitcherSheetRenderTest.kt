package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.LiftChipCopy
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextSecondary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The session switcher as it is drawn and read: a sheet titled "Lifts", one row per lift of the
 * session with its picture, its working sets and its rest, and "Add exercise" at its foot. The
 * current lift's running rest reads "Rest remaining" in the rest's cyan; a lift that is not
 * resting names its planned rest in plain grey. Each row is spoken once, in the switcher's own
 * sentence, and its picture adds nothing to it.
 *
 * These were lines of LiftSwitcherSheet.kt read as text (`CurrentLiftCopy.SWITCHER_TITLE`,
 * `"Add exercise"`, `ExerciseThumb(`, `ThumbSize.header`, `LiftChipCopy.marks(`,
 * `CurrentLiftCopy.switcherSpoken`, `"Rest remaining"`, `WorkoutTestTags.liftRest`, `RestCyan`)
 * and one of ExerciseThumb.kt (`clearAndSetSemantics { }`). Opening the sheet from the floor and
 * a row switching the lift are tapped through the screen in FloorScreenWiringRenderTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class LiftSwitcherSheetRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val picked = mutableListOf<String>()
    private var adds = 0

    private val legExtension = floorLift(targetSets = 3)
    private val legCurl = floorLift(targetSets = 3, id = "leg-curl", name = "Leg Curl")

    /** The leg extension is current, one working set in, resting with 1:15 left of its 2:00. */
    private val rows = listOf(
        LiftSwitcherRow(lift = legExtension, number = 1, workingLogged = 1, restSeconds = 120, restRunning = true, restRemainingSeconds = 75, current = true),
        LiftSwitcherRow(lift = legCurl, number = 2, workingLogged = 0, restSeconds = 90, restRunning = false, restRemainingSeconds = 75, current = false),
    )

    @Test
    fun theSheetIsTitledLiftsAndEndsWithAddExercise() {
        showSheet()
        compose.onNodeWithText(CurrentLiftCopy.SWITCHER_TITLE).assertIsDisplayed()
        val add = compose.onNodeWithTag(WorkoutTestTags.SWITCHER_ADD_LIFT).performScrollTo().assertIsDisplayed()
        assertEquals(listOf("Add exercise"), add.mergedTexts())
        val lastRow = compose.onNodeWithTag(WorkoutTestTags.liftSwitcherRow(legCurl.exercise.id)).getBoundsInRoot()
        assertTrue("Add exercise sits at the foot, under the lifts", add.getBoundsInRoot().top >= lastRow.bottom)
        add.performClick()
        assertEquals(1, adds)
        assertTrue("adding a lift is not picking one", picked.isEmpty())
    }

    @Test
    fun aRunningRestOnTheCurrentLiftSaysRestRemainingInCyan() {
        showSheet()
        val resting = LiftChipCopy.marks(workingLogged = 1, targetSets = 3, restSeconds = 120, restRunningOnThisLift = true, remainingSeconds = 75)
        val running = compose.onNodeWithTag(WorkoutTestTags.liftRest(legExtension.exercise.id), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(listOf("Rest remaining: ${resting.restClock}"), running.mergedTexts())
        // The sheet is its own window, so its ink is read from how its words are laid out.
        assertEquals("a running rest is written in cyan", RestCyan, running.textLayout().layoutInput.style.color)
        val planned = compose.onNodeWithTag(WorkoutTestTags.liftRest(legCurl.exercise.id), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(listOf("Rest: 1:30"), planned.mergedTexts())
        assertEquals("a rest that is not running is plain grey", TextSecondary, planned.textLayout().layoutInput.style.color)
        // The set progress the marks give, beside the lift's place in the session.
        compose.onNode(hasText("${resting.setProgress} working sets · Current"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun eachRowIsSpokenOnceAsTheSwitcherSaysItAndItsStillIsSilent() {
        showSheet()
        val current = compose.onNodeWithTag(WorkoutTestTags.liftSwitcherRow(legExtension.exercise.id))
        val restingClock = LiftChipCopy.marks(workingLogged = 1, targetSets = 3, restSeconds = 120, restRunningOnThisLift = true, remainingSeconds = 75).restClock
        val currentSaid = CurrentLiftCopy.switcherSpoken(
            name = "Leg Extension",
            number = 1,
            total = 2,
            workingLogged = 1,
            targetSets = 3,
            restClock = restingClock,
            restLive = true,
            current = true,
        )
        assertEquals("one sentence, and the picture adds none", listOf("$currentSaid. Current"), current.spokenDescriptions())
        val other = compose.onNodeWithTag(WorkoutTestTags.liftSwitcherRow(legCurl.exercise.id))
        val otherSaid = CurrentLiftCopy.switcherSpoken(
            name = "Leg Curl",
            number = 2,
            total = 2,
            workingLogged = 0,
            targetSets = 3,
            restClock = "1:30",
            restLive = false,
            current = false,
        )
        assertEquals(listOf("$otherSaid. Remaining"), other.spokenDescriptions())
        current.performClick()
        assertEquals(listOf(legExtension.exercise.id), picked)
    }

    @Test
    fun eachRowPicturesItsLiftInAHeaderSizedStill() {
        showSheet()
        rows.forEach { row ->
            val tag = WorkoutTestTags.liftSwitcherRow(row.lift.exercise.id)
            val stills = compose.stillsUnder(hasTestTag(tag), ThumbSize.header)
            assertEquals("one ${ThumbSize.header} still in ${row.lift.exercise.name}'s row", 1, stills.size)
        }
    }

    private fun showSheet() {
        val onSelect: (String) -> Unit = { picked += it }
        val onAddLift: () -> Unit = { adds += 1 }
        compose.showFloor {
            LiftSwitcherSheet(lifts = rows, onSelect = onSelect, onDismiss = {}, onAddLift = onAddLift)
        }
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { compose.isDisplayed(hasTestTag(WorkoutTestTags.LIFT_SWITCHER)) }
        compose.waitForIdle()
    }

}
