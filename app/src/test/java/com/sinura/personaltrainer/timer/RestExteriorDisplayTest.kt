package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestExteriorDisplayTest {
    @Test
    fun idleSnapshotHidesProgressBarCue() {
        val model = RestExteriorDisplay.model(
            state = RestTimerSnapshot(running = false),
            nowElapsedRealtime = 0L,
            kickerRunning = "REST",
            kickerDone = "DONE",
        )
        assertEquals(RestIdleCopy.NOT_RUNNING, model.timeText)
        assertFalse(model.showProgress)
        assertFalse(model.atZero)
    }

    @Test
    fun runningSnapshotScalesProgressWithRemainingFraction() {
        val model = RestExteriorDisplay.model(
            state = RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = 30_000L,
                totalSeconds = 60,
            ),
            nowElapsedRealtime = 0L,
            kickerRunning = "REST",
            kickerDone = "DONE",
        )
        assertEquals("0:30", model.timeText)
        assertTrue(model.showProgress)
        assertEquals(500, model.progressLevel)
        assertFalse(model.atZero)
    }

    @Test
    fun zeroRemainingShowsDoneCopyOnBothSurfaces() {
        val model = RestExteriorDisplay.model(
            state = RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = 0L,
                totalSeconds = 90,
            ),
            nowElapsedRealtime = 0L,
            kickerRunning = "REST",
            kickerDone = "DONE",
        )
        assertEquals("DONE", model.kicker)
        assertEquals("0:00", model.timeText)
        assertTrue(model.atZero)
        assertEquals(0, model.progressLevel)
    }
}
