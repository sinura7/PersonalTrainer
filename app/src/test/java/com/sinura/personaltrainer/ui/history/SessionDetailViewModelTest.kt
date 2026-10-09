package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.FailingObserveSessionDao
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.WorkoutReadGate
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.components.NotesSaveStatus
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Finished-session repair: timestamps stay put, notes flush on leave,
 * undo restores identity, and Repeat never silently steals a live workout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SessionDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: SessionDetailViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun missingSessionResolvesWithoutSpinner() = runBlocking {
        val vm = createViewModel("missing")
        val state = vm.uiState.awaitFirst { !it.isLoading }

        assertNull(state.session)
        assertTrue(state.missing)
        assertFalse(state.failed)
        assertFalse(state.isLoading)
    }

    @Test
    fun finishedSessionLoadsNotesIntoDraft() = runBlocking {
        val fixture = seedFinished(notes = "original note")
        val vm = createViewModel(fixture.id)

        val state = vm.uiState.awaitFirst { !it.isLoading && it.notes == "original note" }
        assertEquals(fixture.id, state.session?.id)
    }

    @Test
    fun updateSetPreservesTimestampAndSetNumber() = runBlocking {
        val fixture = seedFinished()
        val original = fixture.sets.single()
        val vm = createViewModel(fixture.id)
        vm.uiState.awaitFirst { !it.isLoading }

        vm.updateSet(original.id, 105.0, 6, rpe = 9, isWarmup = false)

        val updated = awaitSession(fixture.id) {
            it.sets.singleOrNull()?.weightKg == 105.0
        }.sets.single()
        assertEquals(original.id, updated.id)
        assertEquals(original.completedAt, updated.completedAt)
        assertEquals(original.setNumber, updated.setNumber)
        assertEquals(6, updated.reps)
        assertEquals(9, updated.rpe)
    }

    @Test
    fun addSetAppendsInsideFinishedSessionWindow() = runBlocking {
        val fixture = seedFinished()
        val vm = createViewModel(fixture.id)
        vm.uiState.awaitFirst { !it.isLoading }

        vm.addSet(TEST_EXERCISE, 90.0, 8, rpe = 8, isWarmup = false)

        val updated = awaitSession(fixture.id) { it.sets.size == 2 }
        val added = updated.sets.maxBy { it.setNumber }
        assertEquals(2, added.setNumber)
        assertTrue(added.completedAt >= updated.startedAt)
        assertTrue(added.completedAt <= checkNotNull(updated.finishedAt))
    }

    @Test
    fun deleteOffersUndoAndRestoresOriginalIdentityAndTime() = runBlocking {
        val fixture = seedFinished()
        val original = fixture.sets.single()
        val vm = createViewModel(fixture.id)
        vm.uiState.awaitFirst { !it.isLoading }

        vm.deleteSet(original.id)

        val offered = checkNotNull(vm.deletedSet.awaitFirst { it != null })
        assertEquals(original.id, offered.setId)
        awaitSession(fixture.id) { it.sets.isEmpty() }

        vm.undoDeleteSet()

        val restored = awaitSession(fixture.id) { it.sets.size == 1 }.sets.single()
        assertEquals(original.id, restored.id)
        assertEquals(original.completedAt, restored.completedAt)
        assertNull(vm.deletedSet.value)
    }

    @Test
    fun notesDebounceAndExitFlushBothPersist() = runBlocking {
        // Room still runs on real worker threads. Only this timing test queues Main on the
        // test thread, so observing hydration cannot race collectLatest arming its pause.
        deps.close()
        val notesWrites = CopyOnWriteArrayList<Pair<String, Long>>()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { real ->
                object : WorkoutDao by real {
                    override suspend fun updateSessionNotes(id: String, notes: String) {
                        notesWrites.add(notes to dispatcher.scheduler.currentTime)
                        real.updateSessionNotes(id, notes)
                    }
                }
            },
        )
        val fixture = seedFinished()
        val main = StandardTestDispatcher(scheduler = dispatcher.scheduler, name = "notes timing Main")
        Dispatchers.setMain(main)
        val vm = createViewModel(fixture.id)
        val subscriber = launch(main) { vm.uiState.collect { } }

        // Pump queued Main answers while Room runs, never advancing the typing clock.
        suspend fun <T> awaitOnMain(what: String, read: suspend () -> T, done: (T) -> Boolean): T {
            var last: Any? = null
            return try {
                withTimeout(TestWaits.FLOW_MS) {
                    dispatcher.scheduler.runCurrent()
                    var current = read()
                    last = current
                    while (!done(current)) {
                        delay(10)
                        dispatcher.scheduler.runCurrent()
                        current = read()
                        last = current
                    }
                    current
                }
            } catch (timedOut: TimeoutCancellationException) {
                throw AssertionError("$what never came; last read: $last; notes DAO calls: $notesWrites", timedOut)
            }
        }

        try {
            awaitOnMain(what = "confirmed notes hydration", read = { vm.uiState.value }) {
                !it.isLoading && it.session?.id == fixture.id &&
                    it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy
            }
            assertEquals(emptyList<Pair<String, Long>>(), notesWrites.toList())
            val editedAt = dispatcher.scheduler.currentTime
            vm.setNotes("debounced")
            dispatcher.scheduler.runCurrent()
            dispatcher.scheduler.advanceTimeBy(399)
            dispatcher.scheduler.runCurrent()
            assertEquals("no DAO write before 400 ms", emptyList<Pair<String, Long>>(), notesWrites.toList())
            assertEquals("", deps.workoutRepository.getSession(fixture.id)?.notes)
            assertEquals(editedAt + 399, dispatcher.scheduler.currentTime)

            dispatcher.scheduler.advanceTimeBy(1)
            dispatcher.scheduler.runCurrent()
            assertEquals("one exact notes write at the deadline", listOf("debounced" to editedAt + 400), notesWrites.toList())
            val debounced = awaitOnMain(
                what = "the exact debounced notes row",
                read = { deps.workoutRepository.getSession(fixture.id) },
            ) { it?.notes == "debounced" }
            assertEquals(fixture.copy(notes = "debounced"), debounced)
            awaitOnMain(what = "confirmed debounced notes", read = { vm.uiState.value }) {
                it.notes == "debounced" && it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy
            }

            val timeBeforeExit = dispatcher.scheduler.currentTime
            vm.setNotes("leave immediately")
            vm.persistNotesForExit()
            dispatcher.scheduler.runCurrent()
            val flushed = awaitOnMain(
                what = "the exact exit-flushed notes row",
                read = { deps.workoutRepository.getSession(fixture.id) },
            ) { it?.notes == "leave immediately" }
            awaitOnMain(what = "confirmed exit-flushed notes", read = { vm.uiState.value }) {
                it.notes == "leave immediately" && it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy
            }
            assertEquals(fixture.copy(notes = "leave immediately"), flushed)
            assertEquals(
                listOf("debounced" to editedAt + 400, "leave immediately" to timeBeforeExit),
                notesWrites.toList(),
            )
            assertEquals("exit flush did not wait for another virtual typing pause", timeBeforeExit, dispatcher.scheduler.currentTime)
        } finally {
            val job = checkNotNull(vm.viewModelScope.coroutineContext[Job])
            job.cancel()
            subscriber.cancel()
            try {
                awaitOnMain(what = "notes VM and subscriber teardown", read = { job.isCompleted && subscriber.isCompleted }) { it }
            } finally {
                viewModel = null
                Dispatchers.setMain(dispatcher)
            }
        }
    }

    @Test
    fun validationErrorIsUserFacingAndCanBeAcknowledged() = runBlocking {
        val fixture = seedFinished()
        val original = fixture.sets.single()
        val vm = createViewModel(fixture.id)
        vm.uiState.awaitFirst { !it.isLoading }

        vm.updateSet(original.id, 0.0, 5, rpe = 8, isWarmup = false)

        assertEquals(
            SetLogRules.ZERO_WORKING_WEIGHT,
            vm.error.awaitFirst { it == SetLogRules.ZERO_WORKING_WEIGHT },
        )
        vm.onErrorShown()
        assertNull(vm.error.value)
        assertEquals(100.0, deps.workoutRepository.getSession(fixture.id)!!.sets.single().weightKg, 0.0001)
    }

    @Test
    fun repeatWhenIdleNavigatesOnceAndCopiesNoLoggedSets() = runBlocking {
        val fixture = seedFinished()
        val vm = createViewModel(fixture.id)
        vm.uiState.awaitFirst { !it.isLoading }

        vm.repeatSession()

        val newId = checkNotNull(vm.navigateToSession.awaitFirst { it != null })
        val repeated = checkNotNull(deps.workoutRepository.getSession(newId))
        assertTrue(repeated.sets.isEmpty())
        assertEquals(1, repeated.exercises.size)
        vm.onNavigationHandled()
        assertNull(vm.navigateToSession.value)
    }

    @Test
    fun repeatWhileLiveSurfacesBlockedAndResumeNavigatesToLive() = runBlocking {
        val fixture = seedFinished()
        val live = deps.workoutRepository.startFreeWorkout()
        val vm = createViewModel(fixture.id)
        vm.uiState.awaitFirst { !it.isLoading }

        vm.repeatSession()

        val blocked = checkNotNull(vm.blockedRepeat.awaitFirst { it != null })
        assertEquals(live.id, blocked.inProgressSessionId)
        assertNull(vm.navigateToSession.value)
        vm.resumeBlockedSession()
        assertNull(vm.blockedRepeat.value)
        assertEquals(live.id, vm.navigateToSession.value)
    }

    /**
     * P2a (owner decision of 29 September 2026): a corrected or added working set needs its
     * effort as a fresh one does; a warm-up and a hold (no reps) do not. Nothing is written
     * on a refusal.
     */
    @Test
    fun aWorkingSetCorrectionOrAdditionWithoutAnEffortIsRefusedAndWritesNothing() = runBlocking {
        val fixture = seedFinished()
        val original = fixture.sets.single()
        val vm = createViewModel(fixture.id)
        vm.uiState.awaitFirst { !it.isLoading }

        vm.updateSet(original.id, 105.0, 6, rpe = null, isWarmup = false)
        assertEquals(SetLogRules.EFFORT_MISSING, vm.error.awaitFirst { it == SetLogRules.EFFORT_MISSING })
        vm.onErrorShown()
        assertEquals(100.0, deps.workoutRepository.getSession(fixture.id)!!.sets.single().weightKg, 0.0001)

        vm.addSet(TEST_EXERCISE, 90.0, 8, rpe = null, isWarmup = false)
        assertEquals(SetLogRules.EFFORT_MISSING, vm.error.awaitFirst { it == SetLogRules.EFFORT_MISSING })
        vm.onErrorShown()
        assertEquals(1, deps.workoutRepository.getSession(fixture.id)!!.sets.size)

        // A warm-up still goes in without one.
        vm.addSet(TEST_EXERCISE, 40.0, 8, rpe = null, isWarmup = true)
        assertTrue(awaitSession(fixture.id) { it.sets.size == 2 }.sets.any { it.isWarmup })
    }

    @Test
    fun addSetValidationErrorIsUserFacingAndWritesNothing() = runBlocking {
        val fixture = seedFinished()
        val vm = createViewModel(fixture.id)
        vm.uiState.awaitFirst { !it.isLoading }

        vm.addSet(TEST_EXERCISE, 0.0, 5, rpe = 8, isWarmup = false)

        assertEquals(
            SetLogRules.ZERO_WORKING_WEIGHT,
            vm.error.awaitFirst { it == SetLogRules.ZERO_WORKING_WEIGHT },
        )
        assertEquals(1, deps.workoutRepository.getSession(fixture.id)!!.sets.size)
    }

    @Test
    fun repeatMissingSessionSurfacesFailedWithoutNavigating() = runBlocking {
        val vm = createViewModel("missing")
        vm.uiState.awaitFirst { !it.isLoading }

        vm.repeatSession()

        assertEquals(
            "That session is no longer available.",
            vm.error.awaitFirst { it == "That session is no longer available." },
        )
        assertNull(vm.navigateToSession.value)
        assertNull(vm.blockedRepeat.value)
        assertNull(deps.workoutRepository.getInProgress())
    }

    @Test
    fun dismissBlockedRepeatClearsTheOfferWithoutNavigating() = runBlocking {
        val fixture = seedFinished()
        val live = deps.workoutRepository.startFreeWorkout()
        val vm = createViewModel(fixture.id)
        vm.uiState.awaitFirst { !it.isLoading }

        vm.repeatSession()
        vm.blockedRepeat.awaitFirst { it != null }

        vm.dismissBlockedRepeat()

        assertNull(vm.blockedRepeat.value)
        assertNull(vm.navigateToSession.value)
        assertEquals(live.id, deps.workoutRepository.getInProgress()?.id)
    }

    @Test
    fun undoWithNothingPendingIsANoOp() = runBlocking {
        val fixture = seedFinished()
        val vm = createViewModel(fixture.id)
        vm.uiState.awaitFirst { !it.isLoading }

        vm.undoDeleteSet()

        assertEquals(1, deps.workoutRepository.getSession(fixture.id)!!.sets.size)
        assertNull(vm.deletedSet.value)
        assertNull(vm.error.value)
    }

    @Test
    fun deleteSessionRemovesRowAndEmitsDeleted() = runBlocking {
        val fixture = seedFinished()
        val vm = createViewModel(fixture.id)
        vm.uiState.awaitFirst { !it.isLoading }

        vm.deleteSession()

        vm.deleted.awaitFirst { it }
        assertNull(deps.workoutRepository.getSession(fixture.id))
    }

    @Test
    fun aFailedReadIsUnavailableNotMissingAndRetries() = runBlocking {
        val gate = WorkoutReadGate(shouldFail = false)
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { FailingObserveSessionDao(it, gate) },
        )
        val fixture = seedFinished()
        gate.shouldFail = true

        val vm = createViewModel(fixture.id)
        val failed = withTimeout(TestWaits.FLOW_MS) { vm.uiState.first { !it.isLoading } }
        assertTrue(failed.failed)
        assertFalse("a read fault must not read as a deleted session", failed.missing)

        gate.shouldFail = false
        vm.retry()
        val loaded = withTimeout(TestWaits.FLOW_MS) { vm.uiState.first { !it.isLoading && !it.failed } }
        assertEquals(fixture.id, loaded.session?.id)
    }

    private fun createViewModel(sessionId: String): SessionDetailViewModel =
        SessionDetailViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also { viewModel = it }

    private suspend fun seedFinished(notes: String = ""): WorkoutSession =
        seedTestWorkout(
            deps = deps,
            exerciseId = TEST_EXERCISE,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
            notes = notes,
        ).session

    private suspend fun awaitSession(
        id: String,
        predicate: (WorkoutSession) -> Boolean,
    ): WorkoutSession = withTimeout(TestWaits.FLOW_MS) {
        checkNotNull(
            deps.workoutRepository.observeSession(id).first { session ->
                session != null && predicate(session)
            },
        )
    }

    private companion object {
        const val TEST_EXERCISE = "test-squat"
    }
}
