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
}
