package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.domain.LogCommitFeedback
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.testutil.ControllableTimePort
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.workout.SavedStateWorkoutSave
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WorkoutSaveRecoveryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = ControllableTimePort()
    private lateinit var deps: FakeAppDependencies
    private val models = mutableListOf<ActiveWorkoutViewModel>()
    private var failWrites = false
    private var failInspection = false
    private var insertGate: CompletableDeferred<Unit>? = null
    private var recordGate: CompletableDeferred<Unit>? = null
    private var removeGate: CompletableDeferred<Unit>? = null
    private var restoreGate: CompletableDeferred<Unit>? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher, time = clock,
            workoutDaoDecorator = { real -> object : WorkoutDao by real {
                override suspend fun deleteSessionExercise(id: String) {
                    removeGate?.await()
                    real.deleteSessionExercise(id)
                }

                override suspend fun upsertSessionExercise(item: SessionExerciseEntity) {
                    restoreGate?.await()
                    real.upsertSessionExercise(item)
                }

                override suspend fun insertSet(value: SetLogEntity) {
                    insertGate?.await()
                    check(!failWrites) { "Injected write failure" }
                    real.insertSet(value)
                }

                override suspend fun getSet(id: String): SetLogEntity? {
                    check(!failInspection) { "Injected outcome read failure" }
                    return real.getSet(id)
                }

                override suspend fun recordPriorsBefore(
                    exerciseId: String, sessionId: String, weightKg: Double, completedAt: Long, setNumber: Int,
                ): com.sinura.personaltrainer.data.local.entity.ExerciseRecordPriorsRow {
                    recordGate?.await()
                    return real.recordPriorsBefore(exerciseId, sessionId, weightKg, completedAt, setNumber)
                }
            } },
        )
    }

    @After
    fun tearDown() {
        insertGate?.complete(Unit)
        recordGate?.complete(Unit)
        removeGate?.complete(Unit)
        restoreGate?.complete(Unit)
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun failedTimedSaveRetriesFrozenValuesAndTimeOnceDespiteDraftCallbacks() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val handle = handle(fixture.session.id)
        val vm = active(handle)
        vm.uiState.awaitFirst { it.canLog }
        vm.setWeight(72.5)
        vm.setReps(8)
        vm.setRpe(9)
        vm.startSetStopwatch()
        clock.advance(12_000)
        dispatcher.scheduler.advanceTimeBy(250)
        dispatcher.scheduler.runCurrent()
        failWrites = true
        vm.logSet()
        val failed = vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.FAILED && !it.logging }
        val command = checkNotNull(failed.save.command)
        assertEquals(12, command.values.durationSeconds)
        assertEquals(command, SavedStateWorkoutSave(handle).read(fixture.session.id))
        vm.setWeight(999.0)
        vm.setReps(99)
        vm.setRpe(6)
        vm.setWarmup(true)
        vm.requestExtraSet()
        vm.finishWorkout()
        vm.discardWorkout()
        vm.logSet()
        assertEquals(command, deps.workoutDraftCache.pendingSave(fixture.session.id))
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
        clock.advance(40_000)
        failWrites = false
        vm.retrySave()
        vm.retrySave()
        vm.uiState.awaitFirst { !it.save.pending && !it.logging && it.session?.sets?.size == 1 }
        val row = deps.workoutRepository.getSession(fixture.session.id)!!.sets.single()
        assertEquals(command.setId, row.id)
        assertEquals(command.completedAt, row.completedAt)
        assertEquals(command.values, WorkoutSetValues.from(row))
        assertNull(SavedStateWorkoutSave(handle).read(fixture.session.id))
        assertNull(deps.workoutDraftCache.pendingSave(fixture.session.id))
    }

    @Test
    fun slowSaveLocksOwnerAndRepeatedTapsProduceOneSet() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = active(handle(fixture.session.id))
        vm.uiState.awaitFirst { it.canLog }
        insertGate = CompletableDeferred()
        vm.logSet()
        val busy = vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.SAVING }
        vm.logSet()
        vm.retrySave()
        vm.selectExercise("another")
        vm.setWeight(10.0)
        assertTrue(busy.entryLocked)
        assertFalse(busy.canFinish)
        insertGate!!.complete(Unit)
        val saved = vm.uiState.awaitFirst { !it.logging && it.session?.sets?.size == 1 }
        assertEquals(fixture.exercise.id, saved.selectedExerciseId)
        assertEquals(100.0, saved.session!!.sets.single().weightKg, 0.0)
    }

    @Test
    fun restoredAbsentCommandOffersRetryWithoutWritingAutomatically() = runBlocking {
        val command = pendingCommand()
        val handle = handle(command.sessionId)
        SavedStateWorkoutSave(handle).write(command)
        val vm = active(handle)
        val restored = vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.FAILED }
        assertEquals(61.5, restored.draft.weightKg, 0.0)
        assertEquals(7, restored.draft.reps)
        assertTrue(deps.workoutRepository.getSession(command.sessionId)!!.sets.isEmpty())
        vm.retrySave()
        vm.uiState.awaitFirst { !it.save.pending && it.session?.sets?.size == 1 }
        assertEquals(command.setId, deps.workoutRepository.getSession(command.sessionId)!!.sets.single().id)
    }

    @Test
    fun committedBeforeAcknowledgementReconcilesWithoutRestOrCelebration() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val handle = handle(fixture.session.id)
        val original = active(handle)
        original.uiState.awaitFirst { it.canLog }
        recordGate = CompletableDeferred()
        original.logSet()
        original.uiState.awaitFirst { it.logging && it.session?.sets?.size == 1 }
        val command = checkNotNull(SavedStateWorkoutSave(handle).read(fixture.session.id))
        original.removeSelectedLift()
        assertEquals(1, deps.workoutRepository.getSession(fixture.session.id)!!.exercises.size)
        assertEquals(command, SavedStateWorkoutSave(handle).read(fixture.session.id))
        assertNull(original.uiState.value.error)
        original.clearAndJoinForTest()
        models.remove(original)
        deps.workoutDraftCache.clear(fixture.session.id)
        val restored = active(handle)
        val feedback = mutableListOf<LogCommitFeedback>()
        val collection = launch(dispatcher) { restored.logFeedback.collect { feedback.add(it) } }
        try {
            restored.uiState.awaitFirst { it.loadState == SessionLoadState.FOUND && !it.save.pending }
            assertEquals(listOf(command.setId), deps.workoutRepository.getSession(fixture.session.id)!!.sets.map { it.id })
            assertFalse(deps.restTimerStore.current().running)
            assertNull(restored.logReceipt.value)
            assertNull(restored.personalRecord.value)
            assertTrue(feedback.isEmpty())
            assertNull(SavedStateWorkoutSave(handle).read(fixture.session.id))
        } finally { collection.cancel() }
    }

    @Test
    fun unavailableOutcomeKeepsIdentityAndCannotBeReleasedOrReplayed() = runBlocking {
        val command = pendingCommand()
        val handle = handle(command.sessionId)
        SavedStateWorkoutSave(handle).write(command)
        failInspection = true
        val vm = active(handle)
        vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.FAILED }
        vm.editFailedSave()
        vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.FAILED && !it.logging }
        assertEquals(command, SavedStateWorkoutSave(handle).read(command.sessionId))
        assertTrue(deps.workoutRepository.getSession(command.sessionId)!!.sets.isEmpty())
        failInspection = false
        vm.editFailedSave()
        vm.uiState.awaitFirst { !it.save.pending && !it.logging }
        vm.setWeight(80.0)
        assertEquals(80.0, vm.uiState.awaitFirst { it.draft.weightKg == 80.0 }.draft.weightKg, 0.0)
        assertTrue(deps.workoutRepository.getSession(command.sessionId)!!.sets.isEmpty())
    }

    @Test
    fun deletedEditRestoresAsConflictAndNeverCreatesANewSet() = runBlocking {
        val command = pendingCommand()
        deps.workoutRepository.saveSet(command)
        val edit = command.copy(original = command.values, values = command.values.copy(weightKg = 65.0))
        deps.workoutRepository.deleteSet(command.setId)
        val handle = handle(command.sessionId)
        SavedStateWorkoutSave(handle).write(edit)
        val vm = active(handle)
        vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.CONFLICT }
        vm.retrySave()
        assertTrue(deps.workoutRepository.getSession(command.sessionId)!!.sets.isEmpty())
        assertNotNull(SavedStateWorkoutSave(handle).read(command.sessionId))
        vm.editFailedSave()
        val released = vm.uiState.awaitFirst { !it.save.pending && !it.logging && it.editingSetId == null }
        assertNull(released.editingSetId)
        assertTrue(deps.workoutRepository.getSession(command.sessionId)!!.sets.isEmpty())
    }

    @Test
    fun recreatedEditorCannotOverwriteACorrectionMadeSinceEditingBegan() = runBlocking {
        val command = pendingCommand()
        deps.workoutRepository.saveSet(command)
        val handle = handle(command.sessionId)
        val first = active(handle)
        first.uiState.awaitFirst { it.canLog && it.session?.sets?.size == 1 }
        first.editSet(command.setId)
        // Opening an edit reads the stored row, so the typed weight must follow the open
        // rather than race it; otherwise the row's own values land on top of it.
        first.uiState.awaitFirst { it.editingSetId == command.setId }
        first.setWeight(65.0)
        first.clearAndJoinForTest()
        models.remove(first)
        deps.workoutDraftCache.clear(command.sessionId)
        deps.workoutRepository.updateSet(command.setId, 80.0, 7, rpe = 8, isWarmup = false)
        val restored = active(handle)
        restored.uiState.awaitFirst { it.canLog && it.editingSetId == command.setId }
        restored.logSet()
        restored.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.CONFLICT }
        assertEquals(80.0, deps.workoutRepository.getSession(command.sessionId)!!.sets.single().weightKg, 0.0)
    }

    @Test
    fun removalStartedBeforeLogKeepsEntryLockedUntilSelectionSettles() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = active(handle(fixture.session.id))
        vm.uiState.awaitFirst { it.canLog }
        removeGate = CompletableDeferred()
        vm.removeSelectedLift()
        vm.uiState.awaitFirst { it.mutating && it.entryLocked }
        vm.logSet()
        assertNull(deps.workoutDraftCache.pendingSave(fixture.session.id))
        removeGate!!.complete(Unit)
        vm.uiState.awaitFirst { !it.mutating && it.session?.exercises?.isEmpty() == true }
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
    }

    @Test
    fun undoStartedBeforeLogCannotChangeAnOutstandingSaveOwner() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = active(handle(fixture.session.id))
        vm.uiState.awaitFirst { it.canLog }
        vm.removeSelectedLift()
        vm.uiState.awaitFirst { !it.mutating && it.session?.exercises?.isEmpty() == true }
        val other = insertTestExercise(deps, id = "row", name = "Row")
        deps.workoutRepository.addExerciseToSession(
            sessionId = fixture.session.id, exercise = other, targetSets = 3, targetReps = 8,
            targetWeightKg = 40.0, restSeconds = 90,
        )
        vm.uiState.awaitFirst { it.canLog && it.selectedExerciseId == other.id }
        restoreGate = CompletableDeferred()
        vm.undoRemoveLift()
        vm.uiState.awaitFirst { it.mutating && it.entryLocked }
        vm.logSet()
        assertNull(deps.workoutDraftCache.pendingSave(fixture.session.id))
        restoreGate!!.complete(Unit)
        vm.uiState.awaitFirst { !it.mutating && it.selectedExerciseId == fixture.exercise.id }
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
    }

    @Test
    fun queuedLogCannotBecomeNextAndRapidNextCannotBecomeLog() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 1)
        val other = insertTestExercise(deps, id = "row", name = "Row")
        deps.workoutRepository.addExerciseToSession(
            sessionId = fixture.session.id, exercise = other, targetSets = 1, targetReps = 8,
            targetWeightKg = 40.0, restSeconds = 90,
        )
        val vm = active(handle(fixture.session.id))
        val log = vm.primaryAction.awaitFirst {
            it.enabled && it.kind == WorkoutPrimaryKind.LOG_SET &&
                it.identity.exerciseId == fixture.exercise.id &&
                it.identity.draft.weightKg == 100.0 && it.identity.draft.reps == 5
        }
        assertTrue(vm.performPrimary(log))
        val next = vm.primaryAction.awaitFirst { it.enabled && it.kind == WorkoutPrimaryKind.NEXT_EXERCISE }
        assertFalse(vm.performPrimary(log))
        assertFalse(vm.performPrimary(next))
        clock.advance(android.view.ViewConfiguration.getDoubleTapTimeout().toLong())
        assertTrue(vm.performPrimary(next))
        val secondLog = vm.primaryAction.awaitFirst {
            it.enabled && it.kind == WorkoutPrimaryKind.LOG_SET && it.identity.exerciseId == other.id &&
                it.identity.draft.weightKg == 40.0 && it.identity.draft.reps == 8
        }
        assertFalse(vm.performPrimary(secondLog))
        assertEquals(1, deps.workoutRepository.getSession(fixture.session.id)!!.sets.size)
        clock.advance(android.view.ViewConfiguration.getDoubleTapTimeout().toLong())
        assertTrue(vm.performPrimary(secondLog))
        vm.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.FINISH }
        assertEquals(2, deps.workoutRepository.getSession(fixture.session.id)!!.sets.size)
    }

    @Test
    fun timerTickDoesNotInvalidatePressAndCommitUsesTheDisplayedDuration() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = active(handle(fixture.session.id))
        vm.uiState.awaitFirst { it.canLog }
        vm.startSetStopwatch()
        clock.advance(12_000)
        dispatcher.scheduler.advanceTimeBy(250)
        dispatcher.scheduler.runCurrent()
        val displayed = vm.primaryAction.awaitFirst { it.enabled && it.durationSeconds == 12 }
        clock.advance(5_000)
        dispatcher.scheduler.advanceTimeBy(250)
        dispatcher.scheduler.runCurrent()
        vm.primaryAction.awaitFirst { it.durationSeconds == 17 }
        assertTrue(vm.performPrimary(displayed))
        vm.uiState.awaitFirst { !it.logging && it.session?.sets?.size == 1 }
        assertEquals(12, deps.workoutRepository.getSession(fixture.session.id)!!.sets.single().durationSeconds)
    }

    @Test
    fun editingIdentitySurvivesSavedStateThenCacheThenSavedStateAgain() = runBlocking {
        val command = pendingCommand()
        deps.workoutRepository.saveSet(command)
        val firstHandle = handle(command.sessionId)
        val first = active(firstHandle)
        first.uiState.awaitFirst { it.canLog && it.session?.sets?.size == 1 }
        first.editSet(command.setId)
        // Opening an edit reads the stored row, so the typed weight must follow the open
        // rather than race it; otherwise the row's own values land on top of it.
        first.uiState.awaitFirst { it.editingSetId == command.setId }
        first.setWeight(65.0)
        first.clearAndJoinForTest()
        models.remove(first)
        deps.workoutDraftCache.clear(command.sessionId)
        val restoredFromState = active(firstHandle)
        restoredFromState.uiState.awaitFirst { it.canLog && it.editingSetId == command.setId }
        restoredFromState.clearAndJoinForTest()
        models.remove(restoredFromState)
        val freshHandle = handle(command.sessionId)
        val restoredFromCache = active(freshHandle)
        restoredFromCache.uiState.awaitFirst { it.canLog && it.editingSetId == command.setId }
        restoredFromCache.clearAndJoinForTest()
        models.remove(restoredFromCache)
        deps.workoutDraftCache.clear(command.sessionId)
        val final = active(freshHandle)
        final.uiState.awaitFirst { it.canLog && it.editingSetId == command.setId && it.draft.weightKg == 65.0 }
        final.logSet()
        final.uiState.awaitFirst { !it.logging && it.editingSetId == null && !it.save.pending }
        val row = deps.workoutRepository.getSession(command.sessionId)!!.sets.single()
        assertEquals(command.setId, row.id)
        assertEquals(65.0, row.weightKg, 0.0)
    }

    @Test
    fun extraSetIntentSurvivesRecreationAtCompletedTarget() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 1)
        val handle = handle(fixture.session.id)
        val first = active(handle)
        first.uiState.awaitFirst { it.canLog }
        first.logSet()
        first.uiState.awaitFirst { !it.logging && it.session?.sets?.size == 1 }
        first.requestExtraSet()
        assertTrue(first.extraSetRequested.value)
        first.clearAndJoinForTest()
        models.remove(first)
        deps.workoutDraftCache.clear(fixture.session.id)
        val restored = active(handle)
        restored.uiState.awaitFirst { it.loadState == SessionLoadState.FOUND }
        assertTrue(restored.extraSetRequested.value)
    }

    private suspend fun pendingCommand(): WorkoutSetSave {
        val fixture = seedTestWorkout(deps)
        return WorkoutSetSave(
            sessionId = fixture.session.id, exerciseId = fixture.exercise.id, setId = "pending",
            completedAt = 1_700_000_000_000,
            values = WorkoutSetValues(61.5, 7, 8, false, 13),
        )
    }

    @Test
    fun removedPendingOwnerKeepsReviewActionThenReturnsToRemainingExercise() = runBlocking {
        checkRemovedPendingOwner(remainingExercise = true)
    }

    @Test
    fun removedLastPendingOwnerKeepsReviewActionThenReturnsToAddExercise() = runBlocking {
        checkRemovedPendingOwner(remainingExercise = false)
    }

    private suspend fun checkRemovedPendingOwner(remainingExercise: Boolean) {
        val command = pendingCommand()
        if (remainingExercise) {
            val other = insertTestExercise(deps, id = "remaining", name = "Remaining exercise")
            deps.workoutRepository.addExerciseToSession(command.sessionId, other, 3, 8, 40.0, 90)
        }
        val handle = handle(command.sessionId)
        SavedStateWorkoutSave(handle).write(command)
        val owner = deps.workoutRepository.getSession(command.sessionId)!!.exercises
            .first { it.exercise.id == command.exerciseId }
        deps.workoutRepository.removeExerciseFromSession(command.sessionId, owner.id)
        val vm = active(handle)
        val review = vm.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.REVIEW_SAVE && it.enabled }
        assertEquals(command.exerciseId, review.identity.exerciseId)
        assertEquals(command, review.identity.pendingSave)
        assertTrue(vm.performPrimary(review))
        vm.editFailedSave()
        val released = vm.uiState.awaitFirst {
            !it.save.pending && !it.logging && it.selectedExerciseId != command.exerciseId
        }
        if (remainingExercise) {
            assertEquals("remaining", released.selectedExerciseId)
            vm.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.LOG_SET && it.enabled }
        } else {
            assertNull(released.selectedExerciseId)
            vm.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.ADD_EXERCISE && it.enabled }
        }
        assertTrue(deps.workoutRepository.getSession(command.sessionId)!!.sets.isEmpty())
    }

    private fun handle(sessionId: String) = SavedStateHandle(mapOf("sessionId" to sessionId))
    private fun active(handle: SavedStateHandle) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(), savedStateHandle = handle, container = deps,
    ).also(models::add)
}
