package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorCompactChromeTest {
    @Test
    fun expandedCardIsTheOnlyCurrentLiftCopy() {
        assertFalse(FloorCompactChrome.showSelectedLiftDock())
    }

    @Test
    fun optionalRpePanelWaitsUntilRestRuns() {
        assertFalse(FloorCompactChrome.showOptionalLogOptions(restRunning = false))
        assertTrue(FloorCompactChrome.showOptionalLogOptions(restRunning = true))
    }

    @Test
    fun idleStartNextIsNotASecondVolt() {
        assertFalse(FloorCompactChrome.idleStartNextIsVolt())
    }

    @Test
    fun overflowSitsOnTheHeaderRow() {
        assertTrue(FloorCompactChrome.overflowOnHeaderRow())
    }

    @Test
    fun compactFloorStacksWeightAboveReps() {
        assertTrue(FloorCompactChrome.stackWeightAboveReps())
    }

    @Test
    fun compactFloorWeightAndRepsAreWheels() {
        assertTrue(FloorCompactChrome.weightAndRepsAreWheels())
    }

    @Test
    fun warmupSitsOutsideTheRpeTrack() {
        assertTrue(FloorCompactChrome.warmupOutsideRpeTrack())
        assertTrue(FloorCompactChrome.rpeTrackFitsWithoutScroll())
    }
}
