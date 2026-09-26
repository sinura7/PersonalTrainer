package com.sinura.personaltrainer.workout

import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The saved-state keys a hold and a set clock are kept under, with their types, as the shipped
 * build writes them. A phone that updates while the app is stopped restores what the old build
 * saved, so a renamed key or a changed type loses a running hold or a timed set. Audit W2d-3a
 * moves the code that calls these writes; this holds what they write.
 */
class SavedStateFloorTimerKeysTest {
    @Test
    fun theTimerSavedKeysAreTheOnesTheShippedBuildWrote() {
        val handle = SavedStateHandle()
        val saved = SavedStateFloorTimer(handle)
        saved.writeHold(
            "ex-dead-hang",
            HoldTimerUiState(
                running = true,
                remainingSeconds = 25,
                totalSeconds = 30,
                elapsedSeconds = 5,
                startElapsedRealtime = 1_000L,
                deadlineElapsedRealtime = 31_000L,
                targetReached = false,
            ),
        )
        saved.writeStopwatch(
            "squat",
            SetStopwatchUiState(
                running = true,
                elapsedSeconds = 7,
                used = true,
                startElapsedRealtime = 2_000L,
                frozenElapsedSeconds = 3,
                exerciseId = "squat",
            ),
        )

        assertEquals(
            "one hold and one lift's set clock, and nothing else",
            setOf(
                "timer.hold.exerciseId",
                "timer.hold.running",
                "timer.hold.startMs",
                "timer.hold.deadlineMs",
                "timer.hold.total",
                "timer.hold.targetReached",
                "timer.sw.ids",
                "timer.sw.squat.running",
                "timer.sw.squat.startMs",
                "timer.sw.squat.frozen",
                "timer.sw.squat.elapsed",
                "timer.sw.squat.used",
            ),
            handle.keys(),
        )
        assertValue(handle, "timer.hold.exerciseId", "ex-dead-hang") { it is String }
        assertValue(handle, "timer.hold.running", true) { it is Boolean }
        assertValue(handle, "timer.hold.startMs", 1_000L) { it is Long }
        assertValue(handle, "timer.hold.deadlineMs", 31_000L) { it is Long }
        assertValue(handle, "timer.hold.total", 30) { it is Int }
        assertValue(handle, "timer.hold.targetReached", false) { it is Boolean }
        assertValue(handle, "timer.sw.ids", arrayListOf("squat")) { it is ArrayList<*> }
        assertValue(handle, "timer.sw.squat.running", true) { it is Boolean }
        assertValue(handle, "timer.sw.squat.startMs", 2_000L) { it is Long }
        assertValue(handle, "timer.sw.squat.frozen", 3) { it is Int }
        assertValue(handle, "timer.sw.squat.elapsed", 7) { it is Int }
        assertValue(handle, "timer.sw.squat.used", true) { it is Boolean }
    }

    private fun assertValue(handle: SavedStateHandle, key: String, expected: Any, isType: (Any?) -> Boolean) {
        val value = handle.get<Any>(key)
        assertTrue("$key is saved as the type the shipped build wrote; it holds $value", isType(value))
        assertEquals("$key holds the value written", expected, value)
    }
}
