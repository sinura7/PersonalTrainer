package com.sinura.personaltrainer.util

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the two rules the shared `MutableStateFlow<String?>` never had. The wedges in
 * ActiveWorkoutViewModelTest and RoutineEditorViewModelTest were both a success in one
 * action wiping a fresh failure before any collector saw it — once across two actions, once
 * across two overlapping taps of the same action.
 */
class ErrorSlotTest {
    private val slot = ErrorSlot()

    @Test
    fun startsEmpty() {
        assertNull(slot.message)
    }

    @Test
    fun aFailureShowsItsMessage() {
        slot.fail(source = "logSet", message = "Reps must be at least 1.")
        assertEquals("Reps must be at least 1.", slot.message)
    }

    @Test
    fun aSuccessClearsItsOwnEarlierRefusal() {
        // The user tapped Log with bad input, fixed it, tapped again.
        slot.fail(source = "logSet", message = "Reps must be at least 1.")
        val started = slot.mark()
        slot.clearFrom(source = "logSet", before = started)
        assertNull(slot.message)
    }

    @Test
    fun aSuccessLeavesAnotherActionsFailureAlone() {
        // The race: logSet's tail clearing removeSelectedLift's refusal.
        val logStarted = slot.mark()
        slot.fail(source = "removeSelectedLift", message = "Delete its sets first.")
        slot.clearFrom(source = "logSet", before = logStarted)
        assertEquals("Delete its sets first.", slot.message)
    }

    @Test
    fun aSuccessLeavesARefusalRaisedAfterItStartedAlone() {
        // The other race: two commits of the same field. The first is slow and succeeds,
        // the second is instant and refused. The first's success must not take the
        // refusal down with it — it is newer than the first commit's tap.
        val firstCommit = slot.mark()
        slot.fail(source = "targets", message = "Sets and reps must be at least 1.")
        slot.clearFrom(source = "targets", before = firstCommit)
        assertEquals("Sets and reps must be at least 1.", slot.message)
    }

    @Test
    fun aNewerFailureReplacesAnOlderOne() {
        slot.fail(source = "logSet", message = "first")
        slot.fail(source = "removeSelectedLift", message = "second")
        assertEquals("second", slot.message)
    }

    @Test
    fun aSuccessMayClearANamedRelatedRefusal() {
        // Adding a lift answers "add at least one lift before saving" — named at the call site.
        slot.fail(source = "save", message = "Add at least one lift before starting this routine.")
        val started = slot.mark()
        slot.clearFrom(source = "addLift", before = started)
        slot.clearFrom(source = "save", before = started)
        assertNull(slot.message)
    }

    @Test
    fun clearingWithoutAMarkIgnoresAge() {
        // An explicit retry the user just asked for clears the family regardless.
        slot.fail(source = "load", message = "This routine is no longer available.")
        slot.clearFrom(source = "load")
        assertNull(slot.message)
    }

    @Test
    fun dismissClearsWhoeverRaisedIt() {
        slot.fail(source = "removeSelectedLift", message = "Delete its sets first.")
        slot.dismiss()
        assertNull(slot.message)
    }

    @Test
    fun clearingAnEmptySlotIsANoOp() {
        slot.clearFrom(source = "logSet", before = slot.mark())
        assertNull(slot.message)
    }

    @Test
    fun messagesProjectsTheTextAlone() = runBlocking {
        slot.fail(source = "logSet", message = "Reps must be at least 1.")
        assertEquals("Reps must be at least 1.", slot.messages.first())
        slot.dismiss()
        assertNull(slot.messages.first())
    }
}
