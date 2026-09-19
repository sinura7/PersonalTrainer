package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftChipCopyTest {
    @Test
    fun idleChipShowsSetProgressAndPrescribedRest() {
        val marks = LiftChipCopy.marks(
            workingLogged = 2,
            targetSets = 5,
            restSeconds = 90,
            restRunningOnThisLift = false,
            remainingSeconds = 0,
        )
        assertEquals("2/5", marks.setProgress)
        assertEquals("1:30", marks.restClock)
        assertFalse(marks.restLive)
        assertEquals(
            "Squat. Sets 2/5. Rest 1:30",
            LiftChipCopy.spoken("Squat", marks.setProgress, marks.restClock, marks.restLive),
        )
    }

    @Test
    fun runningRestOnThisLiftShowsRemainingTime() {
        val marks = LiftChipCopy.marks(
            workingLogged = 2,
            targetSets = 5,
            restSeconds = 90,
            restRunningOnThisLift = true,
            remainingSeconds = 47,
        )
        assertEquals("2/5", marks.setProgress)
        assertEquals("0:47", marks.restClock)
        assertTrue(marks.restLive)
        assertEquals(
            "Squat. Sets 2/5. Rest remaining 0:47",
            LiftChipCopy.spoken("Squat", marks.setProgress, marks.restClock, marks.restLive),
        )
    }

    @Test
    fun noPrescribedRestHidesTheIdleBadge() {
        val marks = LiftChipCopy.marks(
            workingLogged = 1,
            targetSets = 0,
            restSeconds = 0,
            restRunningOnThisLift = false,
            remainingSeconds = 0,
        )
        assertEquals("1", marks.setProgress)
        assertNull(marks.restClock)
        assertFalse(marks.restLive)
        assertEquals(
            "Hang. Sets 1",
            LiftChipCopy.spoken("Hang", marks.setProgress, marks.restClock, marks.restLive),
        )
    }

    @Test
    fun liveRestStillShowsWhenTheLiftHadNoPrescription() {
        val marks = LiftChipCopy.marks(
            workingLogged = 0,
            targetSets = 3,
            restSeconds = 0,
            restRunningOnThisLift = true,
            remainingSeconds = 90,
        )
        assertEquals("0/3", marks.setProgress)
        assertEquals("1:30", marks.restClock)
        assertTrue(marks.restLive)
    }

    @Test
    fun negativeLoggedSetsDoNotGoBelowZero() {
        val marks = LiftChipCopy.marks(
            workingLogged = -2,
            targetSets = 4,
            restSeconds = 60,
            restRunningOnThisLift = false,
            remainingSeconds = 0,
        )
        assertEquals("0/4", marks.setProgress)
        assertEquals("1:00", marks.restClock)
    }
}
