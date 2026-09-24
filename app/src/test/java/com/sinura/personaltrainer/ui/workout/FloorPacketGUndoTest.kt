package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Packet G (WE-G) on the redesigned floor: every saved set is a chip whose tap opens a
 * named Revise / Delete menu (never a swipe), Skip for now sits on the header overflow,
 * swap / remove stay visible with a reason when illegal, and undo is a serialized LIFO
 * queue with an accessibility-extended dwell that survives process death.
 *
 * What those do is held where it can be seen: the chips' menu and the sheet's per-row
 * overflow in SetHistoryStripRenderTest and WorkoutSetsSheetRenderTest; Skip, the blocked
 * Swap / Remove and their reason in LiftOptionsRenderTest; the undo host's words, dwell, key
 * and live region, the dock's and the banner's wiring and the haptics of a delete and its
 * undo in FloorUndoHostRenderTest; the queue's order, its serialization, its feedback and its
 * survival across process death in ActiveWorkoutViewModelTest, UndoQueueTest and
 * FloorVmContractTest (audit T1c-1). The bans stay here: the old single undo slot and swipe
 * gestures must not come back. The slot's bans read both workout packages, `ui/workout` and
 * `workout`, because undo already lives partly in the second and W2d moves more of it there.
 */
class FloorPacketGUndoTest {
    @Test
    fun setChipsKeepNoHiddenSelectGesture() {
        // No hidden tap-to-select gesture: the chip is the row and its menu is the act.
        val strip = ownedSource("ui/workout/SetHistoryStrip.kt")
        assertFalse(strip.contains("selectedSetId"))
        assertFalse(strip.contains("SetRowAction("))
        assertFalse(strip.contains("onSelect ="))
    }

    @Test
    fun theSingleVolatileUndoSlotStaysGone() {
        // The single volatile slot is gone: a second delete no longer expires the first.
        assertWorkoutPackageLacks("undoableDelete")
        assertWorkoutPackageLacks("undoableRemove")
        assertWorkoutPackageLacks("onUndoOfferHandled")
        assertFalse(ownedSource("ui/workout/ActiveWorkoutScreen.kt").contains("onUndoOfferHandled"))
        assertFalse(ownedSource("ui/workout/WorkoutDock.kt").contains("onUndoOfferHandled"))
    }

    @Test
    fun noSwipeToDeleteOnTheFloor() {
        val strip = ownedSource("ui/workout/SetHistoryStrip.kt")
        val body = sourceFrom(strip, "internal fun SetHistoryStrip(")
        assertFalse(body.lowercase().contains("swipe"))
        assertFalse(strip.contains("SwipeToDismiss"))
        assertFalse(strip.contains("DismissValue"))
        val sheet = ownedSource("ui/workout/WorkoutSavedSets.kt")
        assertFalse(sheet.lowercase().contains("swipe"))
        assertFalse(sheet.contains("DismissValue"))
        val table = ownedSource("ui/components/SetTable.kt")
        assertFalse(table.contains("SwipeToDismiss"))
        assertFalse(table.contains("DismissValue"))
    }
}
