package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorCompactChromeTest {
    @Test
    fun anEmptySessionHidesTheTimerDock() {
        assertTrue(FloorCompactChrome.emptySessionHidesTimerDock())
    }

    @Test
    fun coachStripIsCompactOnPrepareOrWhenDraftMatchesSuggestion() {
        assertTrue(FloorCompactChrome.coachUsesCompactStrip(preparePhase = true, entryMatchesSuggestion = false))
        assertTrue(FloorCompactChrome.coachUsesCompactStrip(preparePhase = false, entryMatchesSuggestion = true))
        assertFalse(FloorCompactChrome.coachUsesCompactStrip(preparePhase = false, entryMatchesSuggestion = false))
    }

    @Test
    fun setTypeToggleUsesDenseChips() {
        assertTrue(FloorCompactChrome.setTypeToggleUsesCompactChips())
    }
}
