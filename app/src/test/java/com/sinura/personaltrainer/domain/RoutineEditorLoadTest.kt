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

    @Test
    fun aFailedOpeningReadLeavesLoadingForAnExplicitError() {
        // The bug this guards: a throwing hydration left `hydrated` false forever, so the editor
        // sat on LOADING with no way out. FAILED is a distinct, actionable state.
        assertEquals(EditorPhase.FAILED, existing.markFailed().phase)
    }

    @Test
    fun markFailedDoesNotTearDownAnEditorThatAlreadyHydrated() {
        // A read that throws after the routine is on screen must not wipe it.
        val hydrated = existing.onInitialRead(found = true)
        assertEquals(EditorPhase.EDITING, hydrated.markFailed().phase)
    }

    @Test
    fun aNewRoutineNeverFails() {
        // Nothing is read for a brand-new routine, so there is nothing that can fail to read.
        assertEquals(EditorPhase.EDITING, newRoutine.markFailed().phase)
    }

    @Test
    fun missingWinsOverAFailedRead() {
        val gone = existing.onInitialRead(found = false)
        assertEquals(EditorPhase.MISSING, gone.markFailed().phase)
    }

    @Test
    fun retryDropsAFailedEditorBackToLoading() {
        val failed = existing.markFailed()
        assertEquals(EditorPhase.FAILED, failed.phase)
        assertEquals(EditorPhase.LOADING, failed.onRetry().phase)
    }

    @Test
    fun retryIsANoOpWhenNothingFailed() {
        assertEquals(EditorPhase.LOADING, existing.onRetry().phase)
        assertEquals(EditorPhase.EDITING, newRoutine.onRetry().phase)
    }
}
