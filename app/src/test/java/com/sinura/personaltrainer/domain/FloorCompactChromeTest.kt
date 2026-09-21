package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorCompactChromeTest {
    @Test
    fun exerciseHeaderIsTheOnlyCurrentLiftCopy() {
        assertFalse(FloorCompactChrome.showSelectedLiftDock())
        assertTrue(FloorCompactChrome.oneCurrentLiftOnFloor())
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
    fun heroNumeralsSitSideBySideUntilTheFontScaleStacksThem() {
        // ADR-027: weight and reps are two hero numerals split by a hairline. Only
        // LogLoopScale stacks them, once the system font is too large for the pair.
        assertFalse(FloorCompactChrome.stackWeightAboveReps())
        assertTrue(FloorCompactChrome.heroNumeralsSideBySide())
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
        // HOLD and SET keep the 56 dp instrument bar; rest is its own quiet dock card,
        // idle and running alike (ADR-027). Duration editing still lives in a sheet.
        assertTrue(FloorCompactChrome.timerIsCompactInstrumentBar())
        assertFalse(FloorCompactChrome.idleRestIsInstrumentBar())
        assertTrue(FloorCompactChrome.restIsDockCard())
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
        assertFalse(FloorCompactChrome.headerShowsMinuteTelemetryOnly())
    }

    @Test
    fun imageLedHeroCarriesSessionProgressWithoutAStatsStrip() {
        // ADR-030: stats moved off the logging path; session progress stays in the header.
        assertTrue(FloorCompactChrome.imageLedHero())
        assertFalse(FloorCompactChrome.statsRowUnderIdentity())
        assertTrue(FloorCompactChrome.headerShowsSessionProgress())
    }

    @Test
    fun setHistoryLivesOnTheFloorAndTheClockStaysOnLiftComplete() {
        // Today's sets are chips on the floor with Add set as the last chip once the plan
        // is met, so nothing hides Add set any more; the timer row stays reserved so Log
        // does not jump when a lift completes.
        assertTrue(FloorCompactChrome.setHistoryOnFloor())
        assertFalse(FloorCompactChrome.addSetHiddenOnFloor())
        assertFalse(FloorCompactChrome.liftCompleteReplacesClock())
        assertTrue(FloorCompactChrome.logButtonStaysAnchored())
        assertTrue(FloorCompactChrome.addLiftLivesInSwitcher())
        assertTrue(FloorCompactChrome.progressionKickerInline())
        assertFalse(FloorCompactChrome.showIdleStartNext())
    }

    @Test
    fun cheapDestructivesRunNowAndOfferUndo() {
        assertTrue(FloorCompactChrome.cheapDestructivesAreUndoable())
    }
}
