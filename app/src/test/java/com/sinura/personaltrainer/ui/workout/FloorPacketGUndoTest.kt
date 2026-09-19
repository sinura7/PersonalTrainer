package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet G (WE-G) on the redesigned floor: every saved set is a chip whose tap opens a
 * named Revise / Delete menu (never a swipe), Skip for now sits on the header overflow,
 * swap / remove stay visible with a reason when illegal, and undo is a serialized LIFO
 * queue with an accessibility-extended dwell that survives process death.
 *
 * No Packet H: no goldens, no TalkBack matrix. No swipe-to-delete anywhere on this surface.
 */
class FloorPacketGUndoTest {
    @Test
    fun setChipsOfferANamedReviseDeleteMenu() {
        val strip = readOwned("ui/workout/SetHistoryStrip.kt")
        assertTrue(strip.contains("InstrumentMenu("))
        assertTrue(strip.contains("SetRowCopy.actionsForSet("))
        assertTrue(strip.contains("SetRowCopy.reviseSet("))
        assertTrue(strip.contains("SetRowCopy.deleteSet("))
        assertTrue(strip.contains("Metrics.touchMin"))
        assertTrue(strip.contains("WorkoutTestTags.setOptions(set.id)"))
        assertTrue(strip.contains("onClickLabel = spokenAction"))
        assertTrue(readOwned("ui/workout/ActiveWorkoutScreen.kt").contains("fun setOptions(setId: String)"))
        // No hidden tap-to-select gesture: the chip is the row and its menu is the act.
        assertFalse(strip.contains("selectedSetId"))
        assertFalse(strip.contains("SetRowAction("))
        assertFalse(strip.contains("onSelect ="))
        // The full sheet keeps a visible 48 dp overflow per row with the same two verbs.
        val sheet = readOwned("ui/workout/WorkoutSavedSets.kt")
        assertTrue(sheet.contains("InstrumentMenu("))
        assertTrue(sheet.contains(".size(Metrics.touchMin).testTag(WorkoutTestTags.setOptions(set.id))"))
        assertTrue(sheet.contains("\"Edit set\""))
        assertTrue(sheet.contains("\"Delete set\""))

        val copy = readOwned("domain/SetRowCopy.kt")
        assertTrue(copy.contains("fun actionsForSet"))
        assertTrue(copy.contains("fun reviseSet"))
        assertTrue(copy.contains("fun deleteSet"))
    }

    @Test
    fun liftOverflowSkipsAndExplainsBlockedEdits() {
        val menu = readOwned("ui/workout/WorkoutOverflowMenu.kt")
        assertTrue(menu.contains("onSkip"))
        assertTrue(menu.contains("CurrentLiftCopy.SKIP"))
        assertTrue(menu.contains("CurrentLiftCopy.EDIT_BLOCKED_REASON"))
        assertTrue(menu.contains("enabled = canEdit"))
        assertTrue(menu.contains("CurrentLiftCopy.SWITCH"))
        assertTrue(menu.contains("WorkoutTestTags.LIFT_OPTIONS"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("canEdit = logged.isEmpty()"))
        assertTrue(screen.contains("onSkip = viewModel::skipForNow"))
        assertTrue(screen.contains("onSwitch = { liftSwitcherOpen = true }"))

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
        // While the dock is up, the offer rides in its companion slot with the same key and dwell.
        assertTrue(floor.contains("undoKey = undoEntries.lastOrNull()?.offer?.key"))
        assertTrue(floor.contains("undoDwellMs = undoDwellMs"))
        assertTrue(floor.contains("onUndo = viewModel::undoTopOffer"))
        assertTrue(floor.contains("onUndoDismissed = viewModel::onUndoOfferExpired"))
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("GymUndoHost("))
        assertTrue(dock.contains("offerKey = state.undoKey ?: state.undoMessage"))
        assertTrue(dock.contains("dwellMs = state.undoDwellMs"))
        assertFalse(dock.contains("onUndoOfferHandled"))
    }

    @Test
    fun noSwipeToDeleteOnTheFloor() {
        val strip = readOwned("ui/workout/SetHistoryStrip.kt")
        val body = strip.substring(strip.indexOf("internal fun SetHistoryStrip("))
        assertFalse(body.lowercase().contains("swipe"))
        assertFalse(strip.contains("SwipeToDismiss"))
        assertFalse(strip.contains("DismissValue"))
        val sheet = readOwned("ui/workout/WorkoutSavedSets.kt")
        assertFalse(sheet.lowercase().contains("swipe"))
        assertFalse(sheet.contains("DismissValue"))
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
