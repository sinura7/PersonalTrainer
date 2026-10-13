package com.sinura.personaltrainer.data.draft

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetSaveResolution
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.workout.WorkoutNumberDraft
import com.sinura.personaltrainer.workout.WorkoutNumberField
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FinishedSetDraftsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val io = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var directory: File
    private lateinit var session: WorkoutSession
    private var writes = 0
    private var refuseWrite = false
    private var loseAck = false
    private var unavailable = false
    private var restoreOpen = false
    private val files = FaultFiles()

    @Before fun prepare() = runBlocking {
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = io)
        directory = temporary.newFolder()
        session = seedTestWorkout(deps = deps, exerciseId = "finished-draft-squat",
            loggedSets = listOf(TestSetInput(100.0, 5)), finish = true).session
    }
    @After fun close() { deps.close() }

    @Test fun rawNumericTextUnitAndOriginalIdentityReopenWithoutWriting() = runBlocking {
        val service = service()
        val fields = fields().copy(numbers = listOf(WorkoutNumberDraft(
            exerciseId = session.sets.single().exerciseId, field = WorkoutNumberField.WEIGHT,
            text = "102.", weightUnit = WeightUnit.LBS, selectionStart = 4, selectionEnd = 4, open = true,
        )))
        service.keep(service.open(session.id), fields)
        val reopened = service().open(session.id)
        assertEquals(fields, reopened.draft)
        assertFalse(reopened.problem)
        assertEquals(0, writes)
        assertEquals(100.0, checkNotNull(deps.workoutRepository.getSession(session.id)).sets.single().weightKg, 0.0)
    }

    @Test fun privateAdmissionFailureCannotReachBusinessMutation() = runBlocking {
        val service = service(); val handle = service.open(session.id); val fields = fields()
        service.keep(handle, fields)
        files.refuseNext = true
        assertFalse(runCatching { service.write(handle, fields.command) }.isSuccess)
        assertEquals(0, writes)
        assertNull(service().open(session.id).pending)
    }

    @Test fun lostPrivateAdmissionAcknowledgementIsReadBeforeExactlyOneWrite() = runBlocking {
        val service = service(); val handle = service.open(session.id); val fields = fields()
        service.keep(handle, fields)
        files.loseNextAck = true
        val saved = service.write(handle, fields.command)
        assertEquals(fields.command.setId, saved.row.id)
        assertEquals(1, writes)
        service.settle(handle, fields.command)
        assertNull(service().open(session.id).draft)
    }

    @Test fun failedWriteReopensTheSameFrozenCommandAndExactRetry() = runBlocking {
        val first = service(); val handle = first.open(session.id); val fields = fields()
        first.keep(handle, fields); refuseWrite = true
        assertFalse(runCatching { first.write(handle, fields.command) }.isSuccess)
        val second = service(); val reopened = second.open(session.id)
        assertEquals(fields.command, reopened.pending)
        assertEquals(WorkoutSetSaveResolution.UNSAVED, second.inspect(reopened, fields.command))
        refuseWrite = false
        val result = second.write(reopened, fields.command)
        assertEquals(fields.original.completedAt, result.row.completedAt)
        assertEquals(session.sets.single().setNumber, result.row.setNumber)
        assertEquals(1, writes)
        second.settle(reopened, fields.command)
    }

    @Test fun acceptedWriteWithLostAckSettlesOnReopenWithoutAnotherWrite() = runBlocking {
        val first = service(); val handle = first.open(session.id); val fields = fields()
        first.keep(handle, fields); loseAck = true
        assertFalse(runCatching { first.write(handle, fields.command) }.isSuccess)
        val second = service(); val reopened = second.open(session.id)
        assertEquals(WorkoutSetSaveResolution.SAVED, second.inspect(reopened, fields.command))
        assertTrue(second.write(reopened, fields.command).alreadySaved)
        second.settle(reopened, fields.command)
        assertEquals(1, writes)
        assertNull(service().open(session.id).pending)
    }

    @Test fun exactAcceptedCleanupFailureCannotReplayBusinessWork() = runBlocking {
        val service = service(); val handle = service.open(session.id); val fields = fields()
        service.keep(handle, fields); service.write(handle, fields.command)
        service.inspect(handle, fields.command)
        files.refuseNext = true
        assertFalse(runCatching { service.settle(handle, fields.command) }.isSuccess)
        val next = service(); val reopened = next.open(session.id)
        next.settle(reopened, fields.command)
        assertEquals(1, writes)
        assertNull(service().open(session.id).draft)
    }

    @Test fun externalCorrectionIsConflictAndDiscardNeverUndoesIt() = runBlocking {
        val service = service(); val handle = service.open(session.id); val fields = fields()
        service.keep(handle, fields)
        deps.workoutRepository.updateSet(fields.original.setId, 120.0, 7, 9, false)
        assertFalse(runCatching { service.write(handle, fields.command) }.isSuccess)
        assertEquals(0, writes)
        service.discard(handle)
        assertEquals(120.0, checkNotNull(deps.workoutRepository.getSession(session.id)).sets.single().weightKg, 0.0)
    }

    @Test fun unavailableReadAndOpenRestoreJournalNeverGrantRetry() = runBlocking {
        val service = service(); val handle = service.open(session.id); val fields = fields()
        service.keep(handle, fields); unavailable = true
        assertFalse(runCatching { service.write(handle, fields.command) }.isSuccess)
        assertTrue(service().open(session.id).problem)
        unavailable = false; restoreOpen = true
        assertFalse(runCatching { service.write(handle, fields.command) }.isSuccess)
        assertEquals(0, writes)
    }

    @Test fun newOwningVisitRevokesOldCallbacksAndKeepsRetainedRow() = runBlocking {
        val service = service(); val old = service.open(session.id); val fields = fields()
        service.keep(old, fields); val current = service.open(session.id)
        assertFalse(runCatching { service.keep(old, fields.copy(values = fields.values.copy(reps = 10))) }.isSuccess)
        assertFalse(runCatching { service.discard(old) }.isSuccess)
        assertEquals(fields, current.draft)
    }

    @Test fun productionDestructionFenceReconcilesExactSaveWithoutDisablingTheFence() = runBlocking {
        val privateStore = DurableDraftStore(directory, io, files)
        var fences = 0
        deps.workoutRepository.beforeWorkoutDestruction = { event ->
            assertTrue(privateStore.invalidateOnce(event) is DraftGenerationChange.Changed)
            fences++
        }
        val service = service(); val handle = service.open(session.id); val fields = fields()
        service.keep(handle, fields)
        service.write(handle, fields.command)
        assertEquals(1, fences)
        assertEquals(WorkoutSetSaveResolution.SAVED, service.inspect(handle, fields.command))
        service.settle(handle, fields.command)
        assertEquals(1, writes)
        assertNull(service().open(session.id).draft)
    }

    @Test fun unreadableCheckpointExposesExplicitPrivateOnlyDiscard() = runBlocking {
        val first = service(); val fields = fields()
        first.keep(first.open(session.id), fields)
        val payload = directory.listFiles()!!.single { it.name != "generation.json" }
        payload.writeText("not a supported checkpoint")
        val next = service(); val broken = next.open(session.id)
        assertTrue(broken.problem)
        assertFalse(runCatching { next.keep(broken, fields) }.isSuccess)
        next.discard(broken)
        assertNull(service().open(session.id).draft)
        assertEquals(0, writes)
        assertEquals(session.sets, checkNotNull(deps.workoutRepository.getSession(session.id)).sets)
    }

    @Test fun negativeTimedOriginalAndLiteralSecondsRoundTripWithoutClamping() {
        val values = WorkoutSetValues(60.0, -3, null, false, Int.MAX_VALUE)
        val original = WorkoutSetSave(sessionId = "session", exerciseId = "exercise", setId = "set",
            completedAt = 5L, values = values, original = values)
        val draft = FinishedSetDraft("a".repeat(64), original, values.copy(durationSeconds = 1))
        assertEquals(draft, FinishedSetDraftCodec.decodeDraft(FinishedSetDraftCodec.encodeDraft(draft)))
        assertEquals(draft.command, FinishedSetDraftCodec.decodeCommand(FinishedSetDraftCodec.encodeCommand(draft.command)))
    }

    private fun fields(): FinishedSetDraft {
        val set = session.sets.single(); val before = WorkoutSetValues.from(set)
        val original = WorkoutSetSave(sessionId = set.sessionId, exerciseId = set.exerciseId, setId = set.id,
            completedAt = set.completedAt, values = before, original = before)
        return FinishedSetDraft(FinishedSetDrafts.stamp(session), original, before.copy(weightKg = 105.0, reps = 6, rpe = 9))
    }
    private fun service() = FinishedSetDrafts(
        store = DurableDraftStore(directory, io, files), maintenance = deps.dbMaintenance,
        dispatcher = io, restoreOpen = { restoreOpen },
        readSession = { if (unavailable) throw IOException("Synthetic read failure") else deps.workoutRepository.getSession(it) },
        inspectSaved = deps.workoutRepository::inspectFinishedSetSave,
        readSaved = deps.workoutRepository::readFinishedSetSaveResult,
        writeLocked = { command ->
            if (refuseWrite) throw IOException("Synthetic refusal")
            val result = deps.workoutRepository.saveFinishedSetLocked(command)
            writes++
            if (loseAck) { loseAck = false; throw IOException("Synthetic committed acknowledgement loss") }
            result
        },
    )
    private class FaultFiles : DraftFileIO by AtomicDraftFiles {
        var refuseNext = false
        var loseNextAck = false
        override fun replace(file: File, bytes: ByteArray) {
            if (refuseNext) { refuseNext = false; throw IOException("Synthetic private refusal") }
            AtomicDraftFiles.replace(file, bytes)
            if (loseNextAck) { loseNextAck = false; throw IOException("Synthetic private acknowledgement loss") }
        }
    }
}
