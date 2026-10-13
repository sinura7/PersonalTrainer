package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.FinishedSetDraft
import com.sinura.personaltrainer.data.draft.FinishedSetDrafts
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.workout.WorkoutSavePhase
import com.sinura.personaltrainer.workout.SavedStateWorkoutSave
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** Owning VM, Room and private store together; browser/component tests cannot establish these paths. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FinishedSetViewModelRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val main = StandardTestDispatcher()
    private val io = UnconfinedTestDispatcher(main.scheduler)
    private lateinit var deps: FakeAppDependencies
    private lateinit var directory: File
    private lateinit var session: WorkoutSession
    private lateinit var service: FinishedSetDrafts
    private val subscriptionJob = SupervisorJob()
    private val subscriptions = CoroutineScope(subscriptionJob + main)
    private val models = mutableListOf<SessionDetailViewModel>()
    private var refuse = false
    private var writes = 0

    @Before fun prepare() = runBlocking {
        Dispatchers.setMain(main)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = io)
        directory = temporary.newFolder()
        session = seedTestWorkout(deps = deps, exerciseId = "finished-vm-squat",
            loggedSets = listOf(TestSetInput(100.0, 5)), finish = true).session
        val privateStore = DurableDraftStore(directory, io)
        deps.workoutRepository.beforeWorkoutDestruction = { event -> privateStore.invalidateOnce(event) }
        service = FinishedSetDrafts(store = privateStore, maintenance = deps.dbMaintenance,
            restoreOpen = { false }, dispatcher = io, readSession = deps.workoutRepository::getSession,
            inspectSaved = deps.workoutRepository::inspectFinishedSetSave, readSaved = deps.workoutRepository::readFinishedSetSaveResult,
            writeLocked = { command ->
                if (refuse) throw IOException("Synthetic refusal")
                deps.workoutRepository.saveFinishedSetLocked(command).also { writes++ }
            })
    }
    @After fun close() = runBlocking {
        val stopped = subscriptions.async { models.forEach { it.clearAndJoinForTest() } }
        await { stopped.isCompleted }; stopped.await()
        subscriptionJob.cancel(); main.scheduler.runCurrent(); subscriptionJob.join()
        deps.close(); Dispatchers.resetMain()
    }

    @Test fun mismatchedFrameworkAndPrivateCommandsBecomeActionableAndExplicitDiscardPreservesTheRow() = runBlocking {
        val fields = fields(); val handle = service.open(session.id)
        service.keep(handle, fields); refuse = true
        assertFalse(runCatching { service.write(handle, fields.command) }.isSuccess)
        val saved = SavedStateHandle(mapOf("sessionId" to session.id))
        val framework = fields.command.copy(values = fields.values.copy(reps = 7))
        SavedStateWorkoutSave(saved, "history.pendingSet.v1").write(framework)
        val vm = model(saved)
        await { !vm.setSave.value.busy && vm.finishedSetState.value.message != null }
        assertEquals(WorkoutSavePhase.FAILED, vm.setSave.value.phase)
        assertEquals(fields, vm.finishedSetState.value.draft)
        var discarded = false
        vm.discardHistoricalEditor { discarded = true }
        await { discarded }
        assertFalse(vm.setSave.value.pending)
        assertNull(vm.finishedSetState.value.draft)
        assertEquals(session.sets, checkNotNull(deps.workoutRepository.getSession(session.id)).sets)
        assertEquals(0, writes)
    }

    @Test fun closeKeepsTheFrozenCommandAndResumeReinstatesItBeforeAnyNewInput() = runBlocking {
        val fields = fields(); val handle = service.open(session.id)
        service.keep(handle, fields); refuse = true
        assertFalse(runCatching { service.write(handle, fields.command) }.isSuccess)
        val vm = model(SavedStateHandle(mapOf("sessionId" to session.id)))
        await { vm.setSave.value.phase == WorkoutSavePhase.FAILED && !vm.uiState.value.isLoading }
        var closed = false; vm.closeHistoricalEditor { closed = true }; await { closed }
        assertFalse(vm.setSave.value.pending)
        val form = checkNotNull(vm.finishedSetForm)
        assertFalse(form.allowsInput())
        form.showKept()
        assertTrue(vm.setSave.value.pending)
        form.update(fields.values.copy(reps = 999))
        await { vm.setSave.value.phase == WorkoutSavePhase.FAILED }
        assertEquals(fields.values, vm.finishedSetState.value.draft?.values)
        assertEquals(fields.command, vm.setSave.value.command)
        refuse = false; vm.retrySetSave()
        await { vm.setEditorExit.value != null && vm.finishedSetState.value.draft == null }
        val row = checkNotNull(deps.workoutRepository.getSession(session.id)).sets.single()
        assertEquals(fields.original.setId, row.id)
        assertEquals(fields.original.completedAt, row.completedAt)
        assertEquals(105.0, row.weightKg, 0.0)
        assertEquals(1, writes)
    }

    @Test fun conflictReviewRetainsTheAuthoredDraftWhileShowingTheNewSavedMeasurement() = runBlocking {
        val fields = fields(); val handle = service.open(session.id)
        service.keep(handle, fields); refuse = true
        assertFalse(runCatching { service.write(handle, fields.command) }.isSuccess)
        val vm = model(SavedStateHandle(mapOf("sessionId" to session.id)))
        await { vm.setSave.value.phase == WorkoutSavePhase.FAILED }
        deps.workoutRepository.updateSet(fields.original.setId, 120.0, 7, 9, false)
        vm.releaseSetSave(cancel = false)
        await { !vm.setSave.value.pending }
        assertEquals(fields, vm.finishedSetState.value.draft)
        assertEquals(120.0, checkNotNull(deps.workoutRepository.getSession(session.id)).sets.single().weightKg, 0.0)
        assertEquals(0, writes)
    }

    private fun model(saved: SavedStateHandle): SessionDetailViewModel = SessionDetailViewModel(
        application = ApplicationProvider.getApplicationContext(), savedStateHandle = saved,
        container = deps, drafts = null, setDrafts = service,
    ).also { vm -> models.add(vm); subscriptions.launch { vm.uiState.collect() } }
    private fun fields(): FinishedSetDraft {
        val row = session.sets.single(); val before = WorkoutSetValues.from(row)
        val original = WorkoutSetSave(sessionId = row.sessionId, exerciseId = row.exerciseId, setId = row.id,
            completedAt = row.completedAt, values = before, original = before)
        return FinishedSetDraft(FinishedSetDrafts.stamp(session), original, before.copy(weightKg = 105.0, rpe = 9))
    }
    private suspend fun await(ready: () -> Boolean) = withTimeout(TestWaits.FLOW_MS) {
        while (!ready()) { main.scheduler.runCurrent(); delay(10) }
    }
}
