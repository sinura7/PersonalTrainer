package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The logging loop must keep the hero numerals on screen. Anchoring bringIntoView
 * on the set history (the old logged-sets panel) is the bug this object exists to prevent.
 *
 * The anchor's place is rendered too: SET_ENTRY holds both numerals in either layout
 * (WeightRepsEditorRenderTest), never appears in the set history
 * (SetHistoryStripRenderTest), and survives a real save, a lift switch and an edit on the
 * screen (FloorScreenWiringRenderTest). The screen-order pins below stay as the guard.
 */
class LogLoopBringIntoViewTest {
    @Test
    fun afterLogAnchorIsTheEntryWellsNotTheLoggedSetsPanel() {
        assertEquals(WorkoutTestTags.SET_ENTRY, LogLoopBringIntoView.ANCHOR_TAG)
        assertNotEquals(WorkoutTestTags.SET_HISTORY, LogLoopBringIntoView.ANCHOR_TAG)
        assertFalse(LogLoopBringIntoView.ANCHOR_TAG.contains(other = "logged", ignoreCase = true))
        assertFalse(LogLoopBringIntoView.ANCHOR_TAG.contains(other = "sets-panel", ignoreCase = true))
        assertFalse(LogLoopBringIntoView.ANCHOR_TAG.contains(other = "history", ignoreCase = true))
        val history = readOwned("ui/workout/SetHistoryStrip.kt")
        assertFalse(history.contains("WorkoutTestTags.SET_ENTRY"))
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
        val text = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(text.contains("LogLoopBringIntoView.entryListIndex()"))
        assertTrue(text.contains("listState.scrollToItem(LogLoopBringIntoView.entryListIndex())"))
        assertTrue(
            "an edit reveals the entry; ordinary saves keep the viewport",
            text.contains("if (state.editingSetId != null) listState.animateScrollToItem(LogLoopBringIntoView.editRevealIndex())"),
        )
        assertFalse(text.contains("itemsIndexed("))
        val lazy = text.indexOf("LazyColumn(")
        val header = text.indexOf("item(key = \"exercise-header\")")
        val entry = text.indexOf("item(key = \"entry\")")
        assertTrue("the identity is list offset 0, with the numerals right under it", lazy in 0 until header)
        assertTrue(header in 0 until entry)
        val beforeHeader = text.substring(lazy, header)
        assertTrue(beforeHeader.contains("if (!session.hasLifts()) {"))
        assertEquals(
            "only the empty-session placeholder can precede the identity",
            1,
            Regex("item\\(key = ").findAll(beforeHeader).count(),
        )
        assertTrue(text.indexOf("WeightRepsEditor(") > entry)
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
