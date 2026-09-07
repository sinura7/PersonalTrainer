package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides whether the routine editor may pop after Back or Save. The bug it
 * guards against: a details write that threw was logged and the screen popped anyway, so a
 * rename the owner had just watched "save" was never stored.
 */
class RoutineSaveOutcomeTest {
    private fun targets(vararg pairs: Pair<String, RoutineWriteOutcome>): List<RoutineTargetsOutcome> =
        pairs.map { (lift, outcome) -> RoutineTargetsOutcome(liftName = lift, outcome = outcome) }

    private fun unsaved(outcome: RoutineExitOutcome): RoutineExitOutcome.Unsaved =
        outcome as? RoutineExitOutcome.Unsaved ?: error("expected Unsaved, got $outcome")

    @Test
    fun storedAndNothingToWriteAreLanded() {
        assertTrue(RoutineWriteOutcome.Stored.landed)
        assertTrue(RoutineWriteOutcome.NothingToWrite.landed)
        assertFalse(RoutineWriteOutcome.Failed.landed)
        assertFalse(RoutineWriteOutcome.Rejected("no").landed)
    }

    @Test
    fun everythingLandedLetsTheScreenPop() {
        assertEquals(
            RoutineExitOutcome.Landed,
            RoutineEditorPolicy.exitOutcome(
                details = RoutineWriteOutcome.Stored,
                targets = targets("Squat" to RoutineWriteOutcome.Stored, "Row" to RoutineWriteOutcome.NothingToWrite),
            ),
        )
        assertEquals(
            RoutineExitOutcome.Landed,
            RoutineEditorPolicy.exitOutcome(details = RoutineWriteOutcome.NothingToWrite, targets = emptyList()),
        )
    }

    /** The defect itself: the details write threw. The exit must stay and say so. */
    @Test
    fun aFailedDetailsWriteBlocksTheExitAndSaysWhat() {
        val outcome = unsaved(
            RoutineEditorPolicy.exitOutcome(details = RoutineWriteOutcome.Failed, targets = emptyList()),
        )
        assertEquals(RoutineSaveCopy.DETAILS_FAILED, outcome.message)
        assertEquals(listOf(RoutineSaveCopy.DETAILS_ITEM), outcome.items)
    }

    @Test
    fun aFailedTargetWriteNamesTheLift() {
        val outcome = unsaved(
            RoutineEditorPolicy.exitOutcome(
                details = RoutineWriteOutcome.Stored,
                targets = targets("Squat" to RoutineWriteOutcome.Stored, "Row" to RoutineWriteOutcome.Failed),
            ),
        )
        assertEquals(RoutineSaveCopy.targetsFailed("Row"), outcome.message)
        assertEquals(listOf(RoutineSaveCopy.targetsItem("Row")), outcome.items)
    }

    /** A rejected value blocks Back as well as Save: a card reading 0 sets must not be walked past. */
    @Test
    fun aRejectedTargetBlocksTheExitWithTheCardsOwnWords() {
        val outcome = unsaved(
            RoutineEditorPolicy.exitOutcome(
                details = RoutineWriteOutcome.NothingToWrite,
                targets = targets("Squat" to RoutineWriteOutcome.Rejected(RoutineSaveCopy.TARGETS_REJECTED)),
            ),
        )
        assertEquals(RoutineSaveCopy.TARGETS_REJECTED, outcome.message)
        assertEquals(listOf(RoutineSaveCopy.targetsItem("Squat")), outcome.items)
    }

    /** Retrying cannot fix a rejection, so it is the message even when a write also failed. */
    @Test
    fun aRejectionOutranksAFailureInTheOneMessage() {
        val outcome = unsaved(
            RoutineEditorPolicy.exitOutcome(
                details = RoutineWriteOutcome.Failed,
                targets = targets(
                    "Squat" to RoutineWriteOutcome.Failed,
                    "Row" to RoutineWriteOutcome.Rejected(RoutineSaveCopy.TARGETS_REJECTED),
                ),
            ),
        )
        assertEquals(RoutineSaveCopy.TARGETS_REJECTED, outcome.message)
    }

    /** Among failures the targets are reported first, because the flush ran in that order. */
    @Test
    fun targetsAreReportedBeforeDetailsAmongFailures() {
        val outcome = unsaved(
            RoutineEditorPolicy.exitOutcome(
                details = RoutineWriteOutcome.Failed,
                targets = targets("Squat" to RoutineWriteOutcome.Failed),
            ),
        )
        assertEquals(RoutineSaveCopy.targetsFailed("Squat"), outcome.message)
    }

    @Test
    fun everyUnsavedWriteIsListedInFlushOrder() {
        val outcome = unsaved(
            RoutineEditorPolicy.exitOutcome(
                details = RoutineWriteOutcome.Failed,
                targets = targets(
                    "Squat" to RoutineWriteOutcome.Failed,
                    "Bench" to RoutineWriteOutcome.Stored,
                    "Row" to RoutineWriteOutcome.Rejected(RoutineSaveCopy.TARGETS_REJECTED),
                ),
            ),
        )
        assertEquals(
            listOf(
                RoutineSaveCopy.targetsItem("Squat"),
                RoutineSaveCopy.targetsItem("Row"),
                RoutineSaveCopy.DETAILS_ITEM,
            ),
            outcome.items,
        )
    }

    @Test
    fun theBackPromptListsWhatIsUnsavedAndSaysWhatIsKept() {
        val outcome = unsaved(
            RoutineEditorPolicy.exitOutcome(
                details = RoutineWriteOutcome.Failed,
                targets = targets("Squat" to RoutineWriteOutcome.Failed),
            ),
        )
        assertEquals(
            "Not saved: the targets for Squat, the name and notes. " +
                "Lifts you added, moved or removed are already saved.",
            outcome.backPromptBody,
        )
    }

    @Test
    fun copyIsFactualAboutWhichWritesWaitForSave() {
        assertEquals("Save", RoutineSaveCopy.saveLabel(saving = false))
        assertEquals("Saving…", RoutineSaveCopy.saveLabel(saving = true))
        assertEquals(SessionOrderCopy.SAVE_ROUTINE, RoutineSaveCopy.SAVE)
        assertEquals(
            "Lift changes save as you make them. Save keeps the name, notes and targets.",
            RoutineSaveCopy.WRITE_THROUGH,
        )
        assertEquals(
            "Could not save the name and notes. Your lifts are saved. Try again.",
            RoutineSaveCopy.DETAILS_FAILED,
        )
        assertEquals("Could not save the targets for Squat. Try again.", RoutineSaveCopy.targetsFailed("Squat"))
        assertEquals("Some changes are not saved", RoutineSaveCopy.UNSAVED_TITLE)
        assertEquals("Try again", RoutineSaveCopy.TRY_AGAIN)
        assertEquals("Leave without saving these", RoutineSaveCopy.LEAVE_ANYWAY)
    }
}
