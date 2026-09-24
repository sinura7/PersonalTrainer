package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The redesigned floor: overflow on the header row, weight beside reps as two hero numerals,
 * idle rest the same quiet dock card at rest, Log set the only Volt. Primary controls stay in
 * the dock.
 *
 * What the floor shows and does is held where it can be seen (audit T1c-2): one identity, the
 * selected lift's, and the ⋮ "Workout options" as a full-size button on the header's row in
 * WorkoutHeaderRowRenderTest; the commit as the dock's one filled Volt in DockVoltRenderTest;
 * the empty free workout's Add exercise, its Discard and no clock in
 * EmptySessionFloorRenderTest; Session notes in SessionNotesRenderTest; a lift card's trailing
 * on its identity row in LiftCardAndDangerButtonRenderTest; the history sheet's typing wells in
 * HistorySetEntryRenderTest; the dock's commit, pinned under the floor, in DockCommitRenderTest
 * and LandscapeChromeRenderTest; the numerals, the identity's still and "Add another set" in
 * WeightRepsEditorRenderTest, ExerciseHeaderRenderTest and WorkoutDockRenderTest. The bans stay
 * here, and two direct calls on LandscapeChrome.
 */
class FloorCompactPresentationTest {
    @Test
    fun theRetiredSelectedLiftDockAndCurrentLiftCardStayGone() {
        assertFalse("the never-shown THIS LIFT strip is gone", ownedSourceExists("ui/workout/SelectedLiftDock.kt"))
        assertFalse(ownedSourceExists("ui/workout/CurrentLiftCard.kt"))
        assertFalse(ownedSource("ui/workout/LandscapeChrome.kt").contains("hideSelectedLiftDock"))
        assertTrue(LandscapeChrome.hideIdleRest(landscape = true))
        assertFalse(LandscapeChrome.hideIdleRest(landscape = false))
        val workout = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(workout.contains("SelectedLiftDock("))
        assertFalse(workout.contains("hideSelectedLiftDock"))
        assertFalse(workout.contains("WorkoutTestTags.SELECTED_LIFT"))
    }

    @Test
    fun idleRestIsAQuietCardAndTheDockKeepsNoEffortTrack() {
        val card = ownedSource("ui/workout/RestTimerCard.kt")
        assertFalse("idle Start rest must not be a filled Volt", card.contains("PrimaryGymButton"))
        assertFalse(
            "idle Start next must not be composed",
            card.contains("RestIdleCopy.START_NEXT"),
        )
        assertFalse("idle controls are quiet marks, not full-width rows", card.contains("TextButton("))
        val dock = ownedSource("ui/workout/WorkoutDock.kt")
        assertFalse("effort left the dock for its own row", dock.contains("RpeCopy"))
        assertFalse(dock.contains("RPE_TRACK"))
        assertFalse(dock.contains("LazyRow("))
        // The commit is the dock's one filled Volt: DockVoltRenderTest sees it in every
        // companion state, and this keeps a second filled button out of any state it is not shown.
        assertEquals("one filled Volt in the dock", 1, PRIMARY_BUTTON.findAll(dock).count())
    }

    @Test
    fun theIdentityHostsNoMenuHeaderOrSetDots() {
        val hero = ownedSource("ui/workout/ExerciseHeader.kt")
        assertFalse(hero.contains("LiftOverflowMenu("))
        assertFalse(hero.contains("CurrentLiftHeader("))
        assertFalse(hero.contains("SetDots("))
    }

    @Test
    fun theFloorEntryIsNeverAWheelOrTheSharedPanel() {
        assertFalse(ownedSourceExists("ui/workout/WorkoutLiftCard.kt"))
        val editor = ownedSource("ui/workout/WeightRepsEditor.kt")
        assertFalse(editor.contains("SnapValueWheel("))
        assertFalse(editor.contains("FloorEntryWheels"))
        assertFalse(editor.contains("workout-weight-wheel"))
        assertFalse(editor.contains("FloorFieldGlyph("))
        assertFalse(editor.contains("SetEntryPanel("))
        assertFalse(editor.contains("ExerciseThumb("))
        assertFalse(editor.contains("WorkoutTestTags.HOLD_CLOCK"))
        assertFalse(ownedSource("ui/workout/ExerciseHeader.kt").contains("SessionTelemetryCopy"))
        val header = ownedSource("ui/workout/WorkoutHeader.kt")
        assertFalse(header.contains("INSTRUMENT_STRIP"))
        assertFalse(header.contains("SessionTelemetryCopy"))
        assertFalse(ownedSource("ui/workout/ActiveWorkoutScreen.kt").contains("SetEntryPanel("))
        val extra = ownedSource("ui/routines/SessionLiftStrip.kt")
        assertFalse(extra.contains("SnapValueWheel("))
        assertFalse(extra.contains("CompactFloorEntry("))
        assertFalse(extra.contains("FloorNumeralRow("))
    }

    @Test
    fun theScrollingFloorNeverHostsTheDockOrItsClocks() {
        val workout = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        val list = sourceFrom(workout, "LazyColumn(")
        assertFalse(list.contains("WorkoutDock("))
        assertFalse(list.contains("RestTimerCard("))
        assertFalse(list.contains("PinnedDock("))
        assertFalse(workout.contains("FloorTimerSlot("))
        assertFalse(
            "Home session cards are not this packet",
            workout.contains("SessionLiftStrip("),
        )
        assertFalse(ownedSource("ui/routines/SessionLiftStrip.kt").contains("compact = true"))
        assertFalse(ownedSource("ui/home/ThisWeekCard.kt").contains("SetEntryPanel("))
    }

    @Test
    fun anEmptySessionsDockMountsNoWorkoutDockOrClock() {
        val bottom = sourceFrom(ownedSource("ui/workout/ActiveWorkoutScreen.kt"), "bottomBar = {")
        val emptyDock = sourceBetween(bottom, "if (emptySession) {", "} else if (logBarVisible) {")
        assertFalse(
            "empty free workout must not mount the workout dock or its clocks",
            emptyDock.contains("WorkoutDock("),
        )
        assertFalse(emptyDock.contains("RestTimerCard("))
        assertFalse(emptyDock.contains("WorkoutDockTimer("))
        assertFalse(emptyDock.contains("prelude"))
    }

    private companion object {
        /** A call of the filled Volt button. */
        val PRIMARY_BUTTON = Regex("\\bPrimaryGymButton\\(")
    }
}
