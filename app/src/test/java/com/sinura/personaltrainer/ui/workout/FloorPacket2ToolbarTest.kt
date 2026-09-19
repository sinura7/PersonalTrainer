package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet 2 on the redesigned floor: the header is read-only chrome (title, progress,
 * Finish, overflow); the dock ([WorkoutDock]) owns the timer, the advance choice and
 * Log set; rest length is presets / ±15 in a sheet; one clock, two modes.
 */
class FloorPacket2ToolbarTest {
    @Test
    fun headerIsReadOnlyAndTelemetryLivesInSessionSummary() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.headerIsReadOnlyInstrumentStrip())
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.headerShowsMinuteTelemetryOnly())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.headerShowsSessionProgress())
        val header = readOwned("ui/workout/WorkoutHeader.kt")
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
        assertTrue(
            "the header says where the session stands, in words and as a bar",
            header.contains("WorkoutProgressCalculator.headline(progress)"),
        )
        assertTrue(header.contains(".testTag(WorkoutTestTags.PROGRESS_LINE)"))
        assertTrue(header.contains("if (!compact) WorkoutProgressBar(segments = progress.segments)"))
        assertTrue(header.contains("WorkoutTestTags.FINISH"))
        assertTrue(header.contains("overflow?.invoke()"))
        val hero = readOwned("ui/workout/ExerciseHeader.kt")
        assertFalse(hero.contains("SessionTelemetryCopy"))
        assertFalse("session totals stay out of the identity", hero.contains("onSummary"))
        val overflow = readOwned("ui/workout/WorkoutOverflowMenu.kt")
        assertTrue(overflow.contains("onSummary"))
        assertTrue(overflow.contains("\"Session summary\""))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("onSummary = { sessionSummaryOpen = true }"))
        assertTrue(screen.contains("WorkoutSessionSummary("))
        val summary = readOwned("ui/workout/WorkoutSessionSummary.kt")
        assertTrue(summary.contains("SessionTelemetryCopy.elapsedMinutesLabel"))
        assertTrue(summary.contains("Working sets:"))
        assertTrue(summary.contains("External volume:"))
    }

    @Test
    fun dockOwnsTimerAdvanceAndVoltLog() {
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("FloorTimerSurface.mode("))
        assertTrue(dock.contains("SetWorkDock("))
        assertTrue(dock.contains("RestTimerCard("))
        assertFalse("the dock's rest is the card, not the old bar", dock.contains("RestDock("))
        assertFalse(dock.contains("FloorTimerSlot("))
        assertTrue(dock.contains("val nextAct = action.kind == WorkoutPrimaryKind.NEXT_EXERCISE && !state.editing"))
        assertTrue(dock.contains("val finishAct = action.kind == WorkoutPrimaryKind.FINISH && !state.editing"))
        assertTrue(dock.contains("PrimaryGymButton("))
        assertTrue(dock.contains("height = Metrics.commit"))
        assertTrue(dock.contains("onSelectRestDuration"))
        assertTrue(dock.contains("holdElapsedSeconds"))
        assertTrue(dock.contains("WorkoutTestTags.LOG_SET"))
        assertTrue(dock.contains("nextAct -> WorkoutTestTags.NEXT"))
        assertTrue(dock.contains("finishAct -> WorkoutTestTags.DOCK_FINISH"))

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("show = showRest"))
        assertTrue(screen.contains("val showNext = primaryAction.kind == WorkoutPrimaryKind.NEXT_EXERCISE"))
        assertTrue(screen.contains("val showFinish = primaryAction.kind == WorkoutPrimaryKind.FINISH"))
        assertTrue(screen.contains("onOpenRest = { session?.id?.let(onOpenRest) }"))
        assertFalse(
            "RestDock must not be a sibling of the dock in the screen",
            screen.contains("RestDock("),
        )
        assertFalse(screen.contains("RestTimerCard("))
        val bottomBar = screen.indexOf("bottomBar = {")
        val workoutDock = screen.indexOf("WorkoutDock(")
        val lazy = screen.indexOf("LazyColumn(")
        assertTrue(bottomBar >= 0 && workoutDock > bottomBar && lazy > workoutDock)
        assertFalse(screen.substring(lazy).contains("WorkoutDock("))
        assertFalse(screen.substring(lazy).contains("FloorTimerSlot("))
        assertFalse(screen.substring(lazy).contains("SetWorkDock("))
    }

    @Test
    fun restLengthEditsWithPresetsAndModesDoNotStack() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.restLengthIsInlineWheel())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.oneClockTwoModes())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.restIsDockCard())
        val timers = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(timers.contains("fun SetWorkDock"))
        assertTrue(timers.contains("fun RestDurationSheet"))
        assertFalse(timers.contains("SnapValueWheel("))
        assertTrue(timers.contains("RestPresetChips("))
        assertTrue(timers.contains("FloorTimerSurface.mode("))
        val sheet = timers.substring(timers.indexOf("fun RestDurationSheet"))
        assertTrue(sheet.contains("RestPresetChips("))
        assertTrue(sheet.contains("ModalBottomSheet("))
        assertTrue(sheet.contains("workout-rest-duration-sheet"))
        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertFalse("idle must not expand presets inline", card.contains("picking"))
        assertFalse(card.contains("RestPresetChips("))
        assertFalse(card.contains("SnapValueWheel("))
        assertTrue(
            "idle tap edits the length; running tap opens the rest page",
            card.contains(".clickable(role = Role.Button, onClick = if (idle) onEditDuration else onOpenRest)"),
        )
        assertTrue(card.contains("WorkoutTestTags.START_REST"))
        assertTrue(card.contains("WorkoutTestTags.REST_MINUS"))
        assertTrue(card.contains("WorkoutTestTags.REST_PLUS"))
        assertTrue(card.contains("WorkoutTestTags.REST_SKIP"))
        assertFalse("no pulse on the dock card", card.contains("rememberInfiniteTransition"))
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("onEditDuration = { durationSheet = true }"))
        assertTrue(dock.contains("onOpenRest = events.onOpenRest"))
        assertTrue(dock.contains("RestDurationSheet("))
        val sheetHost = dock.indexOf("if (durationSheet) {")
        assertTrue("the sheet is an overlay after the pinned dock", dock.indexOf("PinnedDock(") in 0 until sheetHost)
    }

    @Test
    fun floorEntryDoesNotHostASecondSetCountdown() {
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertFalse(
            "running hold clock must not stay in the entry",
            editor.contains("WorkoutTestTags.HOLD_CLOCK"),
        )
        assertFalse(editor.contains("workout-hold-clock"))
        assertFalse(editor.contains("SetWorkDock("))
        assertFalse(editor.contains("FloorInstrumentBar("))
        assertTrue(
            "the hold numeral is read-only while the dock clock runs",
            editor.contains("enabled = enabled && !holdRunning"),
        )
        val identity = readOwned("ui/workout/ExerciseHeader.kt")
        assertFalse(identity.contains("HOLD_CLOCK"))
        assertFalse(identity.contains("SetWorkDock("))
        val timers = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(timers.contains("workout-hold-clock"))
        assertTrue(timers.contains("FloorTimerSurface.setClockSeconds"))
        assertEquals("workout-hold-clock", WorkoutTestTags.HOLD_CLOCK)
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("SetWorkDock("))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.substring(screen.indexOf("LazyColumn(")).contains("SetWorkDock("))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
