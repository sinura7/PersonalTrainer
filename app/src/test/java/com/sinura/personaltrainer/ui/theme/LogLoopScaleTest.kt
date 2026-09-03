package com.sinura.personaltrainer.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogLoopScaleTest {
    @Test
    fun defaultAndLargeStaySideBySide() {
        assertFalse(LogLoopScale.stackEntryWells(1.0f))
        assertFalse(LogLoopScale.stackEntryWells(1.3f))
        assertFalse(LogLoopScale.stackEntryWells(1.59f))
    }

    @Test
    fun accessibilityTwoStacksTheWells() {
        assertTrue(LogLoopScale.stackEntryWells(1.6f))
        assertTrue(LogLoopScale.stackEntryWells(2.0f))
    }

    @Test
    fun homeHeadlineGainsALineWhenTheWellsStack() {
        assertEquals(2, LogLoopScale.headlineLines(1.0f))
        assertEquals(3, LogLoopScale.headlineLines(2.0f))
    }

    @Test
    fun tileNumeralShrinksOnceWellsStack() {
        assertEquals(36f, LogLoopScale.tileNumeral(1.0f).fontSize.value, 0.001f)
        assertEquals(36f / 1.6f, LogLoopScale.tileNumeral(1.6f).fontSize.value, 0.001f)
        assertEquals(18f, LogLoopScale.tileNumeral(2.0f).fontSize.value, 0.001f)
        assertEquals(
            InstrumentType.numeralLg.fontSize.value,
            LogLoopScale.tileNumeral(1.3f).fontSize.value,
            0.001f,
        )
    }

    @Test
    fun liveBarClusterHidesWhenTilesStack() {
        assertFalse(LogLoopScale.hideLiveBarCluster(1.0f))
        assertFalse(LogLoopScale.stackTiles(1.59f))
        assertTrue(LogLoopScale.hideLiveBarCluster(1.6f))
        assertTrue(LogLoopScale.stackTiles(1.6f))
        assertTrue(LogLoopScale.hideLiveBarCluster(2.0f))
    }
}
