package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.database.sqlite.SQLiteFullException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.dao.FinishedWorkingSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.components.NotesSaveStatus
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarViewModel
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutDraft
import com.sinura.personaltrainer.workout.FinishOutcome
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
 * The session notes' rules that no other test held, pinned before they move out of
 * ActiveWorkoutViewModel (W2d-3b): no write while the notes on disk are unknown, at a typing pause
 * or at Back; a cleared note is saved; the one-time fill from the row does not bring back a note
 * deleted inside a typing pause; a lift's load that ends after Back, keeping its entry or failing,
 * saves the last words; a write already underway lands before the next one starts; a failed write
 * is tried again with its own failure state; Finish inside a typing pause keeps the last words. The first
 * five were the adversarial T3 review's probes pb, pf, pg, ph and pi.
 *
 * Built as FloorSessionNotesCharacterisationTest builds it (real in-memory Room, a real
 * SavedStateHandle, an unconfined Main), with one change, so that no wait rests on how fast another
 * thread happens to be: a session row, and the answer to a notes write, reach the screen only on
 * the test's own thread, when the test runs the scheduler ([testThread]), as on a phone they reach
 * it on the main thread. What they resume then runs inline, so when a wait that runs them returns,
 * the screen has handled what it waited for. The database is seeded and read past the doors
 * ([unheld]), which a test thread blocked in `runBlocking` could not run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FloorSessionNotesMoveCharacterisationTest {
    private lateinit var dispatcher: TestDispatcher

    /** The test's own thread: what is sent here runs only when the test runs the scheduler. */
    private lateinit var testThread: TestDispatcher
    private lateinit var deps: FakeAppDependencies
    private lateinit var unheld: WorkoutRepository
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    /** Armed, the next finished-history read says so on [historyEntered] and waits for [releaseHistory]. */
    private val holdHistory = AtomicBoolean(false)

    /** With [holdHistory], the held read fails once it is released: the lift's load fails. */
    private val failHeldHistory = AtomicBoolean(false)
    private val historyEntered = CompletableDeferred<Unit>()
    private val releaseHistory = CompletableDeferred<Unit>()

    /** While true, each session row waits at the door before the screen hears of it. */
    private val sessionPaused = MutableStateFlow(false)

    /** Every session row handed to the screen, in order. */
    private val rowsDelivered = CopyOnWriteArrayList<SessionWithDetails?>()

    /** Every notes write that reached the DAO, in order. */
    private val writesStarted = CopyOnWriteArrayList<String>()
    private val writesStartedAt = CopyOnWriteArrayList<Long>()

    /** Every notes write the database took, in order, as its answer reached the screen. */
    private val writesLanded = CopyOnWriteArrayList<String>()

    /** Armed, the next notes write says so on [writeHeld] and waits for [releaseWrite] before it writes. */
    private val holdNextWrite = AtomicBoolean(false)
    private val writeHeld = CompletableDeferred<Unit>()
    private val releaseWrite = CompletableDeferred<Unit>()

    /** Armed, the next notes write fails as a full disk fails it. */
    private val failNextWrite = AtomicBoolean(false)
    private val finishesStarted = AtomicInteger(0)
    private val holdNextSessionRead = AtomicBoolean(false)
    private val sessionReadHeld = CompletableDeferred<Unit>()
    private val releaseSessionRead = CompletableDeferred<Unit>()
    private val holdNextFinishStart = AtomicBoolean(false)
    private val finishStartHeld = CompletableDeferred<Unit>()
    private val releaseFinishStart = CompletableDeferred<Unit>()
    private val failFinishStart = AtomicBoolean(false)

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        testThread = StandardTestDispatcher(scheduler = dispatcher.scheduler, name = "test thread")
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { real -> Doors(real) },
        )
        // The same database, past the doors.
        unheld = WorkoutRepository(deps.database, deps.database.workoutDao())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        releaseHistory.complete(Unit)
        releaseWrite.complete(Unit)
        releaseSessionRead.complete(Unit)
        releaseFinishStart.complete(Unit)
        sessionPaused.value = false
        if (::dispatcher.isInitialized) runBlocking { viewModels.toList().forEach { end(it) } }
        viewModels.clear()
        if (::deps.isInitialized) deps.restTimerController.stop()
        if (::dispatcher.isInitialized) dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun notesAreNeverWrittenWhileTheStoredNotesAreUnknown() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "stored")
        sessionPaused.value = true // the first read is slower than a typing pause, and than Back
        val vm = viewModel(handleFor(sessionId))

        pauseTyping()
        vm.persistDraftForExit()
        pauseTyping()
        assertEquals(
            "no notes write, at a typing pause or at Back, while the notes on disk are unknown",
            emptyList<String>(),
            writesStarted.toList(),
        )

        sessionPaused.value = false
        val shown = awaitScreen(vm, "the stored notes on screen") { it.notes == "stored" }
        assertEquals("the empty field takes the stored notes once they are read", "stored", shown.notes)
        assertEquals("and the row keeps them", "stored", unheld.getSession(sessionId)?.notes)
    }

    @Test
    fun clearingTheNotesSavesTheEmptyNote() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList()) // a session with no notes
        val vm = viewModel(handleFor(sessionId))
        // Handled on this thread: the screen knows the notes on disk are empty.
        runUntil(what = "the session row on the screen", read = { rowsDelivered.size }) { it >= 1 }

        vm.setNotes("abc")
        pauseTyping()
        awaitNotesWritesStarted(1)
        assertEquals("the pause writes the words", listOf("abc"), writesStarted.toList())
        // Its answer runs on this thread: the screen knows "abc" is stored before the next key.
        awaitWriteLanded("abc")
        awaitScreen(vm, "the first notes write is confirmed before the next key") {
            it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy
        }

        vm.setNotes("") // the owner clears the note
        pauseTyping()
        awaitNotesWritesStarted(2)
        assertEquals("the pause writes the cleared note", listOf("abc", ""), writesStarted.toList())
        awaitWriteLanded("")
        assertEquals("the row holds the empty note", "", unheld.getSession(sessionId)?.notes)
    }

    @Test
    fun aRowThatLandsWhileTheNoteIsBeingDeletedDoesNotBringItBack() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "abc")
        val handle = handleFor(sessionId)
        val vm = viewModel(handle)
        awaitScreen(vm, "the stored notes on screen") { it.notes == "abc" }

        vm.setNotes("") // the owner deletes the note, and a set lands inside the typing pause
        unheld.logSet(
            sessionId = sessionId,
            exerciseId = FLOOR_LIFT_ID,
            weightKg = FLOOR_KG70,
            reps = 10,
            rpe = null,
            isWarmup = false,
        )
        runUntil(what = "the row with the set on the screen", read = { rowsDelivered.lastOrNull()?.sets?.size }) {
            it == 1
        }
        assertEquals(
            "the deleted note stays deleted in what the screen keeps for a process death",
            "",
            SavedStateWorkoutDraft(handle).sessionNotes(),
        )
        val shown = awaitScreen(vm, "the set on screen") { it.session?.sets?.size == 1 }
        assertEquals("the deleted note stays deleted on screen", "", shown.notes)

        pauseTyping()
        awaitNotesWritesStarted(1)
        assertEquals("the pause writes the deleted note", listOf(""), writesStarted.toList())
        awaitWriteLanded("")
        assertEquals("and the row holds it", "", unheld.getSession(sessionId)?.notes)
    }

    @Test
    fun aLoadThatKeepsTheEntryAndEndsAfterBackSavesTheLastWords() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "seeded")
        // An entry already typed for this lift in this process (switching back, or reopening):
        // its load keeps it, and saves it once more when it ends.
        deps.workoutDraftCache.put(
            WorkoutDraft(
                sessionId = sessionId,
                exerciseId = FLOOR_LIFT_ID,
                weightKg = FLOOR_KG70,
                reps = 10,
                rpe = null,
                isWarmup = false,
                notes = "seeded",
                dirty = true,
            ),
        )
        val handle = handleFor(sessionId)
        val saved = SavedStateWorkoutDraft(handle)
        holdHistory.set(true)
        val vm = viewModel(handle)
        // The first row's save: the screen has read the notes on disk.
        runUntil(what = "the session collector's save of the notes it read", read = saved::sessionNotes) {
            it == "seeded"
        }
        awaitHistoryEntered(vm)
        sessionPaused.value = true // no later row can have the screen save again

        vm.setNotes("leave now")
        vm.persistDraftForExit()
        val atExit = deps.workoutDraftCache.getLift(sessionId, FLOOR_LIFT_ID)
        assertEquals("precondition: Back left the last words in the lift's entry", "leave now", atExit?.notes)
        awaitWriteLanded("leave now")

        releaseHistory.complete(Unit)
        // Nothing else saves now, so a new entry is the load's own save, landing after Back.
        val late = runUntil(
            what = "the load's save of the entry it kept",
            read = { deps.workoutDraftCache.getLift(sessionId, FLOOR_LIFT_ID) },
        ) { it !== atExit }
        assertEquals("the load that kept the entry saves the last words", "leave now", late?.notes)
        assertEquals("the session's notes in the cache", "leave now", deps.workoutDraftCache.sessionNotes(sessionId))
        assertEquals("and in saved state", "leave now", saved.sessionNotes())
    }

    @Test
    fun aLoadThatFailsAfterBackSavesTheLastWords() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "seeded")
        val handle = handleFor(sessionId)
        val saved = SavedStateWorkoutDraft(handle)
        holdHistory.set(true)
        failHeldHistory.set(true)
        val vm = viewModel(handle)
        awaitScreen(vm, "the stored notes on screen") { it.notes == "seeded" }
        assertEquals(
            "the first read's save keeps the notes it read, for a process death",
            "seeded",
            saved.sessionNotes(),
        )
        awaitHistoryEntered(vm)
        sessionPaused.value = true

        vm.setNotes("leave now")
        vm.persistDraftForExit()
        awaitWriteLanded("leave now")

        releaseHistory.complete(Unit)
        // The failed load saves just before it marks the entry degraded, on the same thread.
        awaitScreen(vm, "the entry degraded") { it.liftReadiness == LiftEntryReadiness.DEGRADED }
        assertEquals("the failed load's save keeps the last words for a process death", "leave now", saved.sessionNotes())
        assertEquals("and in the cache", "leave now", deps.workoutDraftCache.sessionNotes(sessionId))
    }

    @Test
    fun aNotesWriteAlreadyUnderwayLandsBeforeTheNextOneStarts() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "seeded")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "the stored notes on screen") { it.notes == "seeded" }

        holdNextWrite.set(true)
        vm.setNotes("a")
        pauseTyping()
        awaitHeldNotesWrite()
        assertTrue("the pause's write is underway, held at the database's door", writeHeld.isCompleted)

        vm.setNotes("ab") // the next keystroke comes while that write is underway
        pauseTyping()
        assertEquals(
            "the next write waits for the one underway to land",
            listOf("a"),
            writesStarted.toList(),
        )

        releaseWrite.complete(Unit)
        awaitWriteLanded("a")
        // SQL landing precedes the read that confirms it. collectLatest cannot start the
        // newer pause until that NonCancellable confirmation and cancellation have ended.
        awaitScreen(vm, "the older write confirmed while the newer words remain pending") {
            it.notes == "ab" && it.notesSave.status == NotesSaveStatus.PENDING && !it.notesSave.busy
        }
        pauseTyping()
        awaitWriteLanded("ab")
        assertEquals(
            "the write underway lands, then the next, in the order typed",
            listOf("a", "ab"),
            writesLanded.toList(),
        )
        assertEquals("the row holds the last words", "ab", unheld.getSession(sessionId)?.notes)
    }

    @Test
    fun anExitFlushWaitsForAnOlderTypingPauseWrite() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "seeded")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "the stored notes on screen") { it.notes == "seeded" }

        holdNextWrite.set(true)
        vm.setNotes("older")
        pauseTyping()
        awaitHeldNotesWrite()
        assertTrue("the older pause is held before its SQL write", writeHeld.isCompleted)
        vm.setNotes("latest")
        vm.persistDraftForExit()
        dispatcher.scheduler.runCurrent()
        assertEquals("the exit flush cannot race the older writer", listOf("older"), writesStarted.toList())

        releaseWrite.complete(Unit)
        awaitWriteLanded("latest")
        assertEquals("the latest exit text wins", "latest", unheld.getSession(sessionId)?.notes)
    }

    @Test
    fun finishingCannotBeOverwrittenByAnOlderHeldNotesWrite() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1))
        unheld.updateSessionNotes(sessionId, "seeded")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "the stored notes and a finishable workout") { it.notes == "seeded" && it.canFinish }

        holdNextWrite.set(true)
        vm.setNotes("older")
        pauseTyping()
        awaitHeldNotesWrite()
        assertTrue("the older notes write is held", writeHeld.isCompleted)
        vm.setNotes("final words")
        vm.finishWorkout()
        dispatcher.scheduler.runCurrent()
        assertEquals("Finish waits outside its use case while an older writer is held", 0, finishesStarted.get())
        assertNull("the held writer has not allowed Finish to commit", unheld.getSession(sessionId)?.finishedAt)
        releaseWrite.complete(Unit)
        runUntil(what = "Finish committed after the older writer completed", read = {
            runBlocking { unheld.getSession(sessionId) }
        }) { it?.finishedAt != null }
        assertEquals(1, finishesStarted.get())
        assertEquals("an older notes write cannot replace final notes", "final words", unheld.getSession(sessionId)?.notes)
        pauseTyping()
        assertEquals("no delayed notes writer follows Finish", listOf("older"), writesStarted.toList())
        assertEquals("the finished row still has the final words", "final words", unheld.getSession(sessionId)?.notes)
    }

    @Test
    fun failedClearKeptOnExitBlocksBarFinishAndRestoresAsEmptyUntilConfirmed() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1))
        unheld.updateSessionNotes(sessionId, "stored note")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "stored note loaded") { it.notes == "stored note" && it.canFinish }
        failNextWrite.set(true)
        vm.setNotes("")
        awaitScreen(vm, "clear failed without losing its empty draft") { it.notesSave.status == NotesSaveStatus.FAILED }
        vm.requestNotesExit(leaveWithDraft = true)
        assertEquals(WorkoutExit.Kept, vm.exitRequested.value)
        assertTrue(deps.workoutDraftCache.hasPendingNotes(sessionId))
        val before = checkNotNull(unheld.getSession(sessionId))
        deps.restTimerController.start(90, sessionId)
        val bar = LiveSessionBarViewModel(ApplicationProvider.getApplicationContext(), deps)
        try {
            bar.uiState.awaitFirst { it?.canFinish == true }
            bar.finishFromBar()
            assertEquals(EndWorkoutCopy.BAR_NOTES_PENDING, bar.actionError.awaitFirst { it != null })
            assertNull(bar.finishedNavigation.value)
            assertEquals(before, unheld.getSession(sessionId))
            assertTrue(deps.restTimerStore.current().running)
            assertEquals("", deps.workoutDraftCache.sessionNotes(sessionId))
            end(vm)

            holdNextWrite.set(true)
            val reopened = viewModel(handleFor(sessionId))
            awaitScreen(reopened, "failed clear restored with actual row") {
                it.canFinish && it.session?.notes == "stored note" && it.notes.isEmpty() &&
                    it.notesSave.status == NotesSaveStatus.SAVING && it.notesSave.busy
            }
            assertEquals("the resumed clear is still held", before, unheld.getSession(sessionId))
            assertTrue(deps.workoutDraftCache.hasPendingNotes(sessionId))
            reopened.persistDraftForExit()
            releaseWrite.complete(Unit)
            awaitScreen(reopened, "clear confirmed after return") {
                it.notesSave.status == NotesSaveStatus.SAVED && it.notesSave.cleared && !it.notesSave.busy
            }
            assertEquals(before.copy(notes = ""), unheld.getSession(sessionId))
            assertFalse("confirmed reopen retires the older settled guard", deps.workoutDraftCache.hasPendingNotes(sessionId))
            bar.finishFromBar()
            assertEquals(sessionId, bar.finishedNavigation.awaitFirst { it != null })
            val finished = checkNotNull(unheld.getSession(sessionId))
            assertEquals("", finished.notes)
            assertEquals(before.sets, finished.sets)
            assertEquals(before.startedAt, finished.startedAt)
            assertTrue(finished.isFinished)
            assertFalse(deps.restTimerStore.current().running)
            assertNull(deps.workoutDraftCache.sessionNotes(sessionId))
        } finally {
            val job = checkNotNull(bar.viewModelScope.coroutineContext[Job])
            job.cancel()
            runUntil(what = "bar coroutines ended", read = { job.isCompleted }) { it }
        }
    }

    @Test
    fun revertingToStoredTextWhileAnOlderWriteIsHeldStillBlocksOutsideFinish() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1))
        unheld.updateSessionNotes(sessionId, "stored")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "stored notes loaded") { it.notes == "stored" && it.canFinish }
        val before = checkNotNull(unheld.getSession(sessionId))
        deps.restTimerController.start(90, sessionId)
        holdNextWrite.set(true)
        vm.setNotes("intermediate")
        pauseTyping()
        awaitHeldNotesWrite()
        assertTrue(writeHeld.isCompleted)
        vm.setNotes("stored")
        assertEquals("stored", deps.workoutDraftCache.sessionNotes(sessionId))
        assertTrue(deps.workoutDraftCache.hasPendingNotes(sessionId))
        assertEquals(com.sinura.personaltrainer.workout.FinishOutcome.NotesPending, deps.finishWorkout(sessionId))
        assertEquals(before, unheld.getSession(sessionId))
        assertTrue(deps.restTimerStore.current().running)
        assertEquals(0, finishesStarted.get())
        releaseWrite.complete(Unit)
        awaitScreen(vm, "older writer confirmed before explicit Finish") {
            !it.notesSave.busy && it.notes == "stored" && it.notesSave.status == NotesSaveStatus.PENDING
        }
        vm.finishWorkout()
        runUntil(what = "explicit Finish used the latest notes", read = { vm.exitRequested.value }) {
            it == WorkoutExit.Finished(sessionId)
        }
        val finished = checkNotNull(unheld.getSession(sessionId))
        assertEquals("stored", finished.notes)
        assertEquals(before.sets, finished.sets)
        assertEquals(before.startedAt, finished.startedAt)
        pauseTyping()
        assertEquals(finished, unheld.getSession(sessionId))
        assertFalse(deps.workoutDraftCache.hasPendingNotes(sessionId))
    }

    @Test
    fun outsideFinishReservesTheEditorBeforeItsSuspendedSessionSnapshot() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1))
        unheld.updateSessionNotes(sessionId, "confirmed")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "confirmed editor before outside Finish") { it.notes == "confirmed" && it.canFinish }
        val before = checkNotNull(unheld.getSession(sessionId))
        deps.restTimerController.start(90, sessionId)
        holdNextSessionRead.set(true)
        val finish = async { deps.finishWorkout(sessionId) }
        runUntil(what = "outside Finish suspended after capturing its session", read = { sessionReadHeld.isCompleted }) { it }
        awaitScreen(vm, "shared Finish reservation is visible") { it.entryLocked && it.mutating }
        vm.setNotes("words during Finish")
        vm.persistDraftForExit()
        vm.requestNotesExit()
        pauseTyping()
        assertEquals("the disabled editor accepted no unrecorded text", "confirmed", vm.uiState.value.notes)
        assertEquals(before, unheld.getSession(sessionId))
        assertEquals(emptyList<String>(), writesStarted.toList())
        assertNull(vm.exitRequested.value)
        assertTrue(deps.restTimerStore.current().running)
        assertEquals(FinishOutcome.InProgress, deps.finishWorkout(sessionId))
        releaseSessionRead.complete(Unit)
        assertEquals(FinishOutcome.Finished(sessionId), finish.await())
        val finished = checkNotNull(unheld.getSession(sessionId))
        assertEquals("confirmed", finished.notes)
        assertEquals(before.sets, finished.sets)
        assertEquals(before.startedAt, finished.startedAt)
        assertTrue(finished.isFinished)
        assertFalse(deps.restTimerStore.current().running)
        assertNull(deps.workoutDraftCache.sessionNotes(sessionId))
        vm.setNotes("late row emission")
        pauseTyping()
        assertEquals(finished, unheld.getSession(sessionId))
        assertNull("a stale owner cannot recreate the completed draft", deps.workoutDraftCache.sessionNotes(sessionId))
        assertEquals(1, finishesStarted.get())
    }

    @Test
    fun outsideFinishKeepsItsReservationThroughThePostCheckSqlSuspension() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1))
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "ready notes editor") { it.canFinish && it.notesSave.status == NotesSaveStatus.SAVED }
        vm.setNotes("latest confirmed")
        vm.persistDraftForExit()
        awaitScreen(vm, "authored text confirmed before Finish") { it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy }
        val before = checkNotNull(unheld.getSession(sessionId))
        holdNextFinishStart.set(true)
        val finish = async { deps.finishWorkout(sessionId) }
        runUntil(what = "Finish passed its guards and suspended at SQL setup", read = { finishStartHeld.isCompleted }) { it }
        awaitScreen(vm, "Finish keeps draft controls locked") { it.entryLocked && it.mutating }
        vm.setNotes("late words")
        vm.retryNotesSave()
        vm.persistDraftForExit()
        pauseTyping()
        assertEquals(before, unheld.getSession(sessionId))
        assertEquals("latest confirmed", vm.uiState.value.notes)
        assertEquals(listOf("latest confirmed"), writesStarted.toList())
        releaseFinishStart.complete(Unit)
        assertEquals(FinishOutcome.Finished(sessionId), finish.await())
        val finished = checkNotNull(unheld.getSession(sessionId))
        assertEquals(before.notes, finished.notes)
        assertEquals(before.sets, finished.sets)
        assertEquals(before.startedAt, finished.startedAt)
        pauseTyping()
        assertEquals(finished, unheld.getSession(sessionId))
        assertEquals(1, finishesStarted.get())
        assertFalse(deps.workoutDraftCache.hasPendingNotes(sessionId))
    }

    @Test
    fun failedOutsideFinishReleasesTheEditorWithoutClearingItsDraft() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1))
        unheld.updateSessionNotes(sessionId, "confirmed")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "confirmed editor") { it.notes == "confirmed" && it.canFinish }
        val before = checkNotNull(unheld.getSession(sessionId))
        holdNextFinishStart.set(true)
        failFinishStart.set(true)
        val finish = async { deps.finishWorkout(sessionId) }
        runUntil(what = "held Finish before simulated SQL failure", read = { finishStartHeld.isCompleted }) { it }
        releaseFinishStart.complete(Unit)
        assertTrue(finish.await() is FinishOutcome.Failed)
        awaitScreen(vm, "failed Finish unlocked editor") { !it.mutating && !it.entryLocked }
        assertEquals(before, unheld.getSession(sessionId))
        assertEquals("confirmed", deps.workoutDraftCache.sessionNotes(sessionId))
        vm.setNotes("recovery words")
        vm.persistDraftForExit()
        awaitScreen(vm, "new words saved after Finish failure") { it.notes == "recovery words" && it.notesSave.status == NotesSaveStatus.SAVED }
        assertEquals(before.copy(notes = "recovery words"), unheld.getSession(sessionId))
        assertEquals(FinishOutcome.Finished(sessionId), deps.finishWorkout(sessionId))
        assertEquals("recovery words", unheld.getSession(sessionId)?.notes)
    }

    @Test
    fun aHeldGuardedExitOwnsItsIntentUntilSavedAndCannotRaceFinishOrDiscard() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1))
        val handle = handleFor(sessionId)
        val saved = SavedStateWorkoutDraft(handle)
        val vm = viewModel(handle)
        awaitScreen(vm, "ready workout with confirmed notes") {
            it.canFinish && it.liftReadiness == LiftEntryReadiness.READY && it.notesSave.status == NotesSaveStatus.SAVED
        }
        val before = checkNotNull(unheld.getSession(sessionId))
        holdNextWrite.set(true)
        vm.setNotes("exit words")
        vm.requestNotesExit()
        runUntil(what = "guarded exit holds the actual notes write", read = { writeHeld.isCompleted }) { it }
        awaitScreen(vm, "exit locks terminal controls") { it.entryLocked && it.mutating }
        vm.finishWorkout()
        vm.discardWorkout()
        vm.requestNotesExit()
        assertEquals(0, finishesStarted.get())
        assertNull(vm.exitRequested.value)
        assertEquals(before, unheld.getSession(sessionId))
        releaseWrite.complete(Unit)
        runUntil(what = "one kept navigation after confirmed notes", read = { vm.exitRequested.value }) { it == WorkoutExit.Kept }
        awaitScreen(vm, "Kept is queued after its write released the mutation guard") {
            vm.exitRequested.value == WorkoutExit.Kept && it.notes == "exit words" &&
                !it.mutating && !it.notesSave.busy && it.notesSave.status == NotesSaveStatus.SAVED
        }
        assertEquals(listOf("exit words"), writesStarted.toList())
        assertEquals(listOf("exit words"), writesLanded.toList())
        assertEquals(before.copy(notes = "exit words"), unheld.getSession(sessionId))
        val cachedDraft = deps.workoutDraftCache.get(sessionId)
        val cachedLifts = deps.workoutDraftCache.all(sessionId)
        val cachedSelection = deps.workoutDraftCache.selectedExerciseId(sessionId)
        val cachedNotes = deps.workoutDraftCache.sessionNotes(sessionId)
        assertEquals("exit words", cachedNotes)
        assertEquals("exit words", saved.sessionNotes())
        vm.setNotes("late IME words before Kept is acknowledged")
        vm.finishWorkout()
        vm.discardWorkout()
        vm.requestNotesExit()
        pauseTyping()
        assertEquals("queued Kept rejects a late text callback", "exit words", vm.uiState.value.notes)
        assertEquals("the late callback cannot replace saved-state words", "exit words", saved.sessionNotes())
        assertEquals(cachedDraft, deps.workoutDraftCache.get(sessionId))
        assertEquals(cachedLifts, deps.workoutDraftCache.all(sessionId))
        assertEquals(cachedSelection, deps.workoutDraftCache.selectedExerciseId(sessionId))
        assertEquals(cachedNotes, deps.workoutDraftCache.sessionNotes(sessionId))
        assertFalse(deps.workoutDraftCache.hasPendingNotes(sessionId))
        assertEquals(listOf("exit words"), writesStarted.toList())
        assertEquals(listOf("exit words"), writesLanded.toList())
        assertEquals(WorkoutExit.Kept, vm.exitRequested.value)
        assertEquals(0, finishesStarted.get())
        assertEquals(before.copy(notes = "exit words"), unheld.getSession(sessionId))
        vm.onExitHandled()
        assertNull(vm.exitRequested.value)
    }

    @Test
    fun aFailedNotesWriteHasItsOwnStateAndCanRetryWithoutAnotherKeystroke() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "seeded")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "the stored notes on screen") { it.notes == "seeded" }

        // A typing pause's write fails.
        failNextWrite.set(true)
        vm.setNotes("abc")
        pauseTyping()
        awaitNotesWritesStarted(1)
        assertEquals("the pause tried to write", listOf("abc"), writesStarted.toList())
        assertEquals("the failed write left the row as it was", "seeded", unheld.getSession(sessionId)?.notes)
        val afterFailure = awaitScreen(vm, "the failed notes, kept with reachable Retry") {
            it.notes == "abc" && it.notesSave.status == NotesSaveStatus.FAILED && it.notesSave.canRetry
        }
        assertNull("a notes failure does not pollute the set error", afterFailure.error)
        vm.retryNotesSave()
        vm.retryNotesSave()
        awaitScreen(vm, "the explicit Retry confirmed the same words") {
            it.notesSave.status == NotesSaveStatus.SAVED
        }
        assertEquals("an explicit double Retry is one write", listOf("abc", "abc"), writesStarted.toList())
        assertEquals("Retry preserved the exact notes", "abc", unheld.getSession(sessionId)?.notes)

        // The next keystroke's pause writes.
        vm.setNotes("abcd")
        pauseTyping()
        awaitNotesWritesStarted(3)
        assertEquals("the next pause tries again", listOf("abc", "abc", "abcd"), writesStarted.toList())
        awaitWriteLanded("abcd")
        awaitScreen(vm, "the next pause is confirmed before the final keystroke") {
            it.notesSave.status == NotesSaveStatus.SAVED && !it.notesSave.busy
        }
        assertEquals("the next pause writes the words", "abcd", unheld.getSession(sessionId)?.notes)

        // A failed pause, then Back with the same words: Back tries them again.
        failNextWrite.set(true)
        vm.setNotes("leave now")
        pauseTyping()
        awaitNotesWritesStarted(4)
        awaitScreen(vm, "the last pause actually failed before Back retries it") {
            it.notesSave.status == NotesSaveStatus.FAILED && it.notesSave.canRetry
        }
        assertEquals("precondition: the pause's write of the last words failed", "abcd", unheld.getSession(sessionId)?.notes)
        vm.persistDraftForExit()
        awaitNotesWritesStarted(5)
        assertEquals(
            "Back tries the words the failed pause could not write",
            listOf("abc", "abc", "abcd", "leave now", "leave now"),
            writesStarted.toList(),
        )
        awaitWriteLanded("leave now")
        assertEquals("and they reach the row", "leave now", unheld.getSession(sessionId)?.notes)
        assertNull("the set error remains separate", vm.uiState.value.error)
    }

    @Test
    fun finishingInsideATypingPauseKeepsTheLastWords() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1))
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "a workout that can be finished") { it.canFinish }

        vm.setNotes("final words")
        vm.finishWorkout() // at once: no typing pause, and no Back
        val finished = unheld.awaitSession(sessionId) { it.finishedAt != null }
        assertEquals("Finish inside a typing pause keeps the last words", "final words", finished.notes)
        assertEquals("Finish carried them itself; no notes write ran", emptyList<String>(), writesStarted.toList())
    }

    @Test
    fun finishingWithTheNoteClearedSavesItCleared() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1))
        unheld.updateSessionNotes(sessionId, "old")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "the stored notes, and a workout that can be finished") { it.notes == "old" && it.canFinish }
        val before = checkNotNull(unheld.getSession(sessionId))
        val clearedAt = dispatcher.scheduler.currentTime
        holdNextWrite.set(true)
        vm.setNotes("") // the owner clears the note, and finishes at once
        awaitHeldNotesWrite()
        vm.finishWorkout()
        vm.finishWorkout()
        dispatcher.scheduler.runCurrent()
        assertNull("Finish waits for the clear instead of navigating early", vm.exitRequested.value)
        assertEquals(before, unheld.getSession(sessionId))
        releaseWrite.complete(Unit)
        runUntil(what = "one Finish after the held clear", read = { vm.exitRequested.value }) {
            it == WorkoutExit.Finished(sessionId)
        }
        val finished = checkNotNull(unheld.getSession(sessionId))
        assertEquals("Finish with the note cleared saves it cleared, not the stored one", "", finished.notes)
        assertEquals(before.sets, finished.sets)
        assertEquals(before.startedAt, finished.startedAt)
        assertEquals("one clear precedes Finish", listOf(""), writesStarted.toList())
        assertEquals(1, finishesStarted.get())
        assertEquals("neither clear nor Finish waits for typing", clearedAt, dispatcher.scheduler.currentTime)
    }

    @Test
    fun aTrailingSpaceTypedIsKeptOnScreenAndInTheDraft() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        val vm = viewModel(handleFor(sessionId))
        runUntil(what = "the session row on the screen", read = { rowsDelivered.size }) { it >= 1 }

        vm.setNotes("felt ") // mid-word: the next key is a letter
        assertEquals("the draft keeps the trailing space", "felt ", deps.workoutDraftCache.sessionNotes(sessionId))
        val shown = awaitScreen(vm, "the typed words on screen") { it.notes.startsWith("felt") }
        assertEquals("the screen keeps the trailing space", "felt ", shown.notes)
    }

    @Test
    fun aTypingPauseAndBackWriteNothingWhenTheWordsAreWhatIsStored() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "stored")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "the stored notes filling the field") { it.notes == "stored" }

        pauseTyping()
        vm.persistDraftForExit()
        dispatcher.scheduler.runCurrent()
        assertEquals(
            "the words are what is stored, so neither a pause nor Back writes them again",
            emptyList<String>(),
            writesStarted.toList(),
        )
    }

    @Test
    fun aRowEditedElsewhereTellsTheScreenWhatIsStored() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "a")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "the stored notes on screen") { it.notes == "a" }

        unheld.updateSessionNotes(sessionId, "b") // edited elsewhere: History, or another screen
        runUntil(
            what = "the row with the notes edited elsewhere on the screen",
            read = { rowsDelivered.lastOrNull()?.session?.notes },
        ) { it == "b" }
        vm.setNotes("b")
        pauseTyping()
        assertEquals(
            "typing what the row now holds writes nothing",
            emptyList<String>(),
            writesStarted.toList(),
        )
    }

    @Test
    fun theFirstWriteComesAtFourHundredMillisecondsNotOneSooner() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "seeded")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "the stored notes on screen") { it.notes == "seeded" }

        val typedAt = dispatcher.scheduler.currentTime
        vm.setNotes("first")
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceTimeBy(399)
        dispatcher.scheduler.runCurrent() // what is due at 399 ms runs too
        assertEquals("no notes write 399 ms after the last key", emptyList<String>(), writesStarted.toList())
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
        awaitNotesWritesStarted(1)
        assertEquals("exactly one notes write at 400 ms, with the words typed", listOf("first"), writesStarted.toList())
        assertEquals("the DAO entry itself was at exactly 400 ms", listOf(typedAt + 400), writesStartedAt.toList())
        awaitWriteLanded("first")
        assertEquals("that deadline's exact words reached Room", "first", unheld.getSession(sessionId)?.notes)
        assertEquals("Room completion required no later typing time", typedAt + 400, dispatcher.scheduler.currentTime)
    }

    @Test
    fun aScreenOpenedOnAWorkoutFinishedElsewhereKeepsTheRestoredWordsForAProcessDeath() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1), withNextLift = true)
        unheld.updateSessionNotes(sessionId, "stored")
        val handle = handleFor(sessionId)
        val saved = SavedStateWorkoutDraft(handle)
        // Android brings the screen back with words saved state kept and no lift selected, and
        // the workout was finished meanwhile (from the bar, or the notification).
        saved.writeSelection(sessionId = sessionId, exerciseId = null, notes = "restored words", editingSetId = null)
        unheld.finishSession(sessionId, "finished elsewhere")
        val vm = viewModel(handle)

        // The finished row selects a lift. A finished workout's draft is never saved again, so
        // what that selection saves is what saved state keeps.
        runUntil(what = "the finished row on the screen", read = { rowsDelivered.size }) { it >= 1 }
        assertEquals("precondition: the row selected the lift", FLOOR_LIFT_ID, saved.selectedExerciseId())
        assertEquals(
            "the lift the finished row selects keeps the restored words in saved state",
            "restored words",
            saved.sessionNotes(),
        )
        val shown = awaitScreen(vm, "the finished row on screen") { it.session != null }
        assertEquals("and on screen", "restored words", shown.notes)
    }

    @Test
    fun aStaleFinishedRouteCanLeaveWithoutNotesWritesBeforeAndAfterTheCompletedCacheIsLost() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1))
        unheld.updateSessionNotes(sessionId, "confirmed finished words")
        val before = checkNotNull(unheld.getSession(sessionId))
        assertEquals(FinishOutcome.Finished(sessionId), deps.finishWorkout(sessionId))
        val finished = checkNotNull(unheld.getSession(sessionId))
        assertTrue(finished.isFinished)
        assertEquals(before.notes, finished.notes)
        assertEquals(before.sets, finished.sets)
        assertEquals(before.startedAt, finished.startedAt)
        assertEquals(1, finishesStarted.get())

        for (clearCompletedCache in listOf(false, true)) {
            val route = if (clearCompletedCache) "cold finished route" else "completed-cache finished route"
            if (clearCompletedCache) deps.workoutDraftCache.clearAll()
            assertEquals(!clearCompletedCache, deps.workoutDraftCache.liveEntryLocked(sessionId))
            val handle = handleFor(sessionId)
            val saved = SavedStateWorkoutDraft(handle)
            val vm = viewModel(handle)
            awaitScreen(vm, "$route has read its actual finished row") {
                it.session == finished && it.notes == finished.notes && !it.notesSave.busy &&
                    it.notesSave.status == NotesSaveStatus.SAVED && it.liftReadiness == LiftEntryReadiness.READY
            }
            val cachedDraft = deps.workoutDraftCache.get(sessionId)
            val cachedLifts = deps.workoutDraftCache.all(sessionId)
            val cachedSelection = deps.workoutDraftCache.selectedExerciseId(sessionId)
            val cachedNotes = deps.workoutDraftCache.sessionNotes(sessionId)
            val savedNotes = saved.sessionNotes()
            if (!clearCompletedCache) {
                assertNull(cachedDraft)
                assertTrue(cachedLifts.isEmpty())
                assertNull(cachedSelection)
                assertNull(cachedNotes)
            }
            assertFalse(deps.workoutDraftCache.hasPendingNotes(sessionId))
            vm.setNotes("a stale finished editor callback")
            dispatcher.scheduler.runCurrent()
            assertEquals("$route rejects edits before Back", finished.notes, vm.uiState.value.notes)
            vm.requestNotesExit()
            runUntil(what = "$route queues truthful Back", read = { vm.exitRequested.value }) { it == WorkoutExit.Kept }
            vm.setNotes("a late callback while finished Back is queued")
            pauseTyping()
            assertEquals("$route keeps its stored words", finished.notes, vm.uiState.value.notes)
            assertEquals("$route leaves saved state unchanged", savedNotes, saved.sessionNotes())
            assertEquals("$route leaves the complete Room row unchanged", finished, unheld.getSession(sessionId))
            assertEquals("$route performs no notes DAO entry", emptyList<String>(), writesStarted.toList())
            assertEquals("$route lands no notes write", emptyList<String>(), writesLanded.toList())
            assertEquals(cachedDraft, deps.workoutDraftCache.get(sessionId))
            assertEquals(cachedLifts, deps.workoutDraftCache.all(sessionId))
            assertEquals(cachedSelection, deps.workoutDraftCache.selectedExerciseId(sessionId))
            assertEquals(cachedNotes, deps.workoutDraftCache.sessionNotes(sessionId))
            assertFalse(deps.workoutDraftCache.hasPendingNotes(sessionId))
            assertFalse(vm.notesExitBlocked.value)
            assertEquals(WorkoutExit.Kept, vm.exitRequested.value)
            assertEquals(1, finishesStarted.get())
            vm.onExitHandled()
            assertNull(vm.exitRequested.value)
            end(vm)
        }
    }

    @Test
    fun spacesTypedBeforeTheRowIsReadAreNotReplacedByTheStoredNotes() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "stored")
        val handle = handleFor(sessionId)
        sessionPaused.value = true // the row is held
        val vm = viewModel(handle)

        vm.setNotes("  ")
        sessionPaused.value = false
        runUntil(what = "the session row on the screen", read = { rowsDelivered.size }) { it >= 1 }
        assertEquals(
            "a field of spaces is not empty: the row does not fill it, in what the screen keeps",
            "  ",
            SavedStateWorkoutDraft(handle).sessionNotes(),
        )
        assertEquals("nor on screen", "  ", awaitScreen(vm, "the row on screen") { it.session != null }.notes)
    }

    private fun handleFor(sessionId: String) = SavedStateHandle(mapOf("sessionId" to sessionId))

    private fun viewModel(handle: SavedStateHandle) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = handle,
        container = deps,
        undoTimeout = UndoTimeoutProvider { it.toLong() },
    ).also(viewModels::add)

    /**
     * The screen ends: its scope is cancelled, and what is still on its way to [testThread] (a row,
     * a notes write that cannot be cancelled) runs out here rather than leaving a join waiting on a
     * scheduler no one runs.
     */
    private suspend fun end(vm: ActiveWorkoutViewModel) {
        val job = checkNotNull(vm.viewModelScope.coroutineContext[Job]) { "the screen has no job" }
        job.cancel()
        runUntil(what = "the screen's coroutines to end", read = { job.isCompleted }) { it }
        viewModels.remove(vm)
    }

    /** A typing pause: 1 ms past the notes debounce on the screen's clock, and what is due runs. */
    private fun pauseTyping() {
        dispatcher.scheduler.advanceTimeBy(TYPING_PAUSE_MS)
        dispatcher.scheduler.runCurrent()
    }

    /** Room's live-owner read precedes DAO entry; wait for it without moving typing time. */
    private suspend fun awaitNotesWritesStarted(count: Int) {
        val frozenAt = dispatcher.scheduler.currentTime
        runUntil(what = "$count notes writes reach the DAO at virtual time $frozenAt", read = { writesStarted.toList() }) {
            it.size >= count
        }
        assertEquals("waiting for actual DAO entry advances no typing time", frozenAt, dispatcher.scheduler.currentTime)
    }

    private suspend fun awaitHeldNotesWrite() {
        val frozenAt = dispatcher.scheduler.currentTime
        runUntil(what = "the actual notes DAO entry is held at virtual time $frozenAt", read = { writeHeld.isCompleted }) { it }
        assertEquals("waiting for the held DAO entry advances no typing time", frozenAt, dispatcher.scheduler.currentTime)
    }

    /** Runs what waits for the test thread until the screen shows what [shows] accepts; bounded. */
    private suspend fun awaitScreen(
        vm: ActiveWorkoutViewModel,
        what: String,
        shows: (ActiveWorkoutUiState) -> Boolean,
    ): ActiveWorkoutUiState = runUntil(what = what, read = { vm.uiState.value }, done = shows)

    /** Runs the notes writes' answers until [notes] has landed; the screen has taken that answer when this returns. */
    private suspend fun awaitWriteLanded(notes: String) {
        runUntil(
            what = "the notes write of \"$notes\" landing (writes started: $writesStarted)",
            read = { writesLanded.toList() },
        ) { notes in it }
    }

    private suspend fun awaitHistoryEntered(vm: ActiveWorkoutViewModel) {
        try {
            withTimeout(TestWaits.FLOW_MS) { historyEntered.await() }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError(
                "the prefill never reached its first history read; uiState was ${vm.uiState.value}",
                timedOut,
            )
        }
    }

    /** [pollUntil], running what was sent to [testThread] before each read. */
    private suspend fun <T> runUntil(what: String, read: () -> T, done: (T) -> Boolean): T =
        pollUntil(
            what = what,
            read = {
                dispatcher.scheduler.runCurrent()
                read()
            },
            done = done,
        )

    /** [read] until [done] accepts it, bounded; on a timeout, says [what] never came and what was read last. */
    private suspend fun <T> pollUntil(what: String, read: () -> T, done: (T) -> Boolean): T {
        var last = read()
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (!done(last)) {
                    delay(10)
                    last = read()
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("$what never came within ${TestWaits.FLOW_MS} ms; last read: $last", timedOut)
        }
        return last
    }

    /**
     * The real DAO with doors a test can hold, each row and each notes write's answer sent to
     * [testThread], and a record of the notes writes.
     */
    private inner class Doors(private val real: WorkoutDao) : WorkoutDao by real {
        override suspend fun getSession(id: String): SessionWithDetails? {
            val row = real.getSession(id)
            if (holdNextSessionRead.compareAndSet(true, false)) {
                sessionReadHeld.complete(Unit)
                releaseSessionRead.await()
            }
            return row
        }

        override suspend fun sessionStartedAt(id: String): Long? {
            if (holdNextFinishStart.compareAndSet(true, false)) {
                finishStartHeld.complete(Unit)
                releaseFinishStart.await()
                if (failFinishStart.compareAndSet(true, false)) throw SQLiteFullException("finish setup failed (test)")
            }
            return real.sessionStartedAt(id)
        }

        override suspend fun finishSession(id: String, notes: String, durationMinutes: Int, finishedAt: Long): Int {
            finishesStarted.incrementAndGet()
            return real.finishSession(id, notes, durationMinutes, finishedAt)
        }

        override suspend fun finishedWorkingSetsForExercises(
            exerciseIds: List<String>,
        ): List<FinishedWorkingSetRow> {
            if (holdHistory.compareAndSet(true, false)) {
                historyEntered.complete(Unit)
                releaseHistory.await()
                check(!failHeldHistory.get()) { "history unavailable (test)" }
            }
            return real.finishedWorkingSetsForExercises(exerciseIds)
        }

        override fun observeSession(id: String): Flow<SessionWithDetails?> =
            real.observeSession(id).onEach { row ->
                sessionPaused.first { paused -> !paused }
                withContext(testThread) { rowsDelivered.add(row) }
            }

        override suspend fun updateSessionNotes(id: String, notes: String) {
            writesStartedAt.add(dispatcher.scheduler.currentTime)
            writesStarted.add(notes)
            if (failNextWrite.compareAndSet(true, false)) {
                throw SQLiteFullException("database or disk is full (test)")
            }
            if (holdNextWrite.compareAndSet(true, false)) {
                writeHeld.complete(Unit)
                releaseWrite.await()
            }
            withContext(testThread) {
                real.updateSessionNotes(id, notes)
                writesLanded.add(notes)
            }
        }
    }

    private companion object {
        /** One millisecond past the screen's 400 ms notes debounce. */
        const val TYPING_PAUSE_MS = 401L
    }
}
