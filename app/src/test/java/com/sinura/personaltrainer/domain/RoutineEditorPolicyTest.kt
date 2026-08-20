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
}
