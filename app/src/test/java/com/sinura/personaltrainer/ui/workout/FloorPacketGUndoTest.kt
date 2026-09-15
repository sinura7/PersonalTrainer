package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet G (WE-G): undo polish. A visible 48 dp overflow per set row, Skip for now on the
 * lift, swap/remove that stay visible with a reason when illegal, a serialized LIFO undo
 * queue with an accessibility-extended dwell that survives process death.
 *
 * No Packet H: no goldens, no TalkBack matrix. No swipe-to-delete anywhere on this surface.
 */
class FloorPacketGUndoTest {
    @Test
    fun setRowsOfferAVisibleOverflowMenu() {
        val panel = readOwned("ui/workout/LoggedSetsPanel.kt")
        assertTrue(panel.contains("InstrumentMenu("))
        assertTrue(panel.contains("SetRowCopy.actionsForSet"))
        assertTrue(panel.contains("SetRowCopy.reviseSet"))
        assertTrue(panel.contains("SetRowCopy.deleteSet"))
        assertTrue(panel.contains("Metrics.touchMin"))
        assertTrue(readOwned("ui/workout/ActiveWorkoutScreen.kt").contains("fun setOptions(setId: String)"))
        // The hidden tap-to-select gesture is gone; the overflow is always on screen.
        assertFalse(panel.contains("selectedSetId"))
        assertFalse(panel.contains("SetRowAction("))
        assertFalse(panel.contains("onSelect ="))

        val copy = readOwned("domain/SetRowCopy.kt")
        assertTrue(copy.contains("fun actionsForSet"))
        assertTrue(copy.contains("fun reviseSet"))
        assertTrue(copy.contains("fun deleteSet"))
    }

    @Test
    fun liftOverflowSkipsAndExplainsBlockedEdits() {
        val card = readOwned("ui/workout/CurrentLiftCard.kt")
        assertTrue(card.contains("onSkip"))
        assertTrue(card.contains("CurrentLiftCopy.SKIP"))
        assertTrue(card.contains("CurrentLiftCopy.EDIT_BLOCKED_REASON"))
        assertTrue(card.contains("enabled = canEdit"))

        val copy = readOwned("domain/CurrentLiftCopy.kt")
        assertTrue(copy.contains("SKIP = \"Skip for now\""))
        assertTrue(copy.contains("EDIT_BLOCKED_REASON = \"Delete its sets first\""))
        assertTrue(copy.contains("SKIP_NOWHERE"))
    }

    @Test
    fun undoIsALifoQueueWithSerializedMutationsAndSavedState() {
        val vm = readOwned("ui/workout/ActiveWorkoutViewModel.kt")
        assertTrue(vm.contains("UndoQueue.push"))
        assertTrue(vm.contains("UndoQueue.pop"))
        assertTrue(vm.contains("undoMutex"))
        assertTrue(vm.contains("withLock"))
        assertTrue(vm.contains("SavedStateFloorUndo"))
        assertTrue(vm.contains("fun skipForNow()"))
        assertTrue(vm.contains("fun undoTopOffer()"))
        assertTrue(vm.contains("fun onUndoOfferExpired()"))
        assertTrue(vm.contains("DeleteFeedback"))
        assertTrue(vm.contains("UndoDwell.dwellMs"))
        assertTrue(vm.contains("undoTimeout"))
        // The single volatile slot is gone: a second delete no longer expires the first.
        assertFalse(vm.contains("undoableDelete"))
        assertFalse(vm.contains("undoableRemove"))
        assertFalse(vm.contains("onUndoOfferHandled"))

        val queue = readOwned("domain/UndoQueue.kt")
        assertTrue(queue.contains("MAX_DEPTH"))
        assertTrue(queue.contains("fun <T> push"))
        assertTrue(queue.contains("fun <T> pop"))
        assertTrue(queue.contains("object UndoDwell"))

        val saved = readOwned("workout/SavedStateFloorUndo.kt")
        assertTrue(saved.contains("floorUndo.count"))
        assertTrue(saved.contains("floorUndo.dwellMs"))

        val tokens = readOwned("workout/FloorUndo.kt")
        assertTrue(tokens.contains("sealed interface FloorUndo"))
        assertTrue(tokens.contains("class DeletedSet"))
        assertTrue(tokens.contains("class RemovedLift"))
        assertTrue(tokens.contains("fun FloorUndo.toOffer"))
    }

    @Test
    fun undoHostKeysDwellPerOfferAndExtendsUnderTalkBack() {
        val host = readOwned("ui/components/GymStatus.kt")
        assertTrue(host.contains("fun GymUndoHost"))
        assertTrue(host.contains("UndoHostCopy.ACTION"))
        assertTrue(host.contains("offerKey"))
        assertTrue(host.contains("dwellMs"))
        assertTrue(host.contains("liveRegion"))

        val floor = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(floor.contains("GymUndoHost("))
        assertTrue(floor.contains("undoTopOffer"))
        assertTrue(floor.contains("onUndoOfferExpired"))
        assertTrue(floor.contains("offerKey = offer.key"))
        assertTrue(floor.contains("dwellMs = undoDwellMs"))
        assertTrue(floor.contains("deleteFeedback.collect"))
        assertTrue(floor.contains("onSkip = viewModel::skipForNow"))
        assertFalse(floor.contains("onUndoOfferHandled"))
    }

    @Test
    fun noSwipeToDeleteOnTheFloor() {
        val panel = readOwned("ui/workout/LoggedSetsPanel.kt")
        assertFalse(panel.contains("SwipeToDismiss"))
        assertFalse(panel.contains("swipe"))
        val table = readOwned("ui/components/SetTable.kt")
        assertFalse(table.contains("SwipeToDismiss"))
        assertFalse(table.contains("DismissValue"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
