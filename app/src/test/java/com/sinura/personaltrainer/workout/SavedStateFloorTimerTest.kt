package com.sinura.personaltrainer.workout

import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedStateFloorTimerTest {
    @Test
    fun holdRoundTripKeepsTimestamps() {
        val handle = SavedStateHandle()
        val store = SavedStateFloorTimer(handle)
        store.writeHold(
            "ex-dead-hang",
            HoldTimerUiState(
                running = true,
                remainingSeconds = 20,
                totalSeconds = 30,
                elapsedSeconds = 10,
                startElapsedRealtime = 1_000L,
                deadlineElapsedRealtime = 31_000L,
                targetReached = false,
            ),
        )
        val restored = store.readHold("ex-dead-hang", nowElapsedRealtime = 11_000L)
        assertNotNull(restored)
        assertTrue(restored!!.running)
        assertEquals(1_000L, restored.startElapsedRealtime)
        assertEquals(31_000L, restored.deadlineElapsedRealtime)
        assertEquals(30, restored.totalSeconds)
    }

    @Test
    fun rebootClearsHoldWhenNowIsBeforeStart() {
        val handle = SavedStateHandle()
        val store = SavedStateFloorTimer(handle)
        store.writeHold(
            "ex-dead-hang",
            HoldTimerUiState(
                running = true,
                totalSeconds = 30,
                startElapsedRealtime = 50_000L,
                deadlineElapsedRealtime = 80_000L,
            ),
        )
        assertNull(store.readHold("ex-dead-hang", nowElapsedRealtime = 10L))
        assertNull(store.readHold("ex-dead-hang", nowElapsedRealtime = 10L))
    }

    @Test
    fun stopwatchIsPerLiftAndWrongLiftIsIgnored() {
        val handle = SavedStateHandle()
        val store = SavedStateFloorTimer(handle)
        store.writeStopwatch(
            "squat",
            SetStopwatchUiState(
                running = true,
                elapsedSeconds = 4,
                used = true,
                startElapsedRealtime = 2_000L,
                frozenElapsedSeconds = 0,
                exerciseId = "squat",
            ),
        )
        val squat = store.readStopwatch("squat", nowElapsedRealtime = 6_000L)
        assertNotNull(squat)
        assertTrue(squat!!.running)
        assertTrue(squat.used)
        assertEquals(2_000L, squat.startElapsedRealtime)
        assertNull(store.readStopwatch("row", nowElapsedRealtime = 6_000L))
    }

    @Test
    fun stopwatchRebootFreezesInsteadOfRunningBackwards() {
        val handle = SavedStateHandle()
        val store = SavedStateFloorTimer(handle)
        store.writeStopwatch(
            "squat",
            SetStopwatchUiState(
                running = true,
                elapsedSeconds = 9,
                used = true,
                startElapsedRealtime = 40_000L,
                frozenElapsedSeconds = 9,
                exerciseId = "squat",
            ),
        )
        val restored = store.readStopwatch("squat", nowElapsedRealtime = 10L)
        assertNotNull(restored)
        assertFalse(restored!!.running)
        assertTrue(restored.used)
        assertEquals(9, restored.elapsedSeconds)
        assertEquals(0L, restored.startElapsedRealtime)
    }
}
