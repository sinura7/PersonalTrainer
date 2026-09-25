package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.UndoHostCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.workout.FloorUndo
import com.sinura.personaltrainer.workout.SavedStateFloorUndo
import com.sinura.personaltrainer.workout.UndoEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The floor's undo queue, held on the ViewModel's public face before W2d-1 moves the queue out
 * of ActiveWorkoutViewModel.kt: an undone or expired offer stays gone after a process death, the
 * queue keeps five offers, a second tap is dropped while a delete or a save is still outstanding,
 * a revived floor's next offer asks the platform for its dwell again while the offer revealed
 * underneath keeps the dwell it was promised, an expiry with nothing offered saves nothing,
 * every offer gets its own key, a bodyweight set is named the bodyweight way, and a restore that
 * fails keeps its offer.
 *
 * Each of these was a gap: taking out the line that does it failed no test. The queue's
 * survival of a process death and its dwell values are already held by
 * ActiveWorkoutViewModelTest and FloorVmContractTest; its order is asserted again here only
 * so that stacking the wrong way round fails with its own message.
 *
 * Built as FloorVmContractTest builds it: real in-memory Room, a real SavedStateHandle, the
 * ViewModel's own scope inline on the test thread, and a process death that ends the ViewModel
 * and empties the in-memory draft cache before a new ViewModel reads the same handle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FloorUndoCharacterisationTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    /** When set, a set's delete waits here inside its write: a delete the lifter is still waiting on. */
    @Volatile private var deleteGate: CompletableDeferred<Unit>? = null

    /** While true, every set written to the database fails, a log's and an undo's alike. */
    @Volatile private var failWrites = false

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { real -> HeldWrites(real) },
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        deleteGate?.complete(Unit)
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun anUndoneOfferDoesNotComeBackAfterProcessDeath() = runBlocking {
        val sessionId = seedSquat(loggedSets = listOf(SET_100x5))
        val handle = handleFor(sessionId)
        val vm = viewModel(sessionId, handle)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 1 }
        val logged = storedSession(sessionId).sets.single()
        vm.awaitEntryUnlocked()
        vm.deleteSet(logged.id)
        vm.awaitOfferFor(logged.id)
        deps.workoutRepository.awaitSession(sessionId) { it.sets.isEmpty() }
        vm.undoTopOffer()
        deps.workoutRepository.awaitSession(sessionId) { it.sets.size == 1 }
        vm.undoEntries.awaitFirst { it.isEmpty() }
        vm.awaitEntryUnlocked()

        die(vm)
        assertTrue(
            "the saved state holds no offer once it was undone",
            SavedStateFloorUndo(handle).read().isEmpty(),
        )
        val revived = viewModel(sessionId, handle)
        revived.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 1 }
        assertTrue(
            "the revived floor offers nothing: the undone delete does not come back",
            revived.undoEntries.value.isEmpty(),
        )
    }

    @Test
    fun anExpiredOfferDoesNotComeBackAfterProcessDeath() = runBlocking {
        val sessionId = seedSquat(loggedSets = listOf(SET_100x5, SET_100x5))
        val handle = handleFor(sessionId)
        val vm = viewModel(sessionId, handle)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 2 }
        val (older, newer) = storedSession(sessionId).sets
        vm.awaitEntryUnlocked()
        vm.deleteSet(older.id)
        vm.awaitOfferFor(older.id)
        vm.deleteSet(newer.id)
        vm.undoEntries.awaitFirst { it.size == 2 }
        vm.awaitEntryUnlocked()
        assertEquals(
            "offers stack oldest first, newest last: the banner reads the last",
            listOf(older.id, newer.id),
            vm.undoEntries.value.setIds(),
        )
        deps.workoutRepository.awaitSession(sessionId) { it.sets.isEmpty() }

        // The banner timed out on the newest offer.
        vm.onUndoOfferExpired()
        assertEquals("expiry takes the newest offer and leaves the older one", listOf(older.id), vm.undoEntries.value.setIds())
        assertTrue("expiry puts nothing back", storedSession(sessionId).sets.isEmpty())

        die(vm)
        val revived = viewModel(sessionId, handle)
        revived.awaitState { it.loadState == SessionLoadState.FOUND }
        assertEquals(
            "the revived floor offers the older delete alone: the expired one does not come back",
            listOf(older.id),
            revived.undoEntries.value.setIds(),
        )
    }

    @Test
    fun theOfferRevealedUnderneathKeepsTheDwellItWasPromised() = runBlocking {
        val sessionId = seedSquat(loggedSets = listOf(SET_100x5, SET_100x5))
        val handle = handleFor(sessionId)
        // TalkBack asks for twenty seconds, and both offers are promised that long.
        val vm = viewModel(sessionId = sessionId, handle = handle, undoTimeout = UndoTimeoutProvider { TALKBACK_DWELL_MS })
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 2 }
        val (older, newer) = storedSession(sessionId).sets
        vm.awaitEntryUnlocked()
        vm.deleteSet(older.id)
        vm.awaitOfferFor(older.id)
        vm.deleteSet(newer.id)
        vm.awaitOfferFor(newer.id)

        // The process comes back on a platform that would give only the base dwell.
        die(vm)
        val revived = viewModel(sessionId = sessionId, handle = handle, undoTimeout = UndoTimeoutProvider { it.toLong() })
        revived.awaitState { it.loadState == SessionLoadState.FOUND }
        assertEquals("both offers come back with the process", listOf(older.id, newer.id), revived.undoEntries.value.setIds())

        // The banner timed out on the newest offer; the one underneath is revealed.
        revived.onUndoOfferExpired()
        assertEquals("expiry revealed the older offer", listOf(older.id), revived.undoEntries.value.setIds())
        assertEquals(
            "the offer revealed underneath keeps the dwell it was promised",
            TALKBACK_DWELL_MS,
            revived.undoDwellMs.value,
        )
        die(revived)
        assertEquals(
            "the saved state keeps that dwell for the offer underneath",
            TALKBACK_DWELL_MS,
            SavedStateFloorUndo(handle).readDwellMs(),
        )
    }

    @Test
    fun expiringWithNothingOfferedSavesNothing() = runBlocking {
        val sessionId = seedSquat(loggedSets = emptyList())
        val handle = handleFor(sessionId)
        val vm = viewModel(sessionId, handle)
        vm.awaitState { it.loadState == SessionLoadState.FOUND }
        assertTrue("a fresh floor offers nothing", vm.undoEntries.value.isEmpty())

        vm.onUndoOfferExpired()
        assertTrue("expiry with nothing offered leaves nothing offered", vm.undoEntries.value.isEmpty())
        // Read the handle once nothing else writes to it: the ViewModel's draft mirrors share it.
        die(vm)
        assertTrue(
            "expiry with nothing offered saves nothing: no undo key is in the saved state",
            handle.keys().none { it.startsWith("floorUndo.") },
        )
    }

    @Test
    fun aSixthDeleteDropsTheOldestOfferHereAndInSavedState() = runBlocking {
        val sessionId = seedSquat(loggedSets = List(6) { SET_100x5 })
        val handle = handleFor(sessionId)
        val vm = viewModel(sessionId, handle)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 6 }
        val deleted = storedSession(sessionId).sets.map { it.id }
        vm.awaitEntryUnlocked()
        deleted.forEach { id ->
            vm.deleteSet(id)
            vm.awaitOfferFor(id)
        }
        deps.workoutRepository.awaitSession(sessionId) { it.sets.isEmpty() }

        assertEquals(
            "five offers stand, newest last, and the first delete's offer is the one dropped",
            deleted.drop(1),
            vm.undoEntries.value.setIds(),
        )
        die(vm)
        assertEquals(
            "the saved state holds the same five offers",
            deleted.drop(1),
            SavedStateFloorUndo(handle).read().setIds(),
        )
    }

    @Test
    fun aSecondDeleteTappedWhileTheFirstIsStillBeingWrittenIsDropped() = runBlocking {
        val sessionId = seedSquat(loggedSets = listOf(SET_100x5, SET_100x5))
        val vm = viewModel(sessionId)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 2 }
        val (older, newer) = storedSession(sessionId).sets
        vm.awaitEntryUnlocked()
        // The first delete parks inside its write, as a slow disk would hold it.
        val gate = CompletableDeferred<Unit>().also { deleteGate = it }
        vm.deleteSet(older.id)
        vm.awaitState { it.entryLocked }
        vm.deleteSet(newer.id)
        deleteGate = null
        gate.complete(Unit)
        vm.awaitOfferFor(older.id)
        deps.workoutRepository.awaitSession(sessionId) { it.sets.size == 1 }

        assertEquals(
            "the second delete, tapped while the first was being written, was dropped: one offer, for the first",
            listOf(older.id),
            vm.undoEntries.value.setIds(),
        )
        assertEquals("the second set is still stored", listOf(newer.id), storedSession(sessionId).sets.map { it.id })
    }

    @Test
    fun anUndoTappedWhileASaveIsHeldIsDroppedAndTheOfferStands() = runBlocking {
        val sessionId = seedSquat(loggedSets = listOf(SET_100x5))
        val vm = viewModel(sessionId)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 1 }
        val deleted = storedSession(sessionId).sets.single()
        vm.awaitEntryUnlocked()
        vm.deleteSet(deleted.id)
        vm.awaitOfferFor(deleted.id)
        deps.workoutRepository.awaitSession(sessionId) { it.sets.isEmpty() }

        // A log whose write fails leaves its save held until the lifter retries it.
        vm.uiState.awaitFirst { it.canLog }
        failWrites = true
        vm.logSet()
        vm.uiState.awaitFirst { it.save.phase == WorkoutSavePhase.FAILED && !it.logging }
        assertTrue("the failed save is still held", vm.uiState.value.save.pending)

        // The disk would take a write again; the held save alone is what must refuse the tap.
        failWrites = false
        vm.undoTopOffer()
        afterEveryQueuedWrite()
        assertTrue("nothing was put back while the save was held", storedSession(sessionId).sets.isEmpty())
        assertEquals(
            "the undo is dropped while the save is held: the offer stands",
            listOf(deleted.id),
            vm.undoEntries.value.setIds(),
        )

        vm.retrySave()
        vm.uiState.awaitFirst { !it.save.pending && !it.logging && it.session?.sets?.size == 1 }
        vm.awaitEntryUnlocked()
        vm.undoTopOffer()
        val restored = deps.workoutRepository.awaitSession(sessionId) { it.sets.size == 2 }
        vm.undoEntries.awaitFirst { it.isEmpty() }
        assertTrue(
            "once the save landed, undo put the deleted set back",
            restored.sets.any { it.id == deleted.id },
        )
    }

    @Test
    fun theNextOfferAfterAProcessDeathReadsTheAccessibilityTimeoutAgain() = runBlocking {
        val sessionId = seedSquat(loggedSets = listOf(SET_100x5, SET_100x5))
        val handle = handleFor(sessionId)
        // TalkBack asks for twenty seconds.
        val vm = viewModel(sessionId = sessionId, handle = handle, undoTimeout = UndoTimeoutProvider { TALKBACK_DWELL_MS })
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 2 }
        val (older, newer) = storedSession(sessionId).sets
        vm.awaitEntryUnlocked()
        vm.deleteSet(older.id)
        vm.awaitOfferFor(older.id)
        assertEquals("the first offer is promised TalkBack's twenty seconds", TALKBACK_DWELL_MS, vm.undoDwellMs.value)

        // The process comes back on a platform that asks for no more than the base dwell.
        die(vm)
        val revived = viewModel(sessionId = sessionId, handle = handle, undoTimeout = UndoTimeoutProvider { it.toLong() })
        revived.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 1 }
        assertEquals(
            "the revived floor keeps the dwell its offer was promised",
            TALKBACK_DWELL_MS,
            revived.undoDwellMs.value,
        )
        revived.awaitEntryUnlocked()
        revived.deleteSet(newer.id)
        revived.awaitOfferFor(newer.id)
        assertEquals(
            "the next offer reads the platform's timeout again: the base dwell",
            Motion.STATUS_DWELL_MS,
            revived.undoDwellMs.value,
        )
        die(revived)
        assertEquals(
            "the saved state promises the next offer's dwell, not the old one",
            Motion.STATUS_DWELL_MS,
            SavedStateFloorUndo(handle).readDwellMs(),
        )
    }

    @Test
    fun theSameSetDeletedTwiceGetsTwoOfferKeys() = runBlocking {
        val sessionId = seedSquat(loggedSets = listOf(SET_100x5))
        val vm = viewModel(sessionId)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 1 }
        val logged = storedSession(sessionId).sets.single()
        vm.awaitEntryUnlocked()
        vm.deleteSet(logged.id)
        vm.awaitOfferFor(logged.id)
        val firstKey = vm.undoEntries.value.last().offer.key
        vm.undoTopOffer()
        deps.workoutRepository.awaitSession(sessionId) { it.sets.size == 1 }
        vm.undoEntries.awaitFirst { it.isEmpty() }
        vm.awaitEntryUnlocked()

        vm.deleteSet(logged.id)
        vm.awaitOfferFor(logged.id)
        assertNotEquals(
            "the same set deleted again gets a new offer key, so the banner restarts its dwell",
            firstKey,
            vm.undoEntries.value.last().offer.key,
        )
    }

    @Test
    fun aBodyweightSetsOfferNamesItTheBodyweightWay() = runBlocking {
        val sessionId = seedSquat(loggedSets = emptyList())
        deps.database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = PUSH_UP,
                    name = "Push-up",
                    muscleGroup = "Chest",
                    notes = "",
                    isCustom = false,
                    loadType = "BODYWEIGHT",
                    nameKey = "push-up",
                ),
            ),
        )
        val pushUp = checkNotNull(deps.exerciseRepository.getById(PUSH_UP)) { "the push-up was not stored" }
        assertEquals("the push-up is a bodyweight lift", LoadType.BODYWEIGHT, pushUp.loadType)
        deps.workoutRepository.addExerciseToSession(
            sessionId = sessionId,
            exercise = pushUp,
            targetSets = 3,
            targetReps = 12,
            targetWeightKg = null,
            restSeconds = 60,
        )
        deps.workoutRepository.logSet(
            sessionId = sessionId,
            exerciseId = PUSH_UP,
            weightKg = 0.0,
            reps = 12,
            rpe = null,
            isWarmup = false,
        )
        val vm = viewModel(sessionId)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 1 }
        val logged = storedSession(sessionId).sets.single()
        vm.awaitEntryUnlocked()
        vm.deleteSet(logged.id)
        vm.awaitOfferFor(logged.id)

        val bodyweight = UndoHostCopy.setDeleted(
            SetCopy.setLine(weightKg = 0.0, reps = 12, loadClass = LoadClass.BODYWEIGHT, unit = WeightUnit.KG),
        )
        val loaded = UndoHostCopy.setDeleted(
            SetCopy.setLine(weightKg = 0.0, reps = 12, loadClass = LoadClass.LOADED, unit = WeightUnit.KG),
        )
        assertNotEquals("a bodyweight set and a loaded one are worded differently", loaded, bodyweight)
        assertEquals(
            "a bodyweight set's offer names it the bodyweight way",
            bodyweight,
            vm.undoEntries.value.last().offer.message,
        )
    }

    @Test
    fun aRestoreThatFailsKeepsTheOfferAndSaysSo() = runBlocking {
        val sessionId = seedSquat(loggedSets = listOf(SET_100x5))
        val vm = viewModel(sessionId)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 1 }
        val deleted = storedSession(sessionId).sets.single()
        vm.awaitEntryUnlocked()
        vm.deleteSet(deleted.id)
        vm.awaitOfferFor(deleted.id)
        deps.workoutRepository.awaitSession(sessionId) { it.sets.isEmpty() }

        failWrites = true
        vm.undoTopOffer()
        val refused = vm.awaitState { it.error != null && !it.entryLocked }
        assertEquals(
            "a restore that fails says so",
            "Could not update this workout. Your saved sets are kept.",
            refused.error,
        )
        assertEquals("a restore that fails keeps its offer", listOf(deleted.id), vm.undoEntries.value.setIds())
        assertTrue("a restore that fails puts nothing back", storedSession(sessionId).sets.isEmpty())

        failWrites = false
        vm.undoTopOffer()
        val restored = deps.workoutRepository.awaitSession(sessionId) { it.sets.size == 1 }
        assertEquals("the second try puts the set back", deleted.id, restored.sets.single().id)
        vm.undoEntries.awaitFirst { it.isEmpty() }
        vm.awaitState { it.session?.sets?.size == 1 && !it.entryLocked }
        assertNull("the landed undo clears the failure", vm.uiState.value.error)
    }

    private fun viewModel(
        sessionId: String,
        handle: SavedStateHandle = handleFor(sessionId),
        undoTimeout: UndoTimeoutProvider = UndoTimeoutProvider { it.toLong() },
    ) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = handle,
        container = deps,
        undoTimeout = undoTimeout,
    ).also(viewModels::add)

    private fun handleFor(sessionId: String) = SavedStateHandle(mapOf("sessionId" to sessionId))

    private suspend fun seedSquat(loggedSets: List<TestSetInput>): String = seedTestWorkout(
        deps = deps,
        exerciseId = SQUAT,
        exerciseName = "Squat",
        targetSets = 3,
        targetReps = 5,
        targetWeightKg = 100.0,
        restSeconds = 90,
        loggedSets = loggedSets,
    ).session.id

    private suspend fun storedSession(sessionId: String): WorkoutSession =
        checkNotNull(deps.workoutRepository.getSession(sessionId)) { "the session $sessionId is not stored" }

    /**
     * A process death. The ViewModel ends first, with nothing of it running on: its other writers
     * share the handle, and FloorVmContractTest saw a revival beside them read an empty queue. The
     * in-memory draft cache dies with the process; the handle is what comes back.
     */
    private suspend fun die(vm: ActiveWorkoutViewModel) {
        vm.clearAndJoinForTest()
        viewModels.remove(vm)
        deps.workoutDraftCache.clearAll()
    }

    /** The top offer names [setId], and the mutation that made it has let go of the entry. */
    private suspend fun ActiveWorkoutViewModel.awaitOfferFor(setId: String) {
        undoEntries.awaitFirst { entries ->
            val top = entries.lastOrNull()?.token
            top is FloorUndo.DeletedSet && top.deleted.setId == setId
        }
        awaitEntryUnlocked()
    }

    /**
     * Returns once the database has run a transaction asked for now. Room runs transactions one
     * at a time, in the order they were asked for, so any write a tap started is finished by then
     * and what is stored is everything the tap did.
     */
    private suspend fun afterEveryQueuedWrite() {
        try {
            withTimeout(TestWaits.FLOW_MS) { deps.database.withTransaction { } }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("the database never ran a transaction asked for after the tap", timedOut)
        }
    }

    /** Every write the screen makes, except that a set's delete first waits for [deleteGate] and a set's insert fails while [failWrites]. */
    private inner class HeldWrites(private val real: WorkoutDao) : WorkoutDao by real {
        override suspend fun deleteSet(id: String) {
            deleteGate?.await()
            real.deleteSet(id)
        }

        override suspend fun insertSet(set: SetLogEntity) {
            check(!failWrites) { "Injected write failure" }
            real.insertSet(set)
        }
    }

    private companion object {
        const val SQUAT = "squat"
        const val PUSH_UP = "push-up"
        const val TALKBACK_DWELL_MS = 20_000L
        val SET_100x5 = TestSetInput(weightKg = 100.0, reps = 5, rpe = 8)
    }
}

/** The deleted sets these offers name, oldest first. */
private fun List<UndoEntry>.setIds(): List<String> = map { entry ->
    checkNotNull((entry.token as? FloorUndo.DeletedSet)?.deleted?.setId) { "not a deleted set: $entry" }
}
