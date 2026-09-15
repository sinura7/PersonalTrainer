package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorTimerSurfaceTest {
    @Test
    fun holdOrStopwatchIsSetModeOtherwiseRest() {
        assertEquals(
            FloorTimerSurface.Mode.SET,
            FloorTimerSurface.mode(holdRunning = true),
        )
        assertEquals(
            FloorTimerSurface.Mode.SET,
            FloorTimerSurface.mode(holdRunning = false, stopwatchRunning = true),
        )
        assertEquals(
            FloorTimerSurface.Mode.REST,
            FloorTimerSurface.mode(holdRunning = false, stopwatchRunning = false),
        )
    }

    @Test
    fun instrumentStatePrefersHoldThenStopwatchThenRestThenPlanned() {
        assertEquals(
            "HOLD 0:12",
            FloorTimerSurface.instrumentState(
                holdRunning = true,
                holdElapsedSeconds = 12,
                restRunning = true,
                restRemainingSeconds = 40,
                plannedRestSeconds = 90,
                stopwatchRunning = true,
                stopwatchElapsedSeconds = 8,
            ),
        )
        assertEquals(
            "SET 0:08",
            FloorTimerSurface.instrumentState(
                holdRunning = false,
                holdElapsedSeconds = 0,
                restRunning = true,
                restRemainingSeconds = 40,
                plannedRestSeconds = 90,
                stopwatchRunning = true,
                stopwatchElapsedSeconds = 8,
            ),
        )
        assertEquals(
            "rest 0:40",
            FloorTimerSurface.instrumentState(
                holdRunning = false,
                holdElapsedSeconds = 0,
                restRunning = true,
                restRemainingSeconds = 40,
                plannedRestSeconds = 90,
            ),
        )
        assertEquals(
            "rest 1:30",
            FloorTimerSurface.instrumentState(
                holdRunning = false,
                holdElapsedSeconds = 0,
                restRunning = false,
                restRemainingSeconds = 0,
                plannedRestSeconds = 90,
            ),
        )
    }

    @Test
    fun setClockNeverGoesNegative() {
        assertEquals(0, FloorTimerSurface.setClockSeconds(-3))
        assertEquals(8, FloorTimerSurface.setClockSeconds(8))
        assertTrue(FloorTimerSurface.SET_KICKER.isNotBlank())
    }

    @Test
    fun durationToLogWritesStopwatchOnlyWhenUsed() {
        val unused = SetStopwatchUiState()
        assertNull(
            FloorTimerSurface.durationToLog(
                hold = false,
                holdElapsedSeconds = 0,
                holdTotalSeconds = 0,
                holdRemainingSeconds = 0,
                holdDraftSeconds = null,
                stopwatch = unused,
            ),
        )
        val used = SetStopwatchUiState(running = true, elapsedSeconds = 0, used = true)
        assertEquals(
            1,
            FloorTimerSurface.durationToLog(
                hold = false,
                holdElapsedSeconds = 0,
                holdTotalSeconds = 0,
                holdRemainingSeconds = 0,
                holdDraftSeconds = null,
                stopwatch = used,
            ),
        )
        val paused = SetStopwatchUiState(running = false, elapsedSeconds = 12, used = true)
        assertEquals(
            12,
            FloorTimerSurface.durationToLog(
                hold = false,
                holdElapsedSeconds = 0,
                holdTotalSeconds = 0,
                holdRemainingSeconds = 0,
                holdDraftSeconds = null,
                stopwatch = paused,
            ),
        )
    }
}
