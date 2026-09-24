package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The logging loop must keep the hero numerals on screen. Anchoring bringIntoView on the set
 * history (the old logged-sets panel) is the bug this object exists to prevent.
 *
 * The anchor's place is rendered: SET_ENTRY holds both numerals in either layout
 * (WeightRepsEditorRenderTest) and never appears in the set history
 * (SetHistoryStripRenderTest); the loop reads identity, stats, entry, effort, then history; a
 * lift switch lands on the new identity with its numerals, an edit reveals the entry, and a
 * save leaves the numerals where they were (FloorScreenWiringRenderTest). Since audit T1c-2 the
 * identity is the list's first row, taking the empty session's placeholder's place, in
 * EmptySessionFloorRenderTest. What stays is the object's own values and the bans.
 *
 * `ANCHOR_TAG` and `shouldScrollEntryToTop` have no production reader; W2a removes them and
 * these direct calls with them.
 */
class LogLoopBringIntoViewTest {
    @Test
    fun afterLogAnchorIsTheEntryWellsNotTheLoggedSetsPanel() {
        assertEquals(WorkoutTestTags.SET_ENTRY, LogLoopBringIntoView.ANCHOR_TAG)
        assertNotEquals(WorkoutTestTags.SET_HISTORY, LogLoopBringIntoView.ANCHOR_TAG)
        assertFalse(LogLoopBringIntoView.ANCHOR_TAG.contains(other = "logged", ignoreCase = true))
        assertFalse(LogLoopBringIntoView.ANCHOR_TAG.contains(other = "sets-panel", ignoreCase = true))
        assertFalse(LogLoopBringIntoView.ANCHOR_TAG.contains(other = "history", ignoreCase = true))
        assertFalse(ownedSource("ui/workout/SetHistoryStrip.kt").contains("WorkoutTestTags.SET_ENTRY"))
    }

    // Growth-triggered scrolling is intentionally removed by ADR-026. The
    // eight-save native journey now measures the actual entry/commit positions.

    @Test
    fun resumeAndLiftSwitchFocusTheEntryNotAVanishedListOffset() {
        assertEquals(0, LogLoopBringIntoView.entryListIndex())
        assertEquals("header, stats, then the numerals", 2, LogLoopBringIntoView.editRevealIndex())
        assertTrue(LogLoopBringIntoView.shouldScrollEntryToTop(null, "squat"))
        assertTrue(LogLoopBringIntoView.shouldScrollEntryToTop("squat", "row"))
        assertFalse(LogLoopBringIntoView.shouldScrollEntryToTop("squat", "squat"))
        assertFalse(ownedSource("ui/workout/ActiveWorkoutScreen.kt").contains("itemsIndexed("))
    }
}
