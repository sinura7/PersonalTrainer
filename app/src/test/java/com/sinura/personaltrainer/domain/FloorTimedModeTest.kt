package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorTimedModeTest {
    @Test
    fun emptySessionIsNone() {
        assertEquals(
            FloorTimedMode.NONE,
            FloorTimedModeResolver.resolve(
                hasLifts = false,
                holdActive = true,
                stopwatchRunning = true,
                restRunning = true,
                restComplete = true,
            ),
        )
    }

    @Test
    fun holdWinsThenStopwatchThenRest() {
        assertEquals(
            FloorTimedMode.HOLD_RUNNING,
            FloorTimedModeResolver.resolve(
                hasLifts = true,
                holdActive = true,
                stopwatchRunning = true,
                restRunning = true,
                restComplete = false,
            ),
        )
        assertEquals(
            FloorTimedMode.STOPWATCH_RUNNING,
            FloorTimedModeResolver.resolve(
                hasLifts = true,
                holdActive = false,
                stopwatchRunning = true,
                restRunning = true,
                restComplete = false,
            ),
        )
        assertEquals(
            FloorTimedMode.REST_RUNNING,
            FloorTimedModeResolver.resolve(
                hasLifts = true,
                holdActive = false,
                stopwatchRunning = false,
                restRunning = true,
                restComplete = false,
            ),
        )
        assertEquals(
            FloorTimedMode.REST_COMPLETE,
            FloorTimedModeResolver.resolve(
                hasLifts = true,
                holdActive = false,
                stopwatchRunning = false,
                restRunning = false,
                restComplete = true,
            ),
        )
        assertEquals(
            FloorTimedMode.REST_IDLE,
            FloorTimedModeResolver.resolve(
                hasLifts = true,
                holdActive = false,
                stopwatchRunning = false,
                restRunning = false,
                restComplete = false,
            ),
        )
    }

    @Test
    fun atMostOneActiveTimedMode() {
        FloorTimedMode.entries.forEach { mode ->
            val active = FloorTimedModeResolver.isActive(mode)
            if (mode == FloorTimedMode.REST_RUNNING ||
                mode == FloorTimedMode.HOLD_RUNNING ||
                mode == FloorTimedMode.STOPWATCH_RUNNING
            ) {
                assertTrue(mode.name, active)
            } else {
                assertFalse(mode.name, active)
            }
        }
    }

    @Test
    fun timeSetOnlyWhenRestIdleOrCompleteAndLiftIsNotHold() {
        assertTrue(
            FloorTimedModeResolver.offerSetClock(FloorTimedMode.REST_IDLE, isHoldLift = false),
        )
        assertTrue(
            FloorTimedModeResolver.offerSetClock(FloorTimedMode.REST_COMPLETE, isHoldLift = false),
        )
        assertFalse(
            FloorTimedModeResolver.offerSetClock(FloorTimedMode.REST_RUNNING, isHoldLift = false),
        )
        assertFalse(
            FloorTimedModeResolver.offerSetClock(FloorTimedMode.HOLD_RUNNING, isHoldLift = false),
        )
        assertFalse(
            FloorTimedModeResolver.offerSetClock(FloorTimedMode.STOPWATCH_RUNNING, isHoldLift = false),
        )
        assertFalse(
            FloorTimedModeResolver.offerSetClock(FloorTimedMode.REST_IDLE, isHoldLift = true),
        )
    }
}
