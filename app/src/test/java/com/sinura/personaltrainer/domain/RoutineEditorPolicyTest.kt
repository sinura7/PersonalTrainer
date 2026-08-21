package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineEditorPolicyTest {
    @Test
    fun newRouteHasNoIncomingId() {
        assertNull(RoutineEditorPolicy.incomingId("new"))
        assertNull(RoutineEditorPolicy.incomingId(""))
        assertNull(RoutineEditorPolicy.incomingId(null))
        assertEquals("abc", RoutineEditorPolicy.incomingId("abc"))
    }

    @Test
    fun discardsEmptyCreatedStubOnly() {
        assertTrue(RoutineEditorPolicy.shouldDiscardStub(createdThisSession = true, exerciseCount = 0))
        assertFalse(RoutineEditorPolicy.shouldDiscardStub(createdThisSession = true, exerciseCount = 1))
        assertFalse(RoutineEditorPolicy.shouldDiscardStub(createdThisSession = false, exerciseCount = 0))
    }

    /** The bug this exists to prevent: rename, press back, rename silently gone. */
    @Test
    fun anUnsavedRenameIsPersistedOnExit() {
        val pending = RoutineEditorPolicy.detailsToPersistOnExit(
            hydrated = true,
            typedName = "Push Day A",
            typedNotes = "",
            storedName = "Push Day",
            storedNotes = "",
        )
        assertEquals(PendingDetails("Push Day A", ""), pending)
    }

    @Test
    fun anUnsavedNotesEditIsPersistedOnExit() {
        val pending = RoutineEditorPolicy.detailsToPersistOnExit(
            hydrated = true,
            typedName = "Push Day",
            typedNotes = "Keep the bar path vertical.",
            storedName = "Push Day",
            storedNotes = "",
        )
        assertEquals(PendingDetails("Push Day", "Keep the bar path vertical."), pending)
    }

    @Test
    fun anUntouchedEditorWritesNothing() {
        assertNull(
            RoutineEditorPolicy.detailsToPersistOnExit(
                hydrated = true,
                typedName = "Push Day",
                typedNotes = "Slow eccentric.",
                storedName = "Push Day",
                storedNotes = "Slow eccentric.",
            ),
        )
    }

    /** Clearing the field to retype and then leaving must not erase the stored name. */
    @Test
    fun aBlankNameNeverOverwritesTheStoredOne() {
        assertNull(
            RoutineEditorPolicy.detailsToPersistOnExit(
                hydrated = true,
                typedName = "",
                typedNotes = "",
                storedName = "Push Day",
                storedNotes = "",
            ),
        )
        assertNull(
            RoutineEditorPolicy.detailsToPersistOnExit(
                hydrated = true,
                typedName = "   ",
                typedNotes = "",
                storedName = "Push Day",
                storedNotes = "",
            ),
        )
    }

    /** A cleared name must not take an unrelated notes edit down with it. */
    @Test
    fun aBlankNameStillLetsTheNotesThrough() {
        val pending = RoutineEditorPolicy.detailsToPersistOnExit(
            hydrated = true,
            typedName = "  ",
            typedNotes = "Deload week.",
            storedName = "Push Day",
            storedNotes = "",
        )
        assertEquals(PendingDetails("Push Day", "Deload week."), pending)
    }

    @Test
    fun surroundingWhitespaceIsNotAChange() {
        assertNull(
            RoutineEditorPolicy.detailsToPersistOnExit(
                hydrated = true,
                typedName = "  Push Day  ",
                typedNotes = "",
                storedName = "Push Day",
                storedNotes = "",
            ),
        )
    }

    @Test
    fun theNameIsStoredTrimmed() {
        val pending = RoutineEditorPolicy.detailsToPersistOnExit(
            hydrated = true,
            typedName = "  Pull Day  ",
            typedNotes = "",
            storedName = "Push Day",
            storedNotes = "",
        )
        assertEquals(PendingDetails("Pull Day", ""), pending)
    }

    /**
     * Backing out before the editor has read the routine must write nothing. The typed fields
     * are still empty defaults at that point, and treating them as an edit erases real notes.
     */
    @Test
    fun nothingIsWrittenBeforeTheEditorHasHydrated() {
        assertNull(
            RoutineEditorPolicy.detailsToPersistOnExit(
                hydrated = false,
                typedName = "",
                typedNotes = "",
                storedName = "Push Day",
                storedNotes = "Slow eccentric.",
            ),
        )
    }

    /** The repository trims before it stores, so the comparison has to trim too. */
    @Test
    fun surroundingWhitespaceInTheNotesIsNotAChange() {
        assertNull(
            RoutineEditorPolicy.detailsToPersistOnExit(
                hydrated = true,
                typedName = "Push Day",
                typedNotes = "  Slow eccentric.  ",
                storedName = "Push Day",
                storedNotes = "Slow eccentric.",
            ),
        )
    }

    // ---- targets ----

    private fun targets(
        typedSets: Int? = 4,
        typedReps: Int? = 8,
        typedWeightKg: Double? = 60.0,
        typedRestSeconds: Int? = 90,
        storedSets: Int = 4,
        storedReps: Int = 8,
        storedWeightKg: Double? = 60.0,
        storedRestSeconds: Int = 90,
    ) = RoutineEditorPolicy.targetsToPersist(
        typedSets = typedSets,
        typedReps = typedReps,
        typedWeightKg = typedWeightKg,
        typedRestSeconds = typedRestSeconds,
        storedSets = storedSets,
        storedReps = storedReps,
        storedWeightKg = storedWeightKg,
        storedRestSeconds = storedRestSeconds,
    )

    @Test
    fun targetsIdenticalToStorageAreNotWritten() {
        assertNull(targets())
    }

    @Test
    fun aChangedFieldIsWrittenAndTheOthersAreCarried() {
        val pending = targets(typedReps = 12)
        assertEquals(PendingTargets(targetSets = 4, targetReps = 12, targetWeightKg = 60.0, restSeconds = 90), pending)
    }

    @Test
    fun anEmptySetsBoxMeansLeaveItAloneNotZero() {
        // Clearing the field to retype it passes through null for a whole keystroke. Storing a
        // zero there would turn "I am about to type 5" into a routine with no sets.
        assertNull(targets(typedSets = null))
        assertEquals(4, targets(typedSets = null, typedReps = 12)!!.targetSets)
    }

    @Test
    fun aTypedZeroIsPassedOnToBeRejected() {
        // Different from an empty box: the owner typed something, and something wrong. It has
        // to reach the caller so the caller can say so, rather than being silently absorbed.
        assertEquals(0, targets(typedSets = 0)!!.targetSets)
    }

    @Test
    fun clearingTheTargetWeightIsARealChange() {
        // Unlike sets and reps, an empty weight box means "no target" — the parse of an empty
        // string is null, and that null has to survive the round trip to the database.
        val pending = targets(typedWeightKg = null)
        assertEquals(PendingTargets(targetSets = 4, targetReps = 8, targetWeightKg = null, restSeconds = 90), pending)
    }

    @Test
    fun aWeightThatWasAlreadyAbsentStaysAbsentWithoutAWrite() {
        assertNull(targets(typedWeightKg = null, storedWeightKg = null))
    }

    @Test
    fun everyFieldCanChangeAtOnce() {
        assertEquals(
            PendingTargets(targetSets = 5, targetReps = 5, targetWeightKg = 100.0, restSeconds = 180),
            targets(typedSets = 5, typedReps = 5, typedWeightKg = 100.0, typedRestSeconds = 180),
        )
    }

    @Test
    fun anEmptyRestBoxKeepsTheStoredRest() {
        assertNull(targets(typedRestSeconds = null))
        assertEquals(90, targets(typedRestSeconds = null, typedSets = 5)!!.restSeconds)
    }
}
