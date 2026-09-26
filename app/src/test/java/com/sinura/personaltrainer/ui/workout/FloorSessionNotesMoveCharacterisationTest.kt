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
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutDraft
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
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
 * is tried again, and shows no error; Finish inside a typing pause keeps the last words. The first
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

    /** Every notes write the database took, in order, as its answer reached the screen. */
    private val writesLanded = CopyOnWriteArrayList<String>()

    /** Armed, the next notes write says so on [writeHeld] and waits for [releaseWrite] before it writes. */
    private val holdNextWrite = AtomicBoolean(false)
    private val writeHeld = CompletableDeferred<Unit>()
    private val releaseWrite = CompletableDeferred<Unit>()

    /** Armed, the next notes write fails as a full disk fails it. */
    private val failNextWrite = AtomicBoolean(false)

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
        assertEquals("the pause writes the words", listOf("abc"), writesStarted.toList())
        // Its answer runs on this thread: the screen knows "abc" is stored before the next key.
        awaitWriteLanded("abc")

        vm.setNotes("") // the owner clears the note
        pauseTyping()
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
    fun aFailedNotesWriteIsTriedAgainWithNoErrorShown() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, "seeded")
        val vm = viewModel(handleFor(sessionId))
        awaitScreen(vm, "the stored notes on screen") { it.notes == "seeded" }

        // A typing pause's write fails.
        failNextWrite.set(true)
        vm.setNotes("abc")
        pauseTyping()
        assertEquals("the pause tried to write", listOf("abc"), writesStarted.toList())
        assertEquals("the failed write left the row as it was", "seeded", unheld.getSession(sessionId)?.notes)
        val afterFailure = awaitScreen(vm, "the words on screen") { it.notes == "abc" }
        assertNull("a notes write that fails shows no error", afterFailure.error)

        // The next keystroke's pause writes.
        vm.setNotes("abcd")
        pauseTyping()
        assertEquals("the next pause tries again", listOf("abc", "abcd"), writesStarted.toList())
        awaitWriteLanded("abcd")
        assertEquals("the next pause writes the words", "abcd", unheld.getSession(sessionId)?.notes)

        // A failed pause, then Back with the same words: Back tries them again.
        failNextWrite.set(true)
        vm.setNotes("leave now")
        pauseTyping()
        assertEquals("precondition: the pause's write of the last words failed", "abcd", unheld.getSession(sessionId)?.notes)
        vm.persistDraftForExit()
        assertEquals(
            "Back tries the words the failed pause could not write",
            listOf("abc", "abcd", "leave now", "leave now"),
            writesStarted.toList(),
        )
        awaitWriteLanded("leave now")
        assertEquals("and they reach the row", "leave now", unheld.getSession(sessionId)?.notes)
        assertNull("still no error", vm.uiState.value.error)
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

        vm.setNotes("") // the owner clears the note, and finishes at once
        vm.finishWorkout()
        val finished = unheld.awaitSession(sessionId) { it.finishedAt != null }
        assertEquals("Finish with the note cleared saves it cleared, not the stored one", "", finished.notes)
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

        vm.setNotes("first")
        dispatcher.scheduler.advanceTimeBy(399)
        dispatcher.scheduler.runCurrent() // what is due at 399 ms runs too
        assertEquals("no notes write 399 ms after the last key", emptyList<String>(), writesStarted.toList())
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
        assertEquals("exactly one notes write at 400 ms, with the words typed", listOf("first"), writesStarted.toList())
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
