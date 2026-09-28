package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import java.util.concurrent.CopyOnWriteArrayList
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A note deleted just before Android stops the app stays deleted when the screen comes back (N2).
 *
 * Every keystroke is saved with the draft, and saved state is what survives the process. It keeps
 * the notes under one key, which read a deletion ("") and nothing saved alike, and the rebuilt
 * screen restored only a note with words in it. So a note deleted inside the 400 ms typing pause
 * came back empty, the row filled the empty field with the old note, and the deletion was lost
 * (R1). Restoring the empty note as a deletion needs the draft to never hold an empty note that is
 * not one: a workout finished elsewhere used to save the field before the row filled it, and its
 * finished notes would then be written over after a process death (R2). The rest holds what must not
 * change: nothing saved still lets the row fill the field (G2), typed words still come back and are
 * written (G3), and the empty note Back stages while the row is still loading is not a deletion
 * (G4). And, from the reviews: a restored screen writes nothing until its row is read, even when the
 * words differ from what was restored (G5), and restored words deleted before the row arrives stay
 * deleted (B4).
 *
 * A process death is the screen's end with no flush, then the draft cache cleared; the screen is
 * rebuilt on the same SavedStateHandle, over the same database. A reopen from Home is a new screen on
 * a fresh handle, the cache as it was.
 *
 * Built as FloorSessionNotesMoveCharacterisationTest builds it (real in-memory Room, a real
 * SavedStateHandle, an unconfined Main), so that no wait rests on how fast another thread happens to
 * be: a session row, and the answer to a notes write, reach the screen only on the test's own thread,
 * when the test runs the scheduler ([testThread]). What they resume then runs inline, so when a wait
 * that runs them returns, the screen has handled what it waited for. The database is seeded and read
 * past the doors ([unheld]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DeletedNoteStaysDeletedTest {
    private lateinit var dispatcher: TestDispatcher

    /** The test's own thread: what is sent here runs only when the test runs the scheduler. */
    private lateinit var testThread: TestDispatcher
    private lateinit var deps: FakeAppDependencies
    private lateinit var unheld: WorkoutRepository
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    /** While true, each session row waits at the door before the screen hears of it. */
    private val sessionPaused = MutableStateFlow(false)

    /** Every session row handed to the screen, in order. */
    private val rowsDelivered = CopyOnWriteArrayList<SessionWithDetails?>()

    /** Every notes write that reached the DAO, in order. */
    private val writesStarted = CopyOnWriteArrayList<String>()

    /** Every notes write the database took, in order, as its answer reached the screen. */
    private val writesLanded = CopyOnWriteArrayList<String>()

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
        sessionPaused.value = false
        if (::dispatcher.isInitialized) runBlocking { viewModels.toList().forEach { end(it) } }
        viewModels.clear()
        if (::deps.isInitialized) deps.restTimerController.stop()
        if (::dispatcher.isInitialized) dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    /** R1. */
    @Test
    fun aNoteDeletedJustBeforeAProcessDeathStaysDeleted() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, OLD_NOTE)
        val handle = handleFor(sessionId)
        val saved = SavedStateWorkoutDraft(handle)
        val vm = viewModel(handle)
        awaitScreen(vm, "the stored note on screen") { it.notes == OLD_NOTE }

        vm.setNotes("") // the owner deletes the whole note, and Android stops the app inside the typing pause
        assertEquals("precondition: saved state keeps the deletion", "", saved.sessionNotesIfSaved())
        processDeath(vm)
        assertEquals("precondition: the deletion never reached the database", emptyList<String>(), writesStarted.toList())
        assertEquals("precondition: the database still holds the old note", OLD_NOTE, unheld.getSession(sessionId)?.notes)

        val rowMark = rowsDelivered.size
        val revived = viewModel(handle)
        runUntil(what = "the revived screen reading the row", read = { rowsDelivered.size }) { it > rowMark }
        assertEquals(
            "the deleted note stays deleted in saved state once the row is read, not refilled with the old note",
            "",
            saved.sessionNotesIfSaved(),
        )
        val shown = awaitScreen(revived, "the row on the revived screen") { it.session != null }
        assertEquals("the deleted note stays deleted on screen", "", shown.notes)

        pauseTyping()
        assertEquals("the next pause writes the deletion, once", listOf(""), writesStarted.toList())
        awaitWriteLanded("")
        assertEquals("and the database holds it", "", unheld.getSession(sessionId)?.notes)
    }

    /** R2. */
    @Test
    fun aWorkoutFinishedElsewhereKeepsItsNotesThroughAProcessDeath() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = floorSets(1))
        unheld.finishSession(sessionId, FINISHED_NOTES)
        val handle = handleFor(sessionId)
        val saved = SavedStateWorkoutDraft(handle)
        // Opened from History, or a notification, on a workout finished from the bar: a fresh handle.
        val vm = viewModel(handle)

        runUntil(what = "the finished row on the screen", read = { rowsDelivered.size }) { it >= 1 }
        assertEquals("precondition: the finished row selected the lift", FLOOR_LIFT_ID, saved.selectedExerciseId())
        assertEquals(
            "saved state holds the finished notes, not the empty field the selection saved before the row filled it",
            FINISHED_NOTES,
            saved.sessionNotes(),
        )
        awaitScreen(vm, "the finished notes on screen") { it.session != null && it.notes == FINISHED_NOTES }

        processDeath(vm)
        val rowMark = rowsDelivered.size
        val revived = viewModel(handle)
        runUntil(what = "the revived screen reading the row", read = { rowsDelivered.size }) { it > rowMark }
        assertEquals(
            "the revived screen shows the finished notes",
            FINISHED_NOTES,
            awaitScreen(revived, "the row on the revived screen") { it.session != null }.notes,
        )
        pauseTyping()
        assertEquals("nothing is written over the finished notes", emptyList<String>(), writesStarted.toList())
        assertEquals("the database keeps them", FINISHED_NOTES, unheld.getSession(sessionId)?.notes)
    }

    /** G2. */
    @Test
    fun aProcessDeathBeforeAnythingIsSavedLetsTheRowFillTheNote() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, OLD_NOTE)
        val handle = handleFor(sessionId)
        sessionPaused.value = true // the first read is slow, and Android stops the app before it lands
        val vm = viewModel(handle)
        processDeath(vm)
        assertTrue(
            "precondition: nothing of the draft was saved; keys=${handle.keys()}",
            handle.keys().none { it.startsWith(DRAFT_KEY_PREFIX) },
        )

        sessionPaused.value = false
        val rowMark = rowsDelivered.size
        val revived = viewModel(handle)
        runUntil(what = "the revived screen reading the row", read = { rowsDelivered.size }) { it > rowMark }
        assertEquals("with nothing saved, the row fills the field, as saved state keeps it", OLD_NOTE, SavedStateWorkoutDraft(handle).sessionNotes())
        awaitScreen(revived, "the row's note on the revived screen") { it.session != null && it.notes == OLD_NOTE }
        pauseTyping()
        assertEquals("nothing is written", emptyList<String>(), writesStarted.toList())
        assertEquals("the database keeps the note", OLD_NOTE, unheld.getSession(sessionId)?.notes)
    }

    /** G3. */
    @Test
    fun wordsTypedJustBeforeAProcessDeathComeBackAndAreWritten() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, OLD_NOTE)
        val handle = handleFor(sessionId)
        val vm = viewModel(handle)
        awaitScreen(vm, "the stored note on screen") { it.notes == OLD_NOTE }

        vm.setNotes(NEW_WORDS) // typed, and Android stops the app inside the typing pause
        processDeath(vm)
        assertEquals("precondition: the words never reached the database", OLD_NOTE, unheld.getSession(sessionId)?.notes)

        val rowMark = rowsDelivered.size
        val revived = viewModel(handle)
        runUntil(what = "the revived screen reading the row", read = { rowsDelivered.size }) { it > rowMark }
        assertEquals("saved state keeps the typed words", NEW_WORDS, SavedStateWorkoutDraft(handle).sessionNotes())
        assertEquals(
            "the typed words come back on screen",
            NEW_WORDS,
            awaitScreen(revived, "the row on the revived screen") { it.session != null }.notes,
        )
        pauseTyping()
        assertEquals("the next pause writes them, once", listOf(NEW_WORDS), writesStarted.toList())
        awaitWriteLanded(NEW_WORDS)
        assertEquals("and the database holds them", NEW_WORDS, unheld.getSession(sessionId)?.notes)
    }

    /** G4. */
    @Test
    fun backWhileTheFirstRowLoadsThenAReopenKeepsTheNote() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, OLD_NOTE)
        sessionPaused.value = true // the first read is slow
        val vm = viewModel(handleFor(sessionId))
        vm.persistDraftForExit() // the header's Back, before the row has filled the field
        end(vm)
        assertEquals(
            "precondition: Back staged the field, still empty, for the session",
            "",
            deps.workoutDraftCache.sessionNotes(sessionId),
        )

        sessionPaused.value = false
        val rowMark = rowsDelivered.size
        val reopenedHandle = handleFor(sessionId) // reopened from Home: a fresh handle, the same cache
        val reopened = viewModel(reopenedHandle)
        runUntil(what = "the reopened screen reading the row", read = { rowsDelivered.size }) { it > rowMark }
        assertEquals(
            "the note Back never showed is kept: the row fills the field, as saved state keeps it",
            OLD_NOTE,
            SavedStateWorkoutDraft(reopenedHandle).sessionNotes(),
        )
        awaitScreen(reopened, "the row's note on the reopened screen") { it.session != null && it.notes == OLD_NOTE }
        pauseTyping()
        assertEquals("nothing is written over the note", emptyList<String>(), writesStarted.toList())
        assertEquals("the database keeps it", OLD_NOTE, unheld.getSession(sessionId)?.notes)
    }

    /** G5 (review): words typed onto a restored deletion before the row is read wait for it, then Back writes them. */
    @Test
    fun wordsTypedOntoARestoredDeletionWaitForTheRowThenBackWritesThem() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, OLD_NOTE)
        val handle = handleFor(sessionId)
        val vm = viewModel(handle)
        awaitScreen(vm, "the stored note on screen") { it.notes == OLD_NOTE }
        vm.setNotes("") // deleted, and Android stops the app inside the typing pause
        processDeath(vm)

        sessionPaused.value = true // the revived screen's first read is slow
        val rowMark = rowsDelivered.size
        val revived = viewModel(handle)
        revived.setNotes(TYPED_BEFORE_THE_ROW)
        pauseTyping()
        assertEquals(
            "no notes write while the row is unread, though the restored note differs from the words",
            emptyList<String>(),
            writesStarted.toList(),
        )

        sessionPaused.value = false
        runUntil(what = "the revived screen reading the row", read = { rowsDelivered.size }) { it > rowMark }
        revived.persistDraftForExit() // Back: the pause writer waits for the next change, Back does not
        awaitWriteLanded(TYPED_BEFORE_THE_ROW)
        assertEquals("Back writes the words typed before the row, once", listOf(TYPED_BEFORE_THE_ROW), writesStarted.toList())
        assertEquals("and the database holds them", TYPED_BEFORE_THE_ROW, unheld.getSession(sessionId)?.notes)
    }

    /** B4 (review, s43): restored words deleted before the revived row arrives stay deleted. */
    @Test
    fun restoredWordsDeletedBeforeTheRowArrivesStayDeleted() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        unheld.updateSessionNotes(sessionId, OLD_NOTE)
        val handle = handleFor(sessionId)
        val vm = viewModel(handle)
        awaitScreen(vm, "the stored note on screen") { it.notes == OLD_NOTE }
        vm.setNotes(NEW_WORDS) // typed, and Android stops the app inside the typing pause
        processDeath(vm)

        sessionPaused.value = true // the revived screen's first read is slow
        val rowMark = rowsDelivered.size
        val revived = viewModel(handle)
        revived.setNotes("") // the restored words deleted before the row arrives
        pauseTyping()
        assertEquals("no notes write while the row is unread", emptyList<String>(), writesStarted.toList())

        sessionPaused.value = false
        runUntil(what = "the revived screen reading the row", read = { rowsDelivered.size }) { it > rowMark }
        assertEquals(
            "the field stays empty once the row is read, not refilled with the stored note, in saved state",
            "",
            SavedStateWorkoutDraft(handle).sessionNotesIfSaved(),
        )
        assertEquals("and on screen", "", awaitScreen(revived, "the row on the revived screen") { it.session != null }.notes)
        revived.persistDraftForExit() // Back
        awaitWriteLanded("")
        assertEquals("Back writes the deletion, once", listOf(""), writesStarted.toList())
        assertEquals("and the database holds it", "", unheld.getSession(sessionId)?.notes)
    }

    private fun handleFor(sessionId: String) = SavedStateHandle(mapOf("sessionId" to sessionId))

    private fun viewModel(handle: SavedStateHandle) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = handle,
        container = deps,
        undoTimeout = UndoTimeoutProvider { it.toLong() },
    ).also(viewModels::add)

    /** Android stops the app: the screen ends with no flush, and the process's draft cache goes with it. */
    private suspend fun processDeath(vm: ActiveWorkoutViewModel) {
        end(vm)
        deps.workoutDraftCache.clearAll()
    }

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
     * The real DAO with a door each row waits at while [sessionPaused] is true, each row and each
     * notes write's answer sent to [testThread], and a record of the notes writes.
     */
    private inner class Doors(private val real: WorkoutDao) : WorkoutDao by real {
        override fun observeSession(id: String): Flow<SessionWithDetails?> =
            real.observeSession(id).onEach { row ->
                sessionPaused.first { paused -> !paused }
                withContext(testThread) { rowsDelivered.add(row) }
            }

        override suspend fun updateSessionNotes(id: String, notes: String) {
            writesStarted.add(notes)
            withContext(testThread) {
                real.updateSessionNotes(id, notes)
                writesLanded.add(notes)
            }
        }
    }

    private companion object {
        const val OLD_NOTE = "left knee sore"
        const val NEW_WORDS = "left knee sore, iced it"
        const val FINISHED_NOTES = "finished from the bar"
        const val TYPED_BEFORE_THE_ROW = "typed before the row"

        /** SavedStateWorkoutDraft keeps every key under this prefix. */
        const val DRAFT_KEY_PREFIX = "draft."

        /** One millisecond past the screen's 400 ms notes debounce. */
        const val TYPING_PAUSE_MS = 401L
    }
}
