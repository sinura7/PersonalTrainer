package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet 2: read-only instrument strip; LogBar owns timer + advance + Log set;
 * rest length is presets / ±15; one clock, two modes.
 */
class FloorPacket2ToolbarTest {
    @Test
    fun headerStripIsReadOnlyAndTelemetryLivesOnTheHero() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.headerIsReadOnlyInstrumentStrip())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.headerShowsMinuteTelemetryOnly())
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
        val hero = readOwned("ui/workout/CurrentLiftCard.kt")
        assertTrue(hero.contains("SessionTelemetryCopy.line"))
        assertTrue(hero.contains("WorkoutTestTags.INSTRUMENT_STRIP"))
    }

    @Test
    fun logBarOwnsTimerAdvanceAndVoltLog() {
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("FloorTimerSlot("))
        assertTrue(bar.contains("showNext"))
        assertTrue(bar.contains("showFinish"))
        assertTrue(bar.contains("PrimaryGymButton("))
        assertTrue(bar.contains("height = Metrics.commit"))
        assertTrue(bar.contains("onSelectRestDuration"))
        assertTrue(bar.contains("holdElapsedSeconds"))

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("showTimer = showRest"))
        assertTrue(screen.contains("onOpenRest = { session?.id?.let(onOpenRest) }"))
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
    fun restLengthEditsWithPresetsAndModesDoNotStack() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.restLengthIsInlineWheel())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.oneClockTwoModes())
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("fun FloorTimerSlot"))
        assertTrue(dock.contains("fun SetWorkDock"))
        assertFalse(dock.contains("SnapValueWheel("))
        assertTrue(dock.contains("RestPresetChips("))
        assertTrue(dock.contains("FloorTimerSurface.mode("))
        val idleStart = dock.indexOf("fun RestIdleRow")
        val idle = dock.substring(idleStart)
        assertTrue(idle.contains("picking"))
        assertTrue(idle.contains("RestPresetChips("))
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
