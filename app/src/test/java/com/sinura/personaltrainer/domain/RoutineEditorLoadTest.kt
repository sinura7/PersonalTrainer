package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineEditorLoadTest {
    private val newRoutine = RoutineEditorLoad(opensExisting = false)
    private val existing = RoutineEditorLoad(opensExisting = true)

    @Test
    fun aNewRoutineHasNothingToWaitFor() {
        assertEquals(EditorPhase.EDITING, newRoutine.phase)
    }

    @Test
    fun anExistingRoutineLoadsBeforeItEdits() {
        assertEquals(EditorPhase.LOADING, existing.phase)
        assertEquals(EditorPhase.EDITING, existing.onInitialRead(found = true).phase)
    }

    @Test
    fun anIdThatWasAlreadyStaleOnArrivalIsMissing() {
        assertEquals(EditorPhase.MISSING, existing.onInitialRead(found = false).phase)
    }

    @Test
    fun aNullEmissionBeforeTheRoutineHasBeenSeenIsNotADeletion() {
        // The whole point: Room's flow has simply not answered yet. Treating this as a
        // deletion shows "This routine is no longer available" for a routine that is fine.
        val state = existing.onInitialRead(found = true).onRoutineEmission(present = false)
        assertEquals(EditorPhase.EDITING, state.phase)
    }

    @Test
    fun aNullEmissionAfterTheRoutineHasBeenSeenIsADeletion() {
        val state = existing
            .onInitialRead(found = true)
            .onRoutineEmission(present = true)
            .onRoutineEmission(present = false)
        assertEquals(EditorPhase.MISSING, state.phase)
    }

    @Test
    fun aRoutineCreatedInThisSessionIsNeverReportedMissing() {
        // A brand-new routine never loaded an existing row, so a null emission after its own
        // creation and deletion is the editor's own doing, not a surprise to report.
        val state = newRoutine
            .onInitialRead(found = false)
            .onRoutineEmission(present = true)
            .onRoutineEmission(present = false)
        assertEquals(EditorPhase.EDITING, state.phase)
    }

    @Test
    fun missingIsTerminal() {
        val gone = existing.onInitialRead(found = false)
        assertEquals(EditorPhase.MISSING, gone.onRoutineEmission(present = true).phase)
        assertEquals(EditorPhase.MISSING, gone.onInitialRead(found = true).phase)
    }

    @Test
    fun markMissingWinsOverAnyPhase() {
        assertEquals(EditorPhase.MISSING, newRoutine.markMissing().phase)
        assertEquals(EditorPhase.MISSING, existing.markMissing().phase)
    }
}
