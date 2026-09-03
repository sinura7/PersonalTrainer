package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The logging loop must keep the wells on screen. Anchoring bringIntoView
 * on the logged-sets panel is the bug this object exists to prevent.
 */
class LogLoopBringIntoViewTest {
    @Test
    fun afterLogAnchorIsTheEntryWellsNotTheLoggedSetsPanel() {
        assertEquals(WorkoutTestTags.SET_ENTRY, LogLoopBringIntoView.ANCHOR_TAG)
        assertFalse(LogLoopBringIntoView.ANCHOR_TAG.contains("logged", ignoreCase = true))
        assertFalse(LogLoopBringIntoView.ANCHOR_TAG.contains("sets-panel", ignoreCase = true))
    }

    @Test
    fun bringIntoViewOnlyWhenTheSetListGrew() {
        assertFalse(LogLoopBringIntoView.shouldBringIntoView(-1, 0))
        assertTrue(LogLoopBringIntoView.shouldBringIntoView(0, 1))
        assertTrue(LogLoopBringIntoView.shouldBringIntoView(2, 3))
        assertFalse(LogLoopBringIntoView.shouldBringIntoView(3, 3))
        assertFalse(LogLoopBringIntoView.shouldBringIntoView(4, 3))
    }
}
