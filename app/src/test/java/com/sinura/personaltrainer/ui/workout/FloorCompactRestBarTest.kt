package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.ui.theme.Metrics
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compact REST / HOLD / SET instrument bar: one 56 dp row, countdown
 * fill, mode controls. Honesty stays in the context rail so rest cannot
 * collide with the coach line at 360×800.
 */
class FloorCompactRestBarTest {
    @Test
    fun restHoldAndSetShareOneInstrumentBar() {
        assertTrue(FloorCompactChrome.timerIsCompactInstrumentBar())
        assertTrue(FloorCompactChrome.oneClockTwoModes())
        assertFalse(FloorCompactChrome.restLengthIsInlineWheel())
        assertEquals(56, Metrics.logTimerRow.value.toInt())
        assertEquals(48, Metrics.touchMin.value.toInt())
        assertEquals(72, Metrics.commit.value.toInt())

        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("fun FloorInstrumentBar"))
        assertTrue(dock.contains("heightIn(min = Metrics.logTimerRow)"))
        assertFalse(dock.contains("SnapValueWheel("))

        val restStart = dock.indexOf("fun RestDock")
        val restEnd = dock.indexOf("fun RestBatteryHintRow")
        assertTrue(restStart >= 0 && restEnd > restStart)
        val rest = dock.substring(restStart, restEnd)
        assertTrue(rest.contains("FloorInstrumentBar("))
        assertTrue(rest.contains("workout-rest-minus"))
        assertTrue(rest.contains("workout-rest-plus"))
        assertTrue(rest.contains("workout-rest-skip"))
        assertFalse("running rest must not stack a second track under the clock", rest.contains("RestLinearTrack("))
        assertFalse("honesty must not grow the timer row", rest.contains("RestHonestyRow("))
        assertFalse(rest.contains("\"10 seconds\""))
        assertTrue(rest.contains("Last ten seconds"))

        val holdStart = dock.indexOf("fun SetWorkDock")
        val holdEnd = dock.indexOf("fun RestDock")
        val hold = dock.substring(holdStart, holdEnd)
        assertTrue(hold.contains("FloorInstrumentBar("))
        assertTrue(hold.contains("HoldWork.liveDockSeconds"))
        assertTrue(hold.contains("workout-hold-clock"))
        assertTrue(hold.contains("heightIn(min = Metrics.logTimerRow)") || dock.contains("fun FloorInstrumentBar"))
    }

    @Test
    fun honestyAndLogStayInTheReservedRailAndVolt() {
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("RestHonestyRow("))
        assertTrue(bar.contains("RestHonestyCopy.pick("))
        assertTrue(bar.contains("CONTEXT_RAIL"))
        assertTrue(bar.contains("TIMER_ROW"))
        assertTrue(bar.contains("height = Metrics.commit"))
        assertTrue(bar.contains("next = false"))
        assertTrue(bar.contains("holdRemainingSeconds"))
        assertTrue(bar.contains("holdTotalSeconds"))
        assertFalse(FloorCompactChrome.liftCompleteReplacesClock())
        assertTrue(FloorCompactChrome.logButtonStaysAnchored())

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("holdRemainingSeconds = holdTimer.remainingSeconds"))
        assertTrue(screen.contains("holdTotalSeconds = holdTimer.totalSeconds"))
        assertTrue(screen.contains("MicroRecLine("))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
