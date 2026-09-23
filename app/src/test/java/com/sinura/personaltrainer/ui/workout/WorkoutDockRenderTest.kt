package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dock's companion slot, composed on its own: which companion takes the room, that
 * the compact clock stays reachable beside every one of them, and the "Add another set"
 * choice that stands beside Next exercise / Finish once the plan is met.
 *
 * These were held as substrings of WorkoutDock.kt — the `prelude` / `contextVisible` /
 * `completeDock -> Row(` slices and the "Add another set" literal. W1a keeps one "Add set"
 * on the floor, which removes exactly that Row, and the slices would throw rather than say
 * what changed. The advance choice is also where the floor's oldest regression lived: a
 * loop that jumped to the next lift by itself. The last test here holds the dock's half of
 * that: nothing on the dock's own clock presses Next. The ViewModel's half, a timer after a
 * save that moves the lift, is FloorScreenWiringRenderTest's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class WorkoutDockRenderTest {
    @get:Rule val compose = createComposeRule()

    private val primaries = mutableListOf<WorkoutPrimaryAction>()
    private var another = 0
    private var cancelled = 0

    private fun showDock(state: WorkoutDockState) {
        val events = floorDockEvents(
            onPrimary = { primaries += it; true },
            onAnotherSet = { another += 1 },
            onCancelEdit = { cancelled += 1 },
        )
        compose.showFloor { WorkoutDock(state = state, events = events) }
    }

    private val next = floorPrimaryAction(kind = WorkoutPrimaryKind.NEXT_EXERCISE, nextName = "Leg Curl")
    private val finish = floorPrimaryAction(kind = WorkoutPrimaryKind.FINISH)
    private val logSet = floorPrimaryAction(kind = WorkoutPrimaryKind.LOG_SET, draft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10))

    @Test
    fun onceThePlanIsMetAddAnotherSetStandsBesideNextAndTheClock() {
        showDock(floorDockState(action = next, payload = "Leg Curl", spokenPayload = "Leg Curl"))
        // The floor's one "Add set" (W1a): the set history no longer offers its own chip.
        val add = compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET)
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHeightIsAtLeast(Metrics.touchMin)
        compose.onNode(hasText("Add another set") and hasAnyAncestor(hasTestTag(WorkoutTestTags.ANOTHER_SET)), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin)
        compose.onNodeWithTag(WorkoutTestTags.NEXT).assertIsDisplayed()
        compose.onAllNodesWithTag(WorkoutTestTags.LOG_SET).assertCountEquals(0)
        // Both choices live in the companion row, above the commit.
        val row = compose.onNodeWithTag(WorkoutTestTags.TIMER_ROW).getBoundsInRoot()
        val addBounds = add.getBoundsInRoot()
        assertTrue(addBounds.top >= row.top && addBounds.bottom <= row.bottom)
        add.performClick()
        assertEquals(1, another)
        assertTrue("Add another set is not the commit", primaries.isEmpty())
    }

    @Test
    fun finishingTheWorkoutAlsoOffersAnotherSet() {
        showDock(floorDockState(action = finish, payload = null))
        compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).assertIsDisplayed()
        // Finish keeps a way back to one more set.
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertIsDisplayed().performClick()
        assertEquals(1, another)
    }

    @Test
    fun addAnotherSetIsDisabledWhileTheEntryIsLocked() {
        showDock(floorDockState(action = next, payload = "Leg Curl", showAnother = false))
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertIsNotEnabled().performClick()
        assertEquals(0, another)
    }

    @Test
    fun whileLoggingTheCompanionIsTheRestCardAndThereIsNoAddAnotherSet() {
        showDock(floorDockState(action = logSet))
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed()
        // The rest card is the clock itself here, so no compact clock repeats it.
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.TIMER_ROW).assertHeightIsAtLeast(Metrics.logTimerRow)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
    }

    @Test
    fun anErrorTakesTheSlotAndKeepsTheClockBesideIt() {
        showDock(floorDockState(action = logSet, error = "Could not save"))
        compose.onNodeWithTag(WorkoutTestTags.ERROR_DETAILS).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin)
        compose.onNodeWithText("Action needs attention ›").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertIsDisplayed()
        compose.onNodeWithText("Timers").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
    }

    @Test
    fun anUndoOfferTakesTheSlotAndKeepsTheClockBesideIt() {
        showDock(floorDockState(action = logSet, undoMessage = "Set deleted"))
        compose.onNodeWithText("Set deleted", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
    }

    @Test
    fun anEditCanAlwaysStandDownAndOutranksTheAdvanceChoice() {
        // Planned sets are done, but an edit is open: the commit saves the edit, Cancel edit
        // takes the slot, and the advance choice waits.
        showDock(floorDockState(action = next, editing = true))
        compose.onNodeWithTag(WorkoutTestTags.CANCEL_EDIT).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin)
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.NEXT).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.CANCEL_EDIT).performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun theDockNeverAdvancesOnItsOwn() {
        var undoTimedOut = 0
        val events = floorDockEvents(onPrimary = { primaries += it; true }, onUndoDismissed = { undoTimedOut += 1 })
        // The planned sets are done and an undo offer is up: the dock's one timer. A loop
        // that jumps once a dwell ends puts the lifter on the wrong card with a bar in their
        // hands, so the offer is left to run out before anything is checked.
        val state = floorDockState(action = next, payload = "Leg Curl", spokenPayload = "Leg Curl", undoMessage = "Set deleted")
        compose.showFloor { WorkoutDock(state = state, events = events) }
        compose.mainClock.advanceTimeBy(state.undoDwellMs + ONE_MINUTE_MS)
        compose.waitForIdle()
        assertEquals("the undo offer's own dwell ran out", 1, undoTimedOut)
        assertTrue("nothing advanced without a tap, was $primaries", primaries.isEmpty())
        compose.onNodeWithTag(WorkoutTestTags.NEXT).assertIsDisplayed().performClick()
        assertEquals(listOf(next), primaries)
    }

    private companion object {
        const val ONE_MINUTE_MS = 60_000L
    }
}
