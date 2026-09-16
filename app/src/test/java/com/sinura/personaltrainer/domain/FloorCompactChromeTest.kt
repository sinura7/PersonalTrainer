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
    fun rpeChipsBelongToAWorkingDraftNotToRest() {
        assertTrue(FloorCompactChrome.showOptionalLogOptions())
        assertTrue(FloorCompactChrome.showOptionalLogOptions(isWarmup = false))
        assertFalse(FloorCompactChrome.showOptionalLogOptions(isWarmup = true))
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
        assertFalse(FloorCompactChrome.restLengthIsInlineWheel())
    }

    @Test
    fun packet3OffersAManualSetStopwatch() {
        assertTrue(FloorCompactChrome.manualSetStopwatch())
    }

    @Test
    fun packetCShowsOneLiftMinuteTelemetryAndNotesOffLoop() {
        assertTrue(FloorCompactChrome.oneCurrentLiftOnFloor())
        assertTrue(FloorCompactChrome.notesLeaveTheLogLoop())
        assertTrue(FloorCompactChrome.headerShowsMinuteTelemetryOnly())
    }

    @Test
    fun packetFHidesAddSetAndKeepsTheClockOnLiftComplete() {
        assertTrue(FloorCompactChrome.addSetHiddenOnFloor())
        assertFalse(FloorCompactChrome.liftCompleteReplacesClock())
        assertTrue(FloorCompactChrome.logButtonStaysAnchored())
        assertTrue(FloorCompactChrome.imageLedHero())
        assertTrue(FloorCompactChrome.addLiftLivesInSwitcher())
        assertTrue(FloorCompactChrome.progressionKickerInline())
        assertFalse(FloorCompactChrome.showIdleStartNext())
    }
}
