package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Packet 2 on the redesigned floor: the header is read-only chrome (title, progress,
 * Finish, overflow); the dock ([WorkoutDock]) owns the timer, the advance choice and
 * Log set; rest length is presets / ±15 in a sheet; one clock, two modes.
 *
 * What those do is held where it can be seen: the header's plan line and bar in
 * WorkoutFloorComponentsTest and LandscapeChromeRenderTest; Session summary in
 * LiftOptionsRenderTest; the dock's clocks, sheet and commit in WorkoutDockTimerRenderTest,
 * RestDurationSheetRenderTest, WorkoutDockRenderTest and DockCommitRenderTest; the dock
 * pinned under the scrolling floor in LandscapeChromeRenderTest (audit T1c-1). The bans stay
 * here: what the header, the entry and the scrolling list must never host again.
 */
class FloorPacket2ToolbarTest {
    @Test
    fun headerIsReadOnlyAndTelemetryLivesInSessionSummary() {
        val header = ownedSource("ui/workout/WorkoutHeader.kt")
        assertFalse("Start rest must not live in the header", header.contains("onStart"))
        assertFalse("Skip must not live in the header", header.contains("onSkip"))
        assertFalse(header.contains("PrimaryGymButton"))
        assertFalse(header.contains("SnapValueWheel"))
        assertFalse(header.contains("WorkoutTestTags.INSTRUMENT_STRIP"))
        assertFalse(header.contains("SessionTelemetryCopy.line"))
        assertFalse(
            "header must not format a seconds clock for session elapsed",
            header.contains("RestTimer.formatClock"),
        )
        assertFalse(
            "header must not host rest/hold/stopwatch instrumentState",
            header.contains("FloorTimerSurface.instrumentState"),
        )
        assertFalse(header.contains("delay(1_000L)"))
        val hero = ownedSource("ui/workout/ExerciseHeader.kt")
        assertFalse(hero.contains("SessionTelemetryCopy"))
        assertFalse("session totals stay out of the identity", hero.contains("onSummary"))
    }

    @Test
    fun theDockOwnsTheClockAndTheListNeverHostsIt() {
        val dock = ownedSource("ui/workout/WorkoutDock.kt")
        assertFalse("the dock's rest is the card, not the old bar", dock.contains("RestDock("))
        assertFalse(dock.contains("FloorTimerSlot("))

        val screen = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(
            "RestDock must not be a sibling of the dock in the screen",
            screen.contains("RestDock("),
        )
        assertFalse(screen.contains("RestTimerCard("))
        val list = sourceFrom(screen, "LazyColumn(")
        assertFalse(list.contains("WorkoutDock("))
        assertFalse(list.contains("FloorTimerSlot("))
        assertFalse(list.contains("SetWorkDock("))
    }

    @Test
    fun restLengthEditsWithPresetsAndModesDoNotStack() {
        val timers = ownedSource("ui/components/RestTimerUi.kt")
        assertFalse(timers.contains("SnapValueWheel("))
        // The mode switch moved with the dock: RestTimerUi keeps only the bar, sheet and rings.
        assertFalse(timers.contains("FloorTimerSurface.mode("))
        val card = ownedSource("ui/workout/RestTimerCard.kt")
        assertFalse("idle must not expand presets inline", card.contains("picking"))
        assertFalse(card.contains("RestPresetChips("))
        assertFalse(card.contains("SnapValueWheel("))
        assertFalse("no pulse on the dock card", card.contains("rememberInfiniteTransition"))
    }

    @Test
    fun floorEntryDoesNotHostASecondSetCountdown() {
        val editor = ownedSource("ui/workout/WeightRepsEditor.kt")
        assertFalse(
            "running hold clock must not stay in the entry",
            editor.contains("WorkoutTestTags.HOLD_CLOCK"),
        )
        // The literal ban below is only a ban while it is the hold clock's real tag.
        assertEquals("workout-hold-clock", WorkoutTestTags.HOLD_CLOCK)
        assertFalse(editor.contains("workout-hold-clock"))
        assertFalse(editor.contains("SetWorkDock("))
        assertFalse(editor.contains("FloorInstrumentBar("))
        val identity = ownedSource("ui/workout/ExerciseHeader.kt")
        assertFalse(identity.contains("HOLD_CLOCK"))
        assertFalse(identity.contains("SetWorkDock("))
        val screen = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(sourceFrom(screen, "LazyColumn(").contains("SetWorkDock("))
    }
}
