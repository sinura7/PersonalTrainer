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
    @Test fun durableBootCounterPreventsOldRunningClocksResumingAfterReboot() {
        val first = SavedStateFloorTimer(SavedStateHandle())
        val hold = HoldTimerUiState(running = true, totalSeconds = 30, startElapsedRealtime = 1000, deadlineElapsedRealtime = 31000)
        first.writeHold("plank", hold)
        first.writeStopwatch("squat", SetStopwatchUiState(running = true, used = true, elapsedSeconds = 9,
            frozenElapsedSeconds = 3, startElapsedRealtime = 1000, exerciseId = "squat"))
        val raw = first.checkpoint(8, "plank", hold, 10000)
        val same = SavedStateFloorTimer(SavedStateHandle())
        same.restoreCheckpoint(raw, 8)
        assertTrue(same.readHold("plank", 10000)!!.running)
        assertTrue(same.readStopwatch("squat", 10000)!!.running)
        val rebooted = SavedStateFloorTimer(SavedStateHandle())
        rebooted.restoreCheckpoint(raw, 9)
        assertNull(rebooted.readHold("plank", 20000))
        val watch = rebooted.readStopwatch("squat", 20000)!!
        assertFalse(watch.running)
        assertEquals(9, watch.elapsedSeconds)
    }
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

    /**
     * GET READY (P2b): a hold armed at 1 000 with a 5 s lead-in starts at 6 000. Read at 3 000 it
     * is not a reboot (now lies before the start, after the tap) and comes back with its tap
     * time; read at 500, before the tap, it is.
     */
    @Test
    fun aHoldGettingReadyIsNotAReboot() {
        val handle = SavedStateHandle()
        val store = SavedStateFloorTimer(handle)
        store.writeHold(
            "ex-plank",
            HoldTimerUiState(
                running = true,
                remainingSeconds = 30,
                totalSeconds = 30,
                startElapsedRealtime = 6_000L,
                deadlineElapsedRealtime = 36_000L,
                leadInStartElapsedRealtime = 1_000L,
                leadInRemainingSeconds = 5,
            ),
        )
        val ready = store.readHold("ex-plank", nowElapsedRealtime = 3_000L)
        assertNotNull(ready)
        assertTrue(ready!!.running)
        assertEquals(6_000L, ready.startElapsedRealtime)
        assertEquals(1_000L, ready.leadInStartElapsedRealtime)
        assertNull(store.readHold("ex-plank", nowElapsedRealtime = 500L))
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
