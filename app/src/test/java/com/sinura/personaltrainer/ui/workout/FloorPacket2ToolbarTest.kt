package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet 2: read-only instrument strip; LogBar owns timer + advance + Log set;
 * rest length is an inline SnapValueWheel; one clock, two modes.
 */
class FloorPacket2ToolbarTest {
    @Test
    fun headerStripIsReadOnlyAndOpensTheTimer() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.headerIsReadOnlyInstrumentStrip())
        val header = readOwned("ui/workout/WorkoutHeader.kt")
        assertTrue(header.contains("onOpenTimer"))
        assertTrue(header.contains("WorkoutTestTags.INSTRUMENT_STRIP"))
        assertTrue(header.contains("FloorTimerSurface.instrumentState"))
        assertFalse("Start rest must not live in the header", header.contains("onStart"))
        assertFalse("Skip must not live in the header", header.contains("onSkip"))
        assertFalse(header.contains("PrimaryGymButton"))
        assertFalse(header.contains("SnapValueWheel"))
    }

    @Test
    fun logBarOwnsTimerAdvanceAndVoltLog() {
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("FloorTimerSlot("))
        assertTrue(bar.contains("advanceChoice"))
        assertTrue(bar.contains("PrimaryGymButton("))
        assertTrue(bar.contains("height = Metrics.commit"))
        assertTrue(bar.contains("onSelectRestDuration"))
        assertTrue(bar.contains("holdElapsedSeconds"))

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("showTimer = showRest"))
        assertTrue(screen.contains("onOpenTimer = { session?.id?.let(onOpenRest) }"))
        assertFalse(
            "RestDock must not be a sibling of LogBar in the screen",
            screen.contains("RestDock("),
        )
        val bottomBar = screen.indexOf("bottomBar")
        val logBar = screen.indexOf("LogBar(")
        val lazy = screen.indexOf("LazyColumn(")
        assertTrue(bottomBar >= 0 && logBar > bottomBar && lazy > logBar)
        assertFalse(screen.substring(lazy).contains("LogBar("))
        assertFalse(screen.substring(lazy).contains("FloorTimerSlot("))
    }

    @Test
    fun restLengthEditsInlineAndModesDoNotStack() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.restLengthIsInlineWheel())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.oneClockTwoModes())
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("fun FloorTimerSlot"))
        assertTrue(dock.contains("fun SetWorkDock"))
        assertTrue(dock.contains("SnapValueWheel("))
        assertTrue(dock.contains("FloorEntryWheels.restSecondsValues"))
        assertTrue(dock.contains("workout-rest-wheel"))
        assertTrue(dock.contains("FloorTimerSurface.mode(holdRunning, stopwatchRunning)"))
        val idleStart = dock.indexOf("fun RestIdleRow")
        val idle = dock.substring(idleStart)
        assertTrue(idle.contains("editing"))
        assertTrue(idle.contains("SnapValueWheel("))
        assertFalse(
            "idle duration must not push the rest page",
            idle.substring(0, idle.indexOf("fun RestLinearTrack")).contains("onOpenRest"),
        )
    }

    @Test
    fun liveCardDoesNotHostASecondSetCountdown() {
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        assertFalse(
            "running hold clock must not stay on the lift card",
            card.contains("WorkoutTestTags.HOLD_CLOCK"),
        )
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("workout-hold-clock"))
        assertTrue(dock.contains("FloorTimerSurface.setClockSeconds"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
