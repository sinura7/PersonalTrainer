package com.sinura.personaltrainer.data.draft

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.repository.RoutineEditChange
import com.sinura.personaltrainer.data.repository.RoutineEditCommand
import com.sinura.personaltrainer.data.repository.RoutineEditRecord
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RoutineDraftsTest {
    private lateinit var deps: FakeAppDependencies
    private lateinit var directory: File
    private var writes = 0
    private var failWrite = false
    private var lostAck = false
    private var failRead = false
    private var restoring = false
    @Before fun setup() {
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext())
        directory = File.createTempFile("p09-routine-", "").apply { delete(); mkdirs() }
    }
    @After fun close() { deps.close(); directory.deleteRecursively() }
    private fun service() = RoutineDrafts(store = DurableDraftStore(directory, Dispatchers.Unconfined),
        maintenance = deps.dbMaintenance, restoreOpen = { restoring },
        readRecord = deps.routineRepository::editorRecord,
        inspectSaved = { if (failRead) throw IOException("synthetic unavailable read") else deps.routineRepository.inspectEditorCommand(it) },
        commitLocked = { if (failWrite) throw IOException("synthetic refused write")
            writes++; deps.routineRepository.commitEditorCommandLocked(it)
            if (lostAck) throw IOException("synthetic lost acknowledgement") }, dispatcher = Dispatchers.Unconfined)
    private fun creation(fields: RoutineDraftFields) = RoutineEditCommand(changes = listOf(
        RoutineEditChange(id = fields.routineId, before = null,
            after = RoutineEditRecord(routine = RoutineEntity(id = fields.routineId, name = "Upper A", notes = "pause", createdAt = 10, updatedAt = 10), items = emptyList()))))

    @Test fun reopeningRetainsRawFieldsSelectionAndStableCreateIdWithoutBusinessWrites() = runBlocking {
        val port = service(); val handle = port.open(id = null, initialExerciseId = "bench")
        val intended = handle.fields.copy(name = "Upper A — strength", notes = "Pause at the bottom…", pasteText = "Upper A\n1. Bench — 3×8")
        port.keep(handle, intended)
        val reopened = service().open(id = null, initialExerciseId = null)
        assertEquals(intended, reopened.fields)
        assertTrue(reopened.resumed)
        assertEquals(0, writes)
        assertEquals(0, deps.routineRepository.count())
    }

    @Test fun failedCreateReopensAndRetriesTheFrozenStableIdentityOnce() = runBlocking {
        val port = service(); val handle = port.open(id = null, initialExerciseId = null)
        failWrite = true
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, port.execute(handle, ::creation))
        val id = handle.fields.routineId
        val reopenedPort = service(); val reopened = reopenedPort.open(id = null, initialExerciseId = null)
        assertTrue(reopened.pending)
        assertEquals(0, writes)
        failWrite = false
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, reopenedPort.retry(reopened))
        assertEquals(1, writes)
        assertEquals(id, deps.routineRepository.getById(id)?.id)
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, reopenedPort.retry(reopened))
        assertEquals(1, writes)
        assertEquals(1, deps.routineRepository.count())
    }

    @Test fun lostAcknowledgementSettlesSavedCreateWithoutReplay() = runBlocking {
        val port = service(); val handle = port.open(id = null, initialExerciseId = null)
        lostAck = true
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, port.execute(handle, ::creation))
        assertFalse(handle.pending)
        assertEquals(1, writes)
        val reopenedPort = service(); val reopened = reopenedPort.open(id = handle.fields.routineId, initialExerciseId = null)
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, reopenedPort.inspect(reopened))
        assertEquals(1, writes)
        assertEquals(1, deps.routineRepository.count())
    }

    @Test fun unavailableReadKeepsTheCommandAndCannotSubmit() = runBlocking {
        val port = service(); val handle = port.open(id = null, initialExerciseId = null)
        failRead = true
        assertEquals(DraftRecoveryOutcome.UNAVAILABLE, port.execute(handle, ::creation))
        assertTrue(handle.pending)
        assertEquals(0, writes)
        assertEquals(DraftRecoveryOutcome.UNAVAILABLE, port.retry(handle))
        assertEquals(0, writes)
        failRead = false
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, port.retry(handle))
        assertEquals(1, writes)
    }

    @Test fun changedExistingOwnerKeepsRawFieldsAndRefusesTheOldCommand() = runBlocking {
        val row = deps.routineRepository.create(name = "Upper A", notes = "before")
        val port = service(); val handle = port.open(id = row.id, initialExerciseId = null)
        val before = checkNotNull(handle.fields.before)
        val command = RoutineEditCommand(changes = listOf(RoutineEditChange(id = row.id, before = before,
            after = before.copy(routine = before.routine.copy(notes = "intended", updatedAt = before.routine.updatedAt + 1)))))
        port.keep(handle, handle.fields.copy(notes = "intended", notesEdited = true))
        failWrite = true
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, port.execute(handle) { command })
        deps.routineRepository.updateDetails(id = row.id, name = row.name, notes = "external")
        failWrite = false
        assertEquals(DraftRecoveryOutcome.CONFLICT, port.retry(handle))
        assertEquals("intended", handle.fields.notes)
        assertEquals("external", deps.routineRepository.getById(row.id)?.notes)
        assertEquals(0, writes)
    }

    @Test fun newerVisitRetiresOldCheckpointAndExplicitDiscardLeavesSavedEdits() = runBlocking {
        val port = service(); val old = port.open(id = null, initialExerciseId = null)
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, port.execute(old, ::creation))
        val current = port.open(id = old.fields.routineId, initialExerciseId = null)
        assertTrue(runCatching { port.keep(old, old.fields.copy(notes = "stale")) }.isFailure)
        port.discard(current)
        assertEquals("pause", deps.routineRepository.getById(old.fields.routineId)?.notes)
        assertEquals(1, deps.routineRepository.count())
    }

    @Test fun partialRoutineBatchRetriesOnlyTheOutstandingStableRecord() = runBlocking {
        val saved = deps.routineRepository.create(name = "Saved", notes = "kept")
        val before = checkNotNull(deps.routineRepository.editorRecord(saved.id))
        val new = RoutineEditRecord(routine = RoutineEntity(id = "p09-outstanding", name = "New", notes = "remaining", createdAt = 10, updatedAt = 10), items = emptyList())
        val command = RoutineEditCommand(changes = listOf(
            RoutineEditChange(id = saved.id, before = null, after = before),
            RoutineEditChange(id = new.routine.id, before = null, after = new)))
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, deps.routineRepository.inspectEditorCommand(command))
        deps.dbMaintenance.withMaintenanceLock { deps.routineRepository.commitEditorCommandLocked(command) }
        assertEquals(before, deps.routineRepository.editorRecord(saved.id))
        assertEquals(new, deps.routineRepository.editorRecord(new.routine.id))
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, deps.routineRepository.inspectEditorCommand(command))
    }

    @Test fun restoreInProgressCannotAdmitOrMutateTheRoutine() = runBlocking {
        val port = service(); val handle = port.open(id = null, initialExerciseId = null)
        restoring = true
        assertTrue(runCatching { port.execute(handle, ::creation) }.isFailure)
        assertEquals(0, writes)
        assertEquals(0, deps.routineRepository.count())
    }

    @Test fun aVisitInAnotherGraphFencesTheOldFieldsEvenBeforeAnyEdit() = runBlocking {
        val oldPort = service(); val old = oldPort.open(id = null, initialExerciseId = null)
        service().open(id = null, initialExerciseId = null)
        assertTrue(runCatching { oldPort.keep(old, old.fields.copy(name = "retired")) }.isFailure)
        assertEquals(0, writes)
    }

    @Test fun differentOwningFormsDoNotRetireEachOthersFields() = runBlocking {
        val a = deps.routineRepository.create(name = "A")
        val b = deps.routineRepository.create(name = "B")
        val port = service(); val first = port.open(id = a.id, initialExerciseId = null)
        port.open(id = b.id, initialExerciseId = null)
        port.keep(first, first.fields.copy(notes = "unfinished A", notesEdited = true))
        assertEquals("unfinished A", first.fields.notes)
        assertEquals("", deps.routineRepository.getById(a.id)?.notes)
    }

    @Test fun restoreWithTheSameIdButChangedContentRequiresFreshReviewBeforeAnyMutation() = runBlocking {
        val row = deps.routineRepository.create(name = "Before", notes = "before")
        val port = service(); val handle = port.open(id = row.id, initialExerciseId = null)
        deps.dbMaintenance.withMaintenanceLock {
            deps.routineRepository.updateDetails(id = row.id, name = "Restored", notes = "restored")
            val store = DurableDraftStore(directory, Dispatchers.Unconfined)
            val fields = store.bind(DraftOwner(DraftForm.ROUTINE, "fields:existing:${row.id}"), RoutineFieldsCodec)
            val current = (fields.read() as DraftRead.PresentCheckpoint).checkpoint
            assertTrue(store.advanceGeneration(current.currentGeneration) is DraftGenerationChange.Changed)
        }
        port.keep(handle, handle.fields.copy(notes = "my fields", notesEdited = true))
        assertEquals(DraftRecoveryOutcome.CONFLICT, port.execute(handle) { fields ->
            val original = checkNotNull(fields.before)
            RoutineEditCommand(changes = listOf(RoutineEditChange(id = row.id, before = original,
                after = original.copy(routine = original.routine.copy(notes = "my fields")))))
        })
        assertEquals(0, writes)
        assertEquals("restored", deps.routineRepository.getById(row.id)?.notes)
    }

    @Test fun anEmptyRoutineUsedBySavedHistoryIsNotAnImplicitlyDisposableStub() = runBlocking {
        val row = deps.routineRepository.create(name = "Used")
        deps.database.workoutDao().upsertSession(session = WorkoutSessionEntity(id = "p09-saved-source",
            routineId = row.id, routineName = row.name, date = 10, notes = "", durationMinutes = 1,
            startedAt = 10, finishedAt = 60_010))
        assertFalse(deps.routineRepository.editorStubUnused(row.id))
        val before = checkNotNull(deps.routineRepository.editorRecord(row.id))
        val delete = RoutineEditCommand(changes = listOf(RoutineEditChange(id = row.id, before = before, after = null)))
        assertEquals(DraftRecoveryOutcome.CONFLICT, deps.routineRepository.inspectEditorCommand(delete))
        assertTrue(runCatching { deps.dbMaintenance.withMaintenanceLock {
            deps.routineRepository.commitEditorCommandLocked(delete) } }.isFailure)
        assertEquals(row.id, deps.database.workoutDao().getSessionRow("p09-saved-source")?.routineId)
        assertEquals(before, deps.routineRepository.editorRecord(row.id))
    }
}
