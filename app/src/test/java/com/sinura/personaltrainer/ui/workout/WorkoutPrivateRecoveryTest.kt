package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.DurableWorkoutDrafts
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.testutil.TestWorkoutFixture
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.workout.FinishOutcome
import com.sinura.personaltrainer.workout.WorkoutDraftPersistence
import com.sinura.personaltrainer.workout.WorkoutDraftRestore
import com.sinura.personaltrainer.workout.WorkoutNumberDraft
import com.sinura.personaltrainer.workout.WorkoutNumberField
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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

/** Couples the real controller, Room writes and private atomic files; no SavedState replay. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WorkoutPrivateRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var fixture: TestWorkoutFixture
    private lateinit var store: DurableDraftStore
    private val models = mutableListOf<ActiveWorkoutViewModel>()
    private val files = FaultFiles()

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        fixture = seedTestWorkout(deps, targetSets = 12)
        deps.workoutRepository.updateSessionNotes(fixture.session.id, "Existing notes")
        store = DurableDraftStore(directory = temporary.newFolder("private"), files = files)
        install()
    }
    @After fun tearDown() {
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }
    private fun service() = DurableWorkoutDrafts(store = store, repository = deps.workoutRepository, maintenance = deps.dbMaintenance, restoreOpen = { false })
    private fun install(port: WorkoutDraftPersistence = service()) { deps.workoutDraftCache.persistence = port }
    private fun active(handle: SavedStateHandle = SavedStateHandle(mapOf("sessionId" to fixture.session.id))) = ActiveWorkoutViewModel(
        ApplicationProvider.getApplicationContext(), handle, deps,
    ).also(models::add)
    private suspend fun ready(vm: ActiveWorkoutViewModel) {
        vm.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READY }
        vm.uiState.awaitFirst { it.canLog && it.notes == "Existing notes" }
    }
    private suspend fun forget(vm: ActiveWorkoutViewModel) {
        vm.clearAndJoinForTest(); models.remove(vm)
        deps.workoutDraftCache.clear(fixture.session.id)
        install()
    }

    @Test fun closeAndFreshOpenRestoreAuthoredValuesAndBothPartialKeypads() = runBlocking {
        val first = active(); ready(first)
        first.setWeight(41.5); first.setReps(13); first.setRpe(8)
        first.numberEntry.put(WorkoutNumberDraft(fixture.exercise.id, WorkoutNumberField.WEIGHT, "82.", WeightUnit.LBS, 3, 3, false))
        first.numberEntry.put(WorkoutNumberDraft(fixture.exercise.id, WorkoutNumberField.REPS, "", null, 0, 0, false))
        first.requestNotesExit()
        first.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        forget(first)
        val reopened = active(); ready(reopened)
        assertTrue(reopened.privateRecovery.value.resumed)
        val raw = reopened.uiState.value.draft
        assertEquals(41.5, raw.weightKg, 0.0); assertEquals(13, raw.reps); assertEquals(8, raw.rpe)
        assertEquals(listOf("82.", ""), reopened.numberEntry.entries.value.map { it.text })
        assertEquals(WeightUnit.LBS, reopened.numberEntry.entries.value.first().weightUnit)
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
    }

    @Test fun failedCheckpointBlocksNewLogAndFailedCloseUntilLatestEntryIsKept() = runBlocking {
        val vm = active(); ready(vm)
        files.failEntry = true
        vm.setWeight(42.0); vm.setRpe(9)
        vm.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.PRESERVATION_FAILED }
        vm.logWorkingSet()
        assertFalse(vm.uiState.value.save.pending)
        vm.requestNotesExit()
        vm.notesExitBlocked.awaitFirst { it }
        assertNull(vm.exitRequested.value)
        vm.keepEditingNotes()
        files.failEntry = false
        vm.setReps(11)
        vm.requestNotesExit()
        vm.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        forget(vm)
        val reopened = active(); ready(reopened)
        assertEquals(42.0, reopened.uiState.value.draft.weightKg, 0.0)
        assertEquals(11, reopened.uiState.value.draft.reps)
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
    }

    @Test fun closeDuringRestoreWaitsAndDoesNotClearUnhydratedNotes() = runBlocking {
        val barrier = CompletableDeferred<Unit>()
        val real = service()
        install(object : WorkoutDraftPersistence by real {
            override fun forEntry(): WorkoutDraftPersistence = this
            override suspend fun open(sessionId: String): WorkoutDraftRestore { barrier.await(); return real.open(sessionId) }
        })
        val vm = active()
        vm.requestNotesExit()
        assertNull(vm.exitRequested.value)
        barrier.complete(Unit)
        vm.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        assertEquals("Existing notes", deps.workoutRepository.getSession(fixture.session.id)!!.notes)
    }

    @Test fun unavailableCheckpointCanCloseWithoutRewritingStoredData() = runBlocking {
        files.failRead = true
        val vm = active()
        vm.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READ_FAILED }
        vm.setWeight(999.0); vm.logWorkingSet(); vm.requestNotesExit()
        vm.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        assertEquals("Existing notes", deps.workoutRepository.getSession(fixture.session.id)!!.notes)
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
    }

    @Test fun duplicateLogDuringAdmissionWritesExactlyOneIntendedSet() = runBlocking {
        val barrier = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val real = service()
        install(object : WorkoutDraftPersistence by real {
            override fun forEntry(): WorkoutDraftPersistence = this
            override suspend fun <T> writeSet(command: WorkoutSetSave, write: suspend () -> T, acknowledge: suspend () -> T): T {
                entered.complete(Unit); barrier.await(); return real.writeSet(command, write, acknowledge)
            }
        })
        val vm = active(); ready(vm)
        vm.setWeight(43.5); vm.setReps(12); vm.setRpe(8)
        vm.logWorkingSet(); entered.await()
        repeat(8) { vm.logWorkingSet() }
        vm.setWeight(900.0)
        barrier.complete(Unit)
        vm.uiState.awaitFirst { !it.logging && !it.save.pending && it.session?.sets?.size == 1 }
        val row = deps.workoutRepository.getSession(fixture.session.id)!!.sets.single()
        assertEquals(43.5, row.weightKg, 0.0); assertEquals(12, row.reps); assertEquals(8, row.rpe)
    }

    @Test fun acceptedWriteWithLostAcknowledgementReopensWithoutReplay() = runBlocking {
        val real = service()
        install(object : WorkoutDraftPersistence by real {
            override fun forEntry(): WorkoutDraftPersistence = this
            override suspend fun <T> writeSet(command: WorkoutSetSave, write: suspend () -> T, acknowledge: suspend () -> T): T =
                real.writeSet(command, { write(); throw IOException("Injected lost acknowledgement") }, acknowledge)
        })
        val vm = active(); ready(vm)
        vm.setWeight(44.5); vm.setReps(14); vm.setRpe(7)
        vm.logWorkingSet()
        vm.uiState.awaitFirst { it.save.pending && !it.logging }
        val original = deps.workoutRepository.getSession(fixture.session.id)!!.sets.single()
        forget(vm)
        val reopened = active()
        reopened.uiState.awaitFirst { !it.save.pending && !it.logging && it.session?.sets?.size == 1 }
        assertEquals(original, deps.workoutRepository.getSession(fixture.session.id)!!.sets.single())
    }

    @Test fun homeFinishRefusesFrozenUncommittedSetAfterProcessRestart() = runBlocking {
        val real = service()
        val vm = active(); ready(vm)
        vm.setRpe(8); vm.logWorkingSet()
        vm.uiState.awaitFirst { !it.logging && !it.save.pending && it.session?.sets?.size == 1 }
        vm.requestNotesExit(); vm.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        forget(vm)
        assertTrue(real.open(fixture.session.id) is WorkoutDraftRestore.ReadyEntry)
        val next = WorkoutSetSave(fixture.session.id, fixture.exercise.id, "reserved-next", fixture.session.startedAt + 99,
            com.sinura.personaltrainer.domain.WorkoutSetValues(50.0, 9, 8, false, null))
        try { real.writeSet(next, { throw IOException("Injected before business commit") }, { error("No saved row") }) } catch (_: IOException) { }
        install(service())
        assertTrue(deps.finishWorkout(fixture.session.id) is FinishOutcome.Failed)
        assertFalse(deps.workoutRepository.getSession(fixture.session.id)!!.isFinished)
        assertEquals(1, deps.workoutRepository.getSession(fixture.session.id)!!.sets.size)
    }

    @Test fun intentionalNotesClearSurvivesFreshOpenWithoutRestoringOldNotes() = runBlocking {
        val vm = active(); ready(vm)
        vm.setNotes("")
        vm.requestNotesExit()
        vm.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        assertEquals("", deps.workoutRepository.getSession(fixture.session.id)!!.notes)
        forget(vm)
        val reopened = active()
        reopened.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READY }
        reopened.uiState.awaitFirst { it.canLog && it.notes == "" }
        assertEquals("", deps.workoutRepository.getSession(fixture.session.id)!!.notes)
    }

    @Test fun changedSavedSetCanDiscardOnlyItsUnfinishedCorrection() = runBlocking {
        val vm = active(); ready(vm)
        vm.setRpe(8); vm.logWorkingSet()
        val logged = vm.uiState.awaitFirst { !it.logging && !it.save.pending && it.session?.sets?.size == 1 }.session!!.sets.single()
        vm.editSet(logged.id)
        vm.uiState.awaitFirst { it.editingSetId == logged.id && !it.mutating }
        vm.setWeight(51.0)
        vm.requestNotesExit(); vm.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        forget(vm)
        deps.workoutRepository.updateSet(logged.id, 52.0, logged.reps, 9, false)
        val reopened = active()
        reopened.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.CONFLICT }
        reopened.discardConflictingEntry()
        reopened.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READY }
        reopened.uiState.awaitFirst { it.canLog && it.editingSetId == null }
        val saved = deps.workoutRepository.getSession(fixture.session.id)!!.sets.single()
        assertEquals(logged.id, saved.id); assertEquals(52.0, saved.weightKg, 0.0); assertEquals(9, saved.rpe)
    }

    @Test fun acceptedSwapRetiresOnlyTheReplacedLiftsPrivateFields() = runBlocking {
        val vm = active(); ready(vm)
        vm.numberEntry.put(WorkoutNumberDraft(fixture.exercise.id, WorkoutNumberField.WEIGHT, "82.", WeightUnit.KG, 3, 3, false))
        val replacement = insertTestExercise(deps, "replacement", "Replacement row")
        vm.requestSwap(); vm.addExercise(replacement)
        vm.uiState.awaitFirst { it.selectedExerciseId == replacement.id && !it.mutating && it.session?.exercises?.singleOrNull()?.exercise?.id == replacement.id }
        vm.setWeight(42.5); vm.setReps(12); vm.setRpe(8)
        vm.requestNotesExit(); vm.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        forget(vm)
        val reopened = active(); ready(reopened)
        reopened.uiState.awaitFirst {
            it.selectedExerciseId == replacement.id && it.draft.weightKg == 42.5 && it.draft.reps == 12
        }
        assertEquals(replacement.id, reopened.uiState.value.selectedExerciseId)
        assertEquals(42.5, reopened.uiState.value.draft.weightKg, 0.0)
        assertTrue(reopened.numberEntry.entries.value.isEmpty())
        reopened.logWorkingSet()
        val saved = reopened.uiState.awaitFirst { !it.logging && !it.save.pending && it.session?.sets?.size == 1 }.session!!.sets.single()
        assertEquals(replacement.id, saved.exerciseId); assertEquals(42.5, saved.weightKg, 0.0); assertEquals(12, saved.reps)
    }

    @Test fun completedCheckpointRestoresTimedDeleteUndoAfterFreshOpen() = runBlocking {
        val vm = active(); ready(vm)
        val command = WorkoutSetSave(fixture.session.id, fixture.exercise.id, "timed-undo-record", fixture.session.startedAt + 1234,
            com.sinura.personaltrainer.domain.WorkoutSetValues(46.5, 8, 9, false, 23))
        deps.workoutRepository.saveSet(command)
        val original = vm.uiState.awaitFirst { it.session?.sets?.size == 1 }.session!!.sets.single()
        vm.deleteSet(original.id)
        vm.uiState.awaitFirst { !it.mutating && it.session?.sets?.isEmpty() == true && vm.undoEntries.value.isNotEmpty() }
        vm.requestNotesExit(); vm.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        forget(vm)
        val reopened = active(); ready(reopened)
        assertEquals(1, reopened.undoEntries.value.size)
        reopened.undoDeleteSet()
        val restored = reopened.uiState.awaitFirst { !it.mutating && it.session?.sets?.size == 1 }.session!!.sets.single()
        assertEquals(original, restored)
        assertEquals(23, restored.durationSeconds)
        assertTrue(reopened.undoEntries.value.isEmpty())
    }

    @Test fun undoCannotOverwriteANewerResultWithTheSameIdentity() = runBlocking {
        val vm = active(); ready(vm)
        vm.setRpe(8); vm.logWorkingSet()
        val original = vm.uiState.awaitFirst { !it.logging && !it.save.pending && it.session?.sets?.size == 1 }.session!!.sets.single()
        vm.deleteSet(original.id)
        vm.uiState.awaitFirst { !it.mutating && it.session?.sets?.isEmpty() == true && vm.undoEntries.value.isNotEmpty() }
        deps.workoutRepository.saveSet(WorkoutSetSave(original.sessionId, original.exerciseId, original.id, original.completedAt,
            com.sinura.personaltrainer.domain.WorkoutSetValues(99.0, 15, 9, false, 31)))
        vm.uiState.awaitFirst { it.session?.sets?.singleOrNull()?.weightKg == 99.0 }
        vm.undoDeleteSet()
        vm.uiState.awaitFirst { !it.mutating && it.error != null }
        val current = deps.workoutRepository.getSession(original.sessionId)!!.sets.single()
        assertEquals(99.0, current.weightKg, 0.0); assertEquals(15, current.reps); assertEquals(31, current.durationSeconds)
        assertEquals(1, vm.undoEntries.value.size)
    }

    @Test fun removedLiftCannotLeaveOrphanedKeypadOrClockInReopenedEntry() = runBlocking {
        val vm = active(); ready(vm)
        vm.numberEntry.put(WorkoutNumberDraft(fixture.exercise.id, WorkoutNumberField.WEIGHT, "82.", WeightUnit.KG, 3, 3, false))
        vm.startSetStopwatch()
        vm.setStopwatch.awaitFirst { it.running }
        vm.removeSelectedLift()
        vm.uiState.awaitFirst { !it.mutating && it.session?.exercises?.isEmpty() == true }
        vm.requestNotesExit(); vm.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        forget(vm)
        val reopened = active()
        reopened.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READY }
        assertTrue(reopened.numberEntry.entries.value.isEmpty())
        assertFalse(reopened.setStopwatch.value.running)
        assertEquals(1, reopened.undoEntries.value.size)
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.exercises.isEmpty())
    }

    @Test fun releasedRemovedCommandKeepsSurvivorValuesAndCanCloseAndReopen() = runBlocking {
        checkReleasedRemovedCommand(keepSurvivor = true)
    }

    @Test fun releasedLastRemovedCommandCanCloseAndReopenWithoutOrphanOwners() = runBlocking {
        checkReleasedRemovedCommand(keepSurvivor = false)
    }

    private suspend fun checkReleasedRemovedCommand(keepSurvivor: Boolean) {
        val handle = SavedStateHandle(mapOf("sessionId" to fixture.session.id))
        val survivor = if (keepSurvivor) insertTestExercise(deps, "survivor", "Surviving row").also {
            deps.workoutRepository.addExerciseToSession(fixture.session.id, it, 3, 8, 40.0, 90)
            com.sinura.personaltrainer.workout.SavedStateWorkoutDraft(handle).write(
                com.sinura.personaltrainer.workout.WorkoutDraft(fixture.session.id, it.id, 44.5, 11, null, false,
                    "Existing notes", dirty = true), null)
        } else null
        val command = WorkoutSetSave(fixture.session.id, fixture.exercise.id, "retired-command", fixture.session.startedAt,
            com.sinura.personaltrainer.domain.WorkoutSetValues(99.0, 19, 9, false, 23))
        com.sinura.personaltrainer.workout.SavedStateWorkoutSave(handle).write(command)
        deps.workoutRepository.removeExerciseFromSession(fixture.session.id, fixture.session.exercises.single().id)
        val vm = active(handle)
        vm.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.REVIEW_SAVE && it.enabled }
        vm.editFailedSave()
        vm.uiState.awaitFirst { !it.save.pending && !it.logging && it.selectedExerciseId != command.exerciseId }
        vm.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READY }
        if (survivor != null) {
            val entry = vm.uiState.awaitFirst { it.canLog && it.selectedExerciseId == survivor.id }
            assertEquals(44.5, entry.draft.weightKg, 0.0); assertEquals(11, entry.draft.reps)
            assertNull(entry.draft.rpe)
            vm.setRpe(8)
            vm.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.LOG_SET && it.enabled }
        } else vm.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.ADD_EXERCISE && it.enabled }
        assertFalse(deps.workoutDraftCache.all(fixture.session.id).containsKey(command.exerciseId))
        assertNull(com.sinura.personaltrainer.workout.SavedStateWorkoutDraft(handle).readLift(fixture.session.id, command.exerciseId))
        vm.requestNotesExit(); vm.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        forget(vm)
        val reopened = active()
        reopened.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READY }
        val restored = reopened.uiState.awaitFirst { it.loadState == SessionLoadState.FOUND && it.notes == "Existing notes" }
        assertEquals(survivor?.id, restored.selectedExerciseId)
        assertTrue(reopened.numberEntry.entries.value.isEmpty()); assertFalse(reopened.setStopwatch.value.running)
        assertFalse(deps.workoutDraftCache.all(fixture.session.id).containsKey(command.exerciseId))
        if (survivor != null) {
            val entry = reopened.uiState.awaitFirst { it.canLog }
            assertEquals(44.5, entry.draft.weightKg, 0.0); assertEquals(11, entry.draft.reps); assertEquals(8, entry.draft.rpe)
        }
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
    }

    @Test fun changedRawOwnerRequiresExplicitDiscardAndPreservesSavedSurvivor() = runBlocking {
        val survivor = insertTestExercise(deps, "saved-survivor", "Saved survivor")
        deps.workoutRepository.addExerciseToSession(fixture.session.id, survivor, 3, 8, 40.0, 90)
        deps.workoutRepository.saveSet(WorkoutSetSave(fixture.session.id, survivor.id, "saved-survivor-set", fixture.session.startedAt + 5,
            com.sinura.personaltrainer.domain.WorkoutSetValues(42.5, 9, 8, false, 33)))
        val original = deps.workoutRepository.getSession(fixture.session.id)!!.sets.single()
        val first = active(); ready(first)
        first.selectExercise(fixture.exercise.id)
        first.uiState.awaitFirst { it.canLog && it.selectedExerciseId == fixture.exercise.id }
        first.setWeight(91.5)
        first.numberEntry.put(WorkoutNumberDraft(fixture.exercise.id, WorkoutNumberField.WEIGHT, "82.", WeightUnit.LBS, 3, 3, false))
        first.requestNotesExit(); first.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        assertTrue(deps.workoutDraftCache.all(fixture.session.id).containsKey(fixture.exercise.id))
        assertEquals(fixture.exercise.id, first.numberEntry.entries.value.single().exerciseId)
        forget(first)
        deps.workoutRepository.removeExerciseFromSession(fixture.session.id, fixture.session.exercises.single().id)
        val reopened = active()
        reopened.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.CONFLICT }
        val conflicted = reopened.uiState.awaitFirst { it.loadState == SessionLoadState.FOUND }
        assertFalse(conflicted.canLog)
        assertEquals(listOf(original), deps.workoutRepository.getSession(fixture.session.id)!!.sets)
        reopened.discardConflictingEntry()
        reopened.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READY }
        val ready = reopened.uiState.awaitFirst { it.canLog && it.selectedExerciseId == survivor.id && it.notes == "Existing notes" }
        assertFalse(ready.save.pending); assertTrue(reopened.numberEntry.entries.value.isEmpty())
        assertEquals(listOf(original), deps.workoutRepository.getSession(fixture.session.id)!!.sets)
        assertEquals(listOf(survivor.id), deps.workoutRepository.getSession(fixture.session.id)!!.exercises.map { it.exercise.id })
        reopened.requestNotesExit(); reopened.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        forget(reopened)
        val final = active()
        final.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READY }
        final.uiState.awaitFirst { it.canLog && it.selectedExerciseId == survivor.id }
        assertEquals(listOf(original), deps.workoutRepository.getSession(fixture.session.id)!!.sets)
    }

    @Test fun revokedReleaseCannotClearTheNewEditorsPendingCommandOrEntry() = runBlocking {
        val released = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val root = service()
        var entryNumber = 0
        install(object : WorkoutDraftPersistence by root {
            override fun forEntry(): WorkoutDraftPersistence {
                val child = root.forEntry()
                val holdRelease = entryNumber++ == 0
                return object : WorkoutDraftPersistence by child {
                    override suspend fun releaseSet(command: WorkoutSetSave) {
                        child.releaseSet(command)
                        if (holdRelease) { released.complete(Unit); resume.await() }
                    }
                }
            }
        })
        val survivor = insertTestExercise(deps, "new-owner-survivor", "New owner survivor")
        deps.workoutRepository.addExerciseToSession(fixture.session.id, survivor, 3, 8, 40.0, 90)
        val command = WorkoutSetSave(fixture.session.id, fixture.exercise.id, "held-release", fixture.session.startedAt,
            com.sinura.personaltrainer.domain.WorkoutSetValues(99.0, 19, 9, false, 23))
        val handle = SavedStateHandle(mapOf("sessionId" to fixture.session.id))
        com.sinura.personaltrainer.workout.SavedStateWorkoutSave(handle).write(command)
        deps.workoutRepository.removeExerciseFromSession(fixture.session.id, fixture.session.exercises.single().id)
        val older = active(handle)
        older.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.REVIEW_SAVE && it.enabled }
        older.editFailedSave()
        withTimeout(5_000) { released.await() }
        val newer = active()
        newer.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.REVIEW_SAVE && it.enabled }
        val staged = deps.workoutDraftCache.all(fixture.session.id)
        assertEquals(command, deps.workoutDraftCache.pendingSave(fixture.session.id))
        resume.complete(Unit)
        older.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.CONFLICT }
        older.uiState.awaitFirst { !it.logging }
        assertEquals(command, deps.workoutDraftCache.pendingSave(fixture.session.id))
        assertEquals(staged, deps.workoutDraftCache.all(fixture.session.id))
        assertEquals(command, newer.uiState.value.save.command)
        newer.editFailedSave()
        newer.uiState.awaitFirst { !it.save.pending && it.canLog && it.selectedExerciseId == survivor.id }
        newer.setWeight(44.5); newer.setReps(11); newer.setRpe(8); newer.setNotes("New owner notes")
        newer.numberEntry.put(WorkoutNumberDraft(survivor.id, WorkoutNumberField.WEIGHT, "88.", WeightUnit.LBS, 3, 3, false))
        newer.requestNotesExit(); newer.exitRequested.awaitFirst { it == WorkoutExit.Kept }
        forget(newer)
        val reopened = active()
        reopened.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READY }
        val restored = reopened.uiState.awaitFirst { it.canLog && it.notes == "New owner notes" }
        assertEquals(44.5, restored.draft.weightKg, 0.0); assertEquals(11, restored.draft.reps); assertEquals(8, restored.draft.rpe)
        assertEquals("88.", reopened.numberEntry.entries.value.single().text)
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
    }

    private class FaultFiles : DraftFileIO {
        @Volatile var failEntry = false
        @Volatile var failRead = false
        override fun read(file: File): ByteArray? { if (failRead) throw IOException(); return AtomicDraftFiles.read(file) }
        override fun replace(file: File, bytes: ByteArray) {
            if (failEntry && bytes.toString(Charsets.UTF_8).contains("workout-entry")) throw IOException()
            AtomicDraftFiles.replace(file, bytes)
        }
    }
}
