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
        assertEquals(WorkoutTestTags.LOG_RECEIPT, LogLoopBringIntoView.AFTER_LOG_TAG)
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

    @Test
    fun resumeAndLiftSwitchFocusTheEntryNotAVanishedListOffset() {
        assertEquals(0, LogLoopBringIntoView.entryListIndex())
        assertTrue(LogLoopBringIntoView.shouldScrollEntryToTop(null, "squat"))
        assertTrue(LogLoopBringIntoView.shouldScrollEntryToTop("squat", "row"))
        assertFalse(LogLoopBringIntoView.shouldScrollEntryToTop("squat", "squat"))
        val screen = java.io.File("app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutScreen.kt")
            .takeIf { it.isFile }
            ?: java.io.File("../app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutScreen.kt")
        val text = screen.readText()
        assertTrue(text.contains("LogLoopBringIntoView.entryListIndex()"))
        assertTrue(text.contains("scrollToItem"))
        assertFalse(text.contains("itemsIndexed("))
    }
}
