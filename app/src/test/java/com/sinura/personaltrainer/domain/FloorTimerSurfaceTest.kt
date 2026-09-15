package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorTimerSurfaceTest {
    @Test
    fun holdRunningIsSetModeOtherwiseRest() {
        assertEquals(FloorTimerSurface.Mode.SET, FloorTimerSurface.mode(holdRunning = true))
        assertEquals(FloorTimerSurface.Mode.REST, FloorTimerSurface.mode(holdRunning = false))
    }

    @Test
    fun instrumentStatePrefersHoldThenRestThenPlanned() {
        assertEquals(
            "HOLD 0:12",
            FloorTimerSurface.instrumentState(
                holdRunning = true,
                holdElapsedSeconds = 12,
                restRunning = true,
                restRemainingSeconds = 40,
                plannedRestSeconds = 90,
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
}
