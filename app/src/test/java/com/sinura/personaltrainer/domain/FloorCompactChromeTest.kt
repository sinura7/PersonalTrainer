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
    fun packetAHidesEmptyTimerDockAndIdleStartNext() {
        assertTrue(FloorCompactChrome.emptySessionHidesTimerDock())
        assertFalse(FloorCompactChrome.showIdleStartNext())
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
    fun compactFloorWeightAndRepsAreSteppers() {
        assertFalse(FloorCompactChrome.weightAndRepsAreWheels())
        assertFalse(FloorCompactChrome.floorFieldGlyphsReplaceLabels())
    }

    @Test
    fun warmupSitsOutsideTheRpeTrack() {
        assertTrue(FloorCompactChrome.warmupOutsideRpeTrack())
        assertTrue(FloorCompactChrome.rpeTrackFitsWithoutScroll())
    }

    @Test
    fun packet2SplitsInstrumentStripFromDockControls() {
        assertTrue(FloorCompactChrome.headerIsReadOnlyInstrumentStrip())
        assertTrue(FloorCompactChrome.oneClockTwoModes())
        assertTrue(FloorCompactChrome.restLengthIsInlineWheel())
    }

    @Test
    fun packet3OffersAManualSetStopwatch() {
        assertTrue(FloorCompactChrome.manualSetStopwatch())
    }
}
