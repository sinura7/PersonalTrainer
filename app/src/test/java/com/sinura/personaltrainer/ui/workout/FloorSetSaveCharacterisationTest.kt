package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.LogCommitFeedback
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.testutil.ControllableTimePort
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.workout.SavedStateWorkoutSave
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
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

/**
 * Pins the floor's set save as it stands before audit W2d-2 moves it out of
 * [ActiveWorkoutViewModel]: what each outcome says, what it signals, which copies of the frozen
 * set it keeps or clears, and what Edit releases. [WorkoutSaveRecoveryTest] holds the frozen
 * values, the lock and the recovery paths; these are the details it leaves open. Written and
 * passed on the code before the move; the last Edit case was added after the move's mutation run
 * found that gap, and was run on the old code too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FloorSetSaveCharacterisationTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = ControllableTimePort()
    private lateinit var deps: FakeAppDependencies
    private val models = mutableListOf<ActiveWorkoutViewModel>()
    private val feedback = mutableListOf<LogCommitFeedback>()
    private val collectors = CoroutineScope(dispatcher)

    /** Thrown by the next set insert, when set. */
    private var writeFailure: (() -> Exception)? = null
    private var failInspection = false
    private var insertGate: CompletableDeferred<Unit>? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher, time = clock,
            workoutDaoDecorator = { real ->
                object : WorkoutDao by real {
                    override suspend fun insertSet(value: SetLogEntity) {
                        insertGate?.await()
                        writeFailure?.let { throw it() }
                        real.insertSet(value)
                    }

                    override suspend fun getSet(id: String): SetLogEntity? {
                        check(!failInspection) { "Injected outcome read failure" }
                        return real.getSet(id)
                    }
                }
            },
        )
    }

    @After
    fun tearDown() {
        insertGate?.complete(Unit)
        collectors.cancel()
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aFailedWriteSaysWhyRejectsOnceAndKeepsTheFrozenSetInBothCopies() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val handle = handle(fixture.session.id)
        val vm = active(handle)
        vm.uiState.awaitFirst { it.canLog }
        writeFailure = { IllegalStateException("Injected write failure") }

        vm.logSet()

        val failed = vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.FAILED && !it.logging }
        assertEquals(LogCommitCopy.WRITE_FAILED, failed.save.message)
        assertEquals(listOf(LogCommitFeedback.REJECT), feedback)
        val command = checkNotNull(failed.save.command)
        assertEquals(command, SavedStateWorkoutSave(handle).read(fixture.session.id))
        assertEquals(command, deps.workoutDraftCache.pendingSave(fixture.session.id))
        assertTrue("a failed set keeps the entry locked", failed.entryLocked)
        assertNull(vm.logReceipt.value)
    }

    /** The repository's own field rules speak for themselves; nothing else it throws does. */
    @Test
    fun aWriteRefusedByAFieldRuleShowsThatRule() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = active(handle(fixture.session.id))
        vm.uiState.awaitFirst { it.canLog }
        writeFailure = { IllegalStateException(SetLogRules.INVALID_REPS) }

        vm.logSet()

        val failed = vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.FAILED && !it.logging }
        assertEquals(SetLogRules.INVALID_REPS, failed.save.message)
        assertEquals(listOf(LogCommitFeedback.REJECT), feedback)
    }

    @Test
    fun aWriteRefusedAsAConflictShowsTheConflict() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = active(handle(fixture.session.id))
        vm.uiState.awaitFirst { it.canLog }
        writeFailure = { WorkoutRepository.SetSaveConflict() }

        vm.logSet()

        val refused = vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.CONFLICT && !it.logging }
        assertEquals(WorkoutRepository.SetSaveConflict().message, refused.save.message)
        assertEquals(listOf(LogCommitFeedback.REJECT), feedback)
    }

    @Test
    fun aSavedSetCelebratesOnceAndClearsBothCopies() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val handle = handle(fixture.session.id)
        val vm = active(handle)
        vm.uiState.awaitFirst { it.canLog }

        vm.logSet()

        val saved = vm.uiState.awaitFirst { !it.logging && !it.save.pending && it.session?.sets?.size == 1 }
        assertEquals(WorkoutSavePhase.IDLE, saved.save.phase)
        assertNull(saved.save.message)
        assertEquals(listOf(LogCommitFeedback.SUCCESS), feedback)
        assertNotNull(vm.logReceipt.value)
        assertNull(SavedStateWorkoutSave(handle).read(fixture.session.id))
        assertNull(deps.workoutDraftCache.pendingSave(fixture.session.id))
    }

    /** While a set is being written, Finish does nothing: the workout stays open. */
    @Test
    fun aSetBeingWrittenRefusesFinish() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = active(handle(fixture.session.id))
        vm.uiState.awaitFirst { it.canLog }
        insertGate = CompletableDeferred()
        vm.logSet()
        vm.uiState.awaitFirst { it.logging && it.save.phase == WorkoutSavePhase.SAVING }

        vm.finishWorkout()
        insertGate!!.complete(Unit)

        vm.uiState.awaitFirst { !it.logging && it.session?.sets?.size == 1 }
        assertNull(deps.workoutRepository.getSession(fixture.session.id)!!.finishedAt)
    }

    @Test
    fun aRestoredSetTheDatabaseNeverSawIsOfferedForRetryWithItsReason() = runBlocking {
        val command = pendingCommand()
        val handle = handle(command.sessionId)
        SavedStateWorkoutSave(handle).write(command)

        val vm = active(handle)

        val offered = vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.FAILED && !it.logging }
        assertEquals("This set has not been saved. Retry to save these values.", offered.save.message)
        assertEquals(command, offered.save.command)
        assertTrue(feedback.isEmpty())
    }

    @Test
    fun anOutcomeThatCannotBeReadSaysSoAndWillCheckFirst() = runBlocking {
        val command = pendingCommand()
        val handle = handle(command.sessionId)
        SavedStateWorkoutSave(handle).write(command)
        failInspection = true

        val vm = active(handle)

        val unknown = vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.FAILED && !it.logging }
        assertEquals(
            "Could not confirm whether this set was saved. Retry will check before saving again.",
            unknown.save.message,
        )
        assertEquals(command, SavedStateWorkoutSave(handle).read(command.sessionId))
    }

    /** The cache is this run's copy and wins; the saved state is brought up to it. */
    @Test
    fun aRestoredSetComesFromTheCacheFirstAndIsWrittenBackToBothCopies() = runBlocking {
        val command = pendingCommand()
        val older = command.copy(values = command.values.copy(weightKg = 40.0))
        val handle = handle(command.sessionId)
        SavedStateWorkoutSave(handle).write(older)
        deps.workoutDraftCache.putPendingSave(command)

        val vm = active(handle)

        val restored = vm.uiState.awaitFirst { it.save.pending && !it.logging }
        assertEquals(command, restored.save.command)
        assertEquals(61.5, restored.draft.weightKg, 0.0)
        assertEquals(command, SavedStateWorkoutSave(handle).read(command.sessionId))
        assertEquals(command, deps.workoutDraftCache.pendingSave(command.sessionId))
    }

    @Test
    fun aSetOnlyInSavedStateIsWrittenBackToTheCache() = runBlocking {
        val command = pendingCommand()
        val handle = handle(command.sessionId)
        SavedStateWorkoutSave(handle).write(command)

        val vm = active(handle)

        vm.uiState.awaitFirst { it.save.pending && !it.logging }
        assertEquals(command, deps.workoutDraftCache.pendingSave(command.sessionId))
    }

    /** Edit on a set the database never took: released, both copies gone, the values kept. */
    @Test
    fun editingAnUnwrittenSetReleasesItAndClearsBothCopies() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val handle = handle(fixture.session.id)
        val vm = active(handle)
        vm.uiState.awaitFirst { it.canLog }
        vm.setWeight(72.5)
        writeFailure = { IllegalStateException("Injected write failure") }
        vm.logSet()
        vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.FAILED && !it.logging }
        writeFailure = null

        vm.editFailedSave()

        val released = vm.uiState.awaitFirst { !it.save.pending && !it.logging }
        assertEquals(WorkoutSavePhase.IDLE, released.save.phase)
        assertFalse(released.entryLocked)
        assertEquals(72.5, released.draft.weightKg, 0.0)
        assertNull(released.error)
        assertNull(SavedStateWorkoutSave(handle).read(fixture.session.id))
        assertNull(deps.workoutDraftCache.pendingSave(fixture.session.id))
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
    }

    /**
     * A refusal from before the set was frozen (here, a working set at 0) is still held, under the
     * failed save's words, when its fixed set fails to save; Edit lets go of both.
     */
    @Test
    fun editingAnUnwrittenSetClearsAnEarlierLogRefusalToo() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = active(handle(fixture.session.id))
        vm.uiState.awaitFirst { it.canLog }
        vm.setWeight(0.0)
        vm.logSet()
        vm.uiState.awaitFirst { it.error == SetLogRules.ZERO_WORKING_WEIGHT }
        vm.setWeight(72.5)
        writeFailure = { IllegalStateException("Injected write failure") }
        vm.logSet()
        val failed = vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.FAILED && !it.logging }
        assertEquals("the failed save speaks over it", LogCommitCopy.WRITE_FAILED, failed.error)
        writeFailure = null

        vm.editFailedSave()

        // The release clears the frozen set a moment before the refusal, and the screen's state
        // can show the first without the second (as it could before the move): wait for the
        // settled state. With the refusal kept, it never comes.
        val settled = vm.uiState.awaitFirst { !it.save.pending && !it.logging && it.error == null }
        assertNull(settled.error)
    }

    /** Edit on a correction whose set is gone: the edit and its original are let go too. */
    @Test
    fun editingAConflictedCorrectionForgetsTheEditAndItsOriginal() = runBlocking {
        val command = pendingCommand()
        deps.workoutRepository.saveSet(command)
        val edit = command.copy(original = command.values, values = command.values.copy(weightKg = 65.0))
        deps.workoutRepository.deleteSet(command.setId)
        val handle = handle(command.sessionId)
        SavedStateWorkoutSave(handle).write(edit)
        SavedStateWorkoutSave(handle = handle, storageKey = EDIT_ORIGINAL_KEY).write(edit)
        val vm = active(handle)
        vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.CONFLICT && it.editingSetId == edit.setId }

        vm.editFailedSave()

        val released = vm.uiState.awaitFirst { !it.save.pending && !it.logging && it.editingSetId == null }
        assertEquals(WorkoutSavePhase.IDLE, released.save.phase)
        assertNull(SavedStateWorkoutSave(handle).read(command.sessionId))
        assertNull(SavedStateWorkoutSave(handle = handle, storageKey = EDIT_ORIGINAL_KEY).read(command.sessionId))
        assertNull(deps.workoutDraftCache.editingOriginal(command.sessionId))
        assertNull(deps.workoutDraftCache.pendingSave(command.sessionId))
    }

    /** A conflict owns its words: Retry neither writes nor clears them. */
    @Test
    fun retryDoesNothingToAConflict() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = active(handle(fixture.session.id))
        vm.uiState.awaitFirst { it.canLog }
        writeFailure = { WorkoutRepository.SetSaveConflict() }
        vm.logSet()
        val refused = vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.CONFLICT && !it.logging }
        writeFailure = null

        vm.retrySave()

        assertEquals(refused.save, vm.uiState.value.save)
        assertFalse(vm.uiState.value.logging)
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
    }

    private suspend fun pendingCommand(): WorkoutSetSave {
        val fixture = seedTestWorkout(deps)
        return WorkoutSetSave(
            sessionId = fixture.session.id, exerciseId = fixture.exercise.id, setId = "pending",
            completedAt = 1_700_000_000_000,
            values = WorkoutSetValues(61.5, 7, 8, false, 13),
        )
    }

    private fun handle(sessionId: String) = SavedStateHandle(mapOf("sessionId" to sessionId))

    private fun active(handle: SavedStateHandle) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(), savedStateHandle = handle, container = deps,
    ).also { vm ->
        models += vm
        collectors.launch { vm.logFeedback.collect { feedback += it } }
    }

    private companion object {
        const val EDIT_ORIGINAL_KEY = "workout.editOriginal.v1"
    }
}
