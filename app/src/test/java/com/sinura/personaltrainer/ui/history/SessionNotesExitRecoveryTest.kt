package com.sinura.personaltrainer.ui.history

import android.app.Application
import android.database.sqlite.SQLiteFullException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.components.NotesSaveStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

/** A failed History note remains an edit until its actual row is confirmed or leaving is explicit. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SessionNotesExitRecoveryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val failNext = AtomicBoolean(false)
    private val holdNext = AtomicBoolean(false)
    private val writes = AtomicInteger(0)
    private val failNextStoredRead = AtomicBoolean(false)
    private val storedReadFailures = AtomicInteger(0)
    private val held = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()
    private lateinit var deps: FakeAppDependencies
    private var vm: SessionDetailViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher,
            workoutDaoDecorator = { real ->
                object : WorkoutDao by real {
                    override suspend fun getSession(id: String): SessionWithDetails? {
                        if (failNextStoredRead.compareAndSet(true, false)) {
                            storedReadFailures.incrementAndGet()
                            error("Injected stored notes read failure")
                        }
                        return real.getSession(id)
                    }

                    override suspend fun updateSessionNotes(id: String, notes: String) {
                        writes.incrementAndGet()
                        if (failNext.compareAndSet(true, false)) throw SQLiteFullException("Injected notes failure")
                        if (holdNext.compareAndSet(true, false)) {
                            held.complete(Unit)
                            release.await()
                        }
                        real.updateSessionNotes(id, notes)
                    }
                }
            },
        )
    }

    @After
    fun tearDown() {
        release.complete(Unit)
        runBlocking { vm?.clearAndJoinForTest() }
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun failureBlocksExitAndHeldDoubleRetrySavesTheSameSessionOnceWithoutChangingItsTimes() = runBlocking {
        val fixture = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(100.0, 5)), finish = true, notes = "old").session
        val model = SessionDetailViewModel(
            ApplicationProvider.getApplicationContext(), SavedStateHandle(mapOf("sessionId" to fixture.id)), deps,
        ).also { vm = it }
        model.uiState.awaitFirst { it.notes == "old" && it.notesSave.status == NotesSaveStatus.SAVED }
        dispatcher.scheduler.runCurrent()
        failNext.set(true)
        model.setNotes(" latest ")
        // Start the edit's collectLatest pause before advancing its virtual deadline.
        // A Room hydration publication alone does not drain those current coroutine tasks.
        dispatcher.scheduler.runCurrent()
        model.uiState.awaitFirst { it.notes == " latest " && it.notesSave.status == NotesSaveStatus.PENDING }
        assertEquals("no DAO attempt before the typing pause", 0, writes.get())
        assertEquals("old", deps.workoutRepository.getSession(fixture.id)!!.notes)
        dispatcher.scheduler.advanceTimeBy(401)
        dispatcher.scheduler.runCurrent()
        val failed = model.uiState.awaitFirst { it.notesSave.status == NotesSaveStatus.FAILED }
        assertTrue(failed.notesSave.canRetry)
        assertEquals(" latest ", failed.notes)
        assertEquals("old", deps.workoutRepository.getSession(fixture.id)!!.notes)
        assertNull(model.error.value)

        failNext.set(true)
        model.requestNotesExit()
        model.notesExitBlocked.awaitFirst { it }
        assertFalse(model.notesExitRequested.value)
        assertEquals(" latest ", model.uiState.value.notes)

        holdNext.set(true)
        model.retryNotesSave()
        model.retryNotesSave()
        withTimeout(TestWaits.FLOW_MS) { held.await() }
        model.uiState.awaitFirst { it.notesSave.status == NotesSaveStatus.SAVING }
        assertEquals(3, writes.get())
        model.requestNotesExit()
        assertFalse("exit waits for confirmation of the held Retry", model.notesExitRequested.value)
        release.complete(Unit)
        model.notesExitRequested.awaitFirst { it }
        assertEquals("exit did not duplicate Retry's write", 3, writes.get())
        val stored = deps.workoutRepository.getSession(fixture.id)!!
        assertEquals("latest", stored.notes)
        assertEquals(" latest ", model.uiState.value.notes)
        assertEquals(fixture.startedAt, stored.startedAt)
        assertEquals(fixture.finishedAt, stored.finishedAt)
        assertEquals(fixture.sets.single().id, stored.sets.single().id)
        assertEquals(fixture.sets.single().completedAt, stored.sets.single().completedAt)

        model.onNotesExitHandled()
        failNext.set(true)
        model.setNotes("unsaved second change")
        model.requestNotesExit()
        model.notesExitBlocked.awaitFirst { it }
        assertFalse(model.notesExitRequested.value)
        model.requestNotesExit(leaveWithoutChanges = true)
        assertTrue("leaving a failed draft requires the explicit choice", model.notesExitRequested.value)
        dispatcher.scheduler.advanceTimeBy(401)
        dispatcher.scheduler.runCurrent()
        assertEquals("latest", deps.workoutRepository.getSession(fixture.id)!!.notes)
        assertEquals("the pending pause is disarmed by explicit leaving", 4, writes.get())
    }

    @Test
    fun failedRepeatCreatesNothingUntilHeldRetryConfirmsTheNotesAndStartsOnce() = runBlocking {
        val original = finishedWithNotes()
        withControlledModel(original.id) { model ->
            failNext.set(true)
            model.setNotes(" repeat draft ")
            model.repeatSession()
            model.repeatSession()
            awaitBlockedNotes(model)
            assertEquals(1, writes.get())
            assertEquals(setOf(original.id), sessionIds())
            assertEquals(original, deps.workoutRepository.getSession(original.id))
            assertNull(model.navigateToSession.value)
            assertFalse(model.notesExitRequested.value)
            assertNull(model.error.value)

            holdNext.set(true)
            model.requestNotesExit()
            model.requestNotesExit()
            pumpUntil(what = "held Repeat retry", read = { held.isCompleted }) { it }
            awaitState(model) { it.notesSave.status == NotesSaveStatus.SAVING }
            model.setNotes("an edit during pending Repeat")
            assertEquals(" repeat draft ", model.uiState.value.notes)
            assertEquals(setOf(original.id), sessionIds())
            assertNull(model.navigateToSession.value)
            release.complete(Unit)
            val target = checkNotNull(pumpUntil(
                what = "Repeat navigation after confirmation", read = { model.navigateToSession.value },
            ) { it != null })
            assertEquals(2, writes.get())
            assertEquals(setOf(original.id, target), sessionIds())
            assertEquals(original.copy(notes = "repeat draft"), deps.workoutRepository.getSession(original.id))
            val repeated = checkNotNull(deps.workoutRepository.getSession(target))
            assertTrue(repeated.sets.isEmpty())
            assertEquals(original.exercises.map { it.exercise.id }, repeated.exercises.map { it.exercise.id })
            assertEquals(target, deps.database.workoutDao().getInProgressSession()?.id)
            assertFalse(model.notesExitRequested.value)
            awaitState(model) { it.notesExiting && it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy }
            model.setNotes("queued IME after Repeat navigation")
            dispatcher.scheduler.advanceTimeBy(401)
            dispatcher.scheduler.runCurrent()
            assertEquals(" repeat draft ", model.uiState.value.notes)
            assertEquals(2, writes.get())
            assertEquals(original.copy(notes = "repeat draft"), deps.workoutRepository.getSession(original.id))
            assertEquals(repeated, deps.workoutRepository.getSession(target))

            model.onNavigationHandled()
            awaitState(model) { !it.notesExiting }
            model.setNotes(" edit after Repeat acknowledgement ")
            model.persistNotesForExit()
            awaitState(model) { it.notes == " edit after Repeat acknowledgement " && it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy }
            assertEquals(3, writes.get())
            assertEquals(original.copy(notes = "edit after Repeat acknowledgement"), deps.workoutRepository.getSession(original.id))
            assertEquals(repeated, deps.workoutRepository.getSession(target))
            assertEquals(setOf(original.id, target), sessionIds())
        }
    }

    @Test
    fun failedResumeDoesNotNavigateOrCreateAnythingAndRetryReturnsToTheExactLiveSession() = runBlocking {
        val original = finishedWithNotes()
        val live = deps.workoutRepository.startFreeWorkout()
        withControlledModel(original.id) { model ->
            model.repeatSession()
            pumpUntil(what = "blocked Repeat before Resume", read = { model.blockedRepeat.value }) { it != null }
            failNext.set(true)
            model.setNotes(" resume draft ")
            model.resumeBlockedSession()
            model.resumeBlockedSession()
            awaitBlockedNotes(model)
            assertEquals(1, writes.get())
            assertNull(model.navigateToSession.value)
            assertEquals(setOf(original.id, live.id), sessionIds())
            assertEquals(original, deps.workoutRepository.getSession(original.id))
            assertEquals(live, deps.workoutRepository.getSession(live.id))

            holdNext.set(true)
            model.requestNotesExit()
            model.requestNotesExit()
            pumpUntil(what = "held Resume retry", read = { held.isCompleted }) { it }
            assertNull(model.navigateToSession.value)
            release.complete(Unit)
            pumpUntil(what = "Resume navigation after confirmation", read = { model.navigateToSession.value }) { it == live.id }
            assertEquals(2, writes.get())
            assertEquals(setOf(original.id, live.id), sessionIds())
            assertEquals(original.copy(notes = "resume draft"), deps.workoutRepository.getSession(original.id))
            assertEquals(live, deps.workoutRepository.getSession(live.id))
            assertEquals(live.id, deps.database.workoutDao().getInProgressSession()?.id)
            assertFalse(model.notesExitRequested.value)
            assertNull(model.error.value)
            awaitState(model) { it.notesExiting && it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy }
            model.setNotes("queued IME after Resume navigation")
            dispatcher.scheduler.advanceTimeBy(401)
            dispatcher.scheduler.runCurrent()
            assertEquals(" resume draft ", model.uiState.value.notes)
            assertEquals(2, writes.get())
            assertEquals(original.copy(notes = "resume draft"), deps.workoutRepository.getSession(original.id))
            assertEquals(live, deps.workoutRepository.getSession(live.id))

            model.onNavigationHandled()
            awaitState(model) { !it.notesExiting }
            model.setNotes(" edit after Resume acknowledgement ")
            model.persistNotesForExit()
            awaitState(model) { it.notes == " edit after Resume acknowledgement " && it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy }
            assertEquals(3, writes.get())
            assertEquals(original.copy(notes = "edit after Resume acknowledgement"), deps.workoutRepository.getSession(original.id))
            assertEquals(live, deps.workoutRepository.getSession(live.id))
            assertEquals(setOf(original.id, live.id), sessionIds())
        }
    }

    @Test
    fun heldBackFlushRejectsQueuedNotesUntilItsLeaveEventIsAcknowledged() = runBlocking {
        val original = finishedWithNotes()
        withControlledModel(original.id) { model ->
            holdNext.set(true)
            model.setNotes(" held Back draft ")
            model.requestNotesExit()
            pumpUntil(what = "held Back notes write", read = { held.isCompleted }) { it }
            awaitState(model) { it.notesExiting && it.notesSave.status == NotesSaveStatus.SAVING }
            assertFalse(model.notesExitRequested.value)
            release.complete(Unit)
            pumpUntil(what = "Back leave event after notes confirmation", read = { model.notesExitRequested.value }) { it }
            awaitState(model) { it.notesExiting && it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy }
            model.setNotes("queued IME after Back leave event")
            dispatcher.scheduler.advanceTimeBy(401)
            dispatcher.scheduler.runCurrent()
            assertEquals(" held Back draft ", model.uiState.value.notes)
            assertEquals(1, writes.get())
            assertEquals(original.copy(notes = "held Back draft"), deps.workoutRepository.getSession(original.id))
            assertEquals(setOf(original.id), sessionIds())
            assertTrue(model.notesExitRequested.value)
            assertNull(model.navigateToSession.value)
            model.onNotesExitHandled()
            awaitState(model) { !it.notesExiting }
        }
    }

    @Test
    fun explicitDiscardForRepeatReloadsTheDurableDraftAndTheRetainedHistoryEntryCanEditAgain() = runBlocking {
        val original = finishedWithNotes()
        withControlledModel(original.id) { model ->
            failNext.set(true)
            model.setNotes("discard this Repeat draft")
            model.repeatSession()
            awaitBlockedNotes(model)
            model.requestNotesExit(leaveWithoutChanges = true)
            val target = checkNotNull(pumpUntil(
                what = "Repeat after explicit notes discard", read = { model.navigateToSession.value },
            ) { it != null })
            awaitState(model) { it.notes == "old" && it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy }
            val repeated = checkNotNull(deps.workoutRepository.getSession(target))
            dispatcher.scheduler.advanceTimeBy(401)
            dispatcher.scheduler.runCurrent()
            assertEquals(1, writes.get())
            assertEquals(original, deps.workoutRepository.getSession(original.id))
            assertEquals(setOf(original.id, target), sessionIds())

            // AppNav keeps this History entry beneath Active. Returning reuses its notes owner.
            model.onNavigationHandled()
            model.setNotes(" edit after returning ")
            model.persistNotesForExit()
            awaitState(model) { it.notes == " edit after returning " && it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy }
            assertEquals(2, writes.get())
            assertEquals(original.copy(notes = "edit after returning"), deps.workoutRepository.getSession(original.id))
            assertEquals(repeated, deps.workoutRepository.getSession(target))
            assertEquals(setOf(original.id, target), sessionIds())
        }
    }

    @Test
    fun explicitDiscardForResumeKeepsTheLiveRowAndReturningToHistoryCanSaveAndLeaveAgain() = runBlocking {
        val original = finishedWithNotes()
        val live = deps.workoutRepository.startFreeWorkout()
        withControlledModel(original.id) { model ->
            model.repeatSession()
            pumpUntil(what = "blocked Repeat before discarded Resume", read = { model.blockedRepeat.value }) { it != null }
            failNext.set(true)
            model.setNotes("discard this Resume draft")
            model.resumeBlockedSession()
            awaitBlockedNotes(model)
            model.requestNotesExit(leaveWithoutChanges = true)
            pumpUntil(what = "Resume after explicit notes discard", read = { model.navigateToSession.value }) { it == live.id }
            awaitState(model) { it.notes == "old" && it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy }
            dispatcher.scheduler.advanceTimeBy(401)
            dispatcher.scheduler.runCurrent()
            assertEquals(1, writes.get())
            assertEquals(original, deps.workoutRepository.getSession(original.id))
            assertEquals(live, deps.workoutRepository.getSession(live.id))

            model.onNavigationHandled()
            model.setNotes(" new History draft ")
            model.requestNotesExit()
            pumpUntil(what = "Back after editing retained History", read = { model.notesExitRequested.value }) { it }
            awaitState(model) { it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy }
            assertEquals(2, writes.get())
            assertEquals(original.copy(notes = "new History draft"), deps.workoutRepository.getSession(original.id))
            assertEquals(live, deps.workoutRepository.getSession(live.id))
            assertEquals(setOf(original.id, live.id), sessionIds())
        }
    }

    @Test
    fun keepEditingCancelsTheFailedRepeatIntentSoLaterBackDoesNotStartAWorkout() = runBlocking {
        val original = finishedWithNotes()
        withControlledModel(original.id) { model ->
            failNext.set(true)
            model.setNotes("failed Repeat draft")
            model.repeatSession()
            awaitBlockedNotes(model)
            model.keepEditingNotes()
            assertFalse(model.notesExitBlocked.value)
            model.setNotes(" kept editing instead ")
            model.requestNotesExit()
            pumpUntil(what = "Back after keeping notes editing", read = { model.notesExitRequested.value }) { it }
            assertNull(model.navigateToSession.value)
            assertEquals(2, writes.get())
            assertEquals(setOf(original.id), sessionIds())
            assertEquals(original.copy(notes = "kept editing instead"), deps.workoutRepository.getSession(original.id))
        }
    }

    @Test
    fun failedDiscardReadKeepsTheDraftWithoutAutosavingItAndExplicitRetryCanProceed() = runBlocking {
        val original = finishedWithNotes()
        withControlledModel(original.id) { model ->
            failNext.set(true)
            model.setNotes(" words chosen for discard ")
            model.repeatSession()
            awaitBlockedNotes(model)
            assertEquals(1, writes.get())
            failNextStoredRead.set(true)
            model.requestNotesExit(leaveWithoutChanges = true)
            awaitBlockedNotes(model)
            val failed = awaitState(model) { !it.notesExiting && it.notesSave.canRetry && !it.notesSave.busy }
            assertEquals(1, storedReadFailures.get())
            assertEquals(" words chosen for discard ", failed.notes)
            assertEquals(original, deps.workoutRepository.getSession(original.id))
            assertNull(model.navigateToSession.value)
            assertFalse(model.notesExitRequested.value)

            dispatcher.scheduler.advanceTimeBy(1_001)
            dispatcher.scheduler.runCurrent()
            assertEquals("a failed discard never implicitly saves that draft", 1, writes.get())
            assertEquals(original, deps.workoutRepository.getSession(original.id))
            assertEquals(setOf(original.id), sessionIds())
            assertNull(model.navigateToSession.value)
            awaitState(model) { it.notesSave.status == NotesSaveStatus.FAILED && it.notesSave.canRetry }

            holdNext.set(true)
            model.requestNotesExit()
            pumpUntil(what = "explicit Retry after failed discard read", read = { held.isCompleted }) { it }
            assertEquals(2, writes.get())
            assertEquals(original, deps.workoutRepository.getSession(original.id))
            assertNull(model.navigateToSession.value)
            release.complete(Unit)
            val target = checkNotNull(pumpUntil(
                what = "Repeat after explicit Retry confirms discarded draft", read = { model.navigateToSession.value },
            ) { it != null })
            assertEquals(2, writes.get())
            assertEquals(1, storedReadFailures.get())
            assertEquals(original.copy(notes = "words chosen for discard"), deps.workoutRepository.getSession(original.id))
            assertEquals(setOf(original.id, target), sessionIds())
            assertTrue(checkNotNull(deps.workoutRepository.getSession(target)).sets.isEmpty())
        }
    }

    private suspend fun finishedWithNotes(): WorkoutSession = seedTestWorkout(
        deps = deps, loggedSets = listOf(TestSetInput(100.0, 5)), finish = true, notes = "old",
    ).session

    /** New forwarding cases serialize Main answers on the test thread while real Room runs. */
    private suspend fun withControlledModel(sessionId: String, block: suspend (SessionDetailViewModel) -> Unit) = coroutineScope {
        val main = StandardTestDispatcher(scheduler = dispatcher.scheduler, name = "History notes Main")
        Dispatchers.setMain(main)
        val model = SessionDetailViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)), container = deps,
        ).also { vm = it }
        val subscriber = launch(main) { model.uiState.collect { } }
        try {
            awaitState(model) { it.session?.id == sessionId && it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy }
            block(model)
        } finally {
            release.complete(Unit)
            val job = checkNotNull(model.viewModelScope.coroutineContext[Job])
            job.cancel()
            subscriber.cancel()
            try {
                pumpUntil(what = "History notes VM teardown", read = { job.isCompleted && subscriber.isCompleted }) { it }
            } finally {
                vm = null
                Dispatchers.setMain(dispatcher)
            }
        }
    }

    private suspend fun awaitState(model: SessionDetailViewModel, done: (SessionDetailUiState) -> Boolean): SessionDetailUiState =
        pumpUntil(what = "History notes state", read = { model.uiState.value }, done = done)

    private suspend fun awaitBlockedNotes(model: SessionDetailViewModel) {
        pumpUntil(what = "failed notes intent", read = { model.notesExitBlocked.value }) { it }
        awaitState(model) { it.notesSave.status == NotesSaveStatus.FAILED && it.notesSave.canRetry }
    }

    private suspend fun <T> pumpUntil(what: String, read: suspend () -> T, done: (T) -> Boolean): T {
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
            throw AssertionError("$what never came; last: $last; notes writes: ${writes.get()}", timedOut)
        }
    }

    private fun sessionIds(): Set<String> =
        deps.database.openHelper.readableDatabase.query("SELECT id FROM workout_sessions ORDER BY id").use { rows ->
            buildSet { while (rows.moveToNext()) add(rows.getString(0)) }
        }
}
