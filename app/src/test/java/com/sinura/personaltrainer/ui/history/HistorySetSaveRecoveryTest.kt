package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.workout.WorkoutSavePhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room, shipping recovery engine and saved-state codec; no fake save acknowledgement. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HistorySetSaveRecoveryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val models = mutableListOf<SessionDetailViewModel>()
    private var armed = false
    private var failWrite = false
    private var failRead = false
    private var gate: CompletableDeferred<Unit>? = null
    private var inserts = 0
    private var corrections = 0

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher,
            workoutDaoDecorator = { real -> object : WorkoutDao by real {
                override suspend fun insertSet(set: SetLogEntity) {
                    if (armed) { inserts++; gate?.await(); check(!failWrite) { "controlled insert failure" } }
                    real.insertSet(set)
                }
                override suspend fun updateSet(set: SetLogEntity) {
                    if (armed) { corrections++; gate?.await(); check(!failWrite) { "controlled correction failure" } }
                    real.updateSet(set)
                }
                override suspend fun getSet(id: String): SetLogEntity? {
                    check(!failRead) { "controlled inspection failure" }
                    return real.getSet(id)
                }
            } },
        )
    }

    @After fun tearDown() {
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        deps.close()
        Dispatchers.resetMain()
    }

    @Test fun refusedCorrectionRetainsExactOriginalIdentityAndRetrySavesOnlyTheEnteredValues() = runBlocking {
        val session = seed()
        val vm = model(session)
        val original = session.sets.single()
        armed = true; failWrite = true
        vm.saveCorrection(original.id, 117.5, 9, 9, false, null)
        vm.setSave.awaitFirst { it.phase == WorkoutSavePhase.FAILED }
        val frozen = checkNotNull(vm.setSave.value.command)
        assertEquals(session, stored(session.id))
        assertNull(vm.setEditorExit.value)
        assertEquals(original.id, frozen.setId)
        assertEquals(original.completedAt, frozen.completedAt)
        assertEquals(117.5, frozen.values.weightKg, 0.0)
        assertEquals(9, frozen.values.reps)
        // A second Save cannot replace the frozen payload with different values.
        vm.saveCorrection(original.id, 999.0, 1, 6, true, null)
        assertEquals(frozen, vm.setSave.value.command)
        failWrite = false
        vm.retrySetSave()
        assertEquals(frozen, vm.setEditorExit.awaitFirst { it != null })
        val after = stored(session.id)
        assertEquals(session.copy(sets = listOf(original.copy(weightKg = 117.5, reps = 9, rpe = 9))), after)
        assertEquals(2, corrections)
        assertEquals(0, inserts)
    }

    @Test fun refusedAddRetryUsesOneIdentityAndPreservesTheHistoricalWindow() = runBlocking {
        val session = seed()
        val vm = model(session)
        armed = true; failWrite = true
        vm.saveAdditionalSet(LIFT, 17.5, 9, 9, true)
        vm.setSave.awaitFirst { it.phase == WorkoutSavePhase.FAILED }
        val frozen = checkNotNull(vm.setSave.value.command)
        assertEquals(session, stored(session.id))
        assertTrue(frozen.completedAt in session.startedAt..checkNotNull(session.finishedAt))
        failWrite = false
        vm.retrySetSave()
        assertEquals(frozen, vm.setEditorExit.awaitFirst { it != null })
        val after = stored(session.id)
        assertEquals(2, after.sets.size)
        val added = after.sets.single { it.id == frozen.setId }
        assertEquals(17.5, added.weightKg, 0.0)
        assertEquals(9, added.reps)
        assertEquals(9, added.rpe)
        assertTrue(added.isWarmup)
        assertEquals(frozen.completedAt, added.completedAt)
        assertEquals(session.sets.single(), after.sets.first())
        assertEquals(session.copy(sets = after.sets), after)
        assertEquals(2, inserts)
    }

    @Test fun delayedSaveBlocksDuplicateTapsDeletionAndNavigationUntilAcknowledged() = runBlocking {
        val session = seed()
        val vm = model(session)
        armed = true; gate = CompletableDeferred()
        vm.saveAdditionalSet(LIFT, 120.0, 6, 8, false)
        vm.setSave.awaitFirst { it.phase == WorkoutSavePhase.SAVING }
        val frozen = checkNotNull(vm.setSave.value.command)
        vm.saveAdditionalSet(LIFT, 500.0, 1, 10, false)
        vm.retrySetSave()
        vm.releaseSetSave(cancel = true)
        vm.deleteSet(session.sets.single().id)
        vm.deleteSession()
        vm.repeatSession()
        vm.requestNotesExit()
        assertEquals(frozen, vm.setSave.value.command)
        assertFalse(vm.notesExitRequested.value)
        assertFalse(vm.deleted.value)
        assertNull(vm.navigateToSession.value)
        assertNull(vm.setEditorExit.value)
        checkNotNull(gate).complete(Unit)
        vm.setEditorExit.awaitFirst { it != null }
        assertEquals(1, inserts)
        assertEquals(2, stored(session.id).sets.size)
        assertEquals(session.sets.single(), stored(session.id).sets.first())
    }

    @Test fun savedStateRecoveryAfterAnUnacknowledgedCommitChecksBeforeAnotherInsert() = runBlocking {
        val session = seed()
        val handle = SavedStateHandle(mapOf("sessionId" to session.id))
        val vm = model(session, handle)
        armed = true; failWrite = true
        vm.saveAdditionalSet(LIFT, 123.5, 7, 9, false)
        vm.setSave.awaitFirst { it.phase == WorkoutSavePhase.FAILED }
        val frozen = checkNotNull(vm.setSave.value.command)
        failWrite = false
        // The real DB has committed, but this screen has not received an acknowledgement.
        deps.workoutRepository.saveFinishedSet(frozen)
        val committed = stored(session.id)
        val attempted = inserts
        vm.clearAndJoinForTest()
        failRead = true
        val recreated = model(session, handle)
        recreated.setSave.awaitFirst { it.phase == WorkoutSavePhase.FAILED }
        assertEquals(frozen, recreated.setSave.value.command)
        assertEquals(attempted, inserts)
        assertEquals(committed, stored(session.id))
        assertNull(recreated.setEditorExit.value)
        failRead = false
        recreated.retrySetSave()
        assertEquals(frozen, recreated.setEditorExit.awaitFirst { it != null })
        assertEquals(attempted, inserts)
        assertEquals(committed, stored(session.id))
    }

    @Test fun restoredUnwrittenSubmissionOffersRetryWithoutWritingAutomatically() = runBlocking {
        val session = seed()
        val handle = SavedStateHandle(mapOf("sessionId" to session.id))
        val vm = model(session, handle)
        armed = true; failWrite = true
        vm.saveAdditionalSet(LIFT, 112.5, 8, 9, false)
        vm.setSave.awaitFirst { it.phase == WorkoutSavePhase.FAILED }
        val frozen = vm.setSave.value.command
        vm.clearAndJoinForTest()
        failWrite = false
        val recreated = model(session, handle)
        recreated.setSave.awaitFirst { it.phase == WorkoutSavePhase.FAILED }
        assertEquals(frozen, recreated.setSave.value.command)
        assertEquals(1, inserts)
        assertEquals(session, stored(session.id))
        recreated.retrySetSave()
        assertEquals(frozen, recreated.setEditorExit.awaitFirst { it != null })
        assertEquals(2, inserts)
        assertEquals(2, stored(session.id).sets.size)
    }

    @Test fun newerCorrectionConflictsAndRetryCannotOverwriteIt() = runBlocking {
        val session = seed()
        val vm = model(session)
        val original = session.sets.single()
        armed = true; failWrite = true
        vm.saveCorrection(original.id, 120.0, 8, 9, false, null)
        vm.setSave.awaitFirst { it.phase == WorkoutSavePhase.FAILED }
        failWrite = false
        deps.workoutRepository.updateSet(original.id, 125.0, 10, 8, false)
        val newer = stored(session.id)
        vm.retrySetSave()
        vm.setSave.awaitFirst { it.phase == WorkoutSavePhase.CONFLICT }
        val attempts = corrections
        vm.retrySetSave()
        assertEquals(attempts, corrections)
        assertEquals(newer, stored(session.id))
        vm.releaseSetSave(cancel = true)
        vm.setEditorExit.awaitFirst { it != null }
        assertFalse(vm.setSave.value.pending)
        assertEquals(newer, stored(session.id))
    }

    @Test fun cancelAndEditRequireSuccessfulInspectionAndNeverPretendAnUnknownSaveWasDiscarded() = runBlocking {
        val session = seed()
        val vm = model(session)
        armed = true; failWrite = true
        vm.saveAdditionalSet(LIFT, 110.0, 9, 9, false)
        vm.setSave.awaitFirst { it.phase == WorkoutSavePhase.FAILED }
        val frozen = vm.setSave.value.command
        failRead = true
        vm.releaseSetSave(cancel = true)
        vm.setSave.awaitFirst { it.phase == WorkoutSavePhase.FAILED }
        assertEquals(frozen, vm.setSave.value.command)
        assertNull(vm.setEditorExit.value)
        failRead = false
        vm.releaseSetSave(cancel = false)
        vm.setSave.awaitFirst { !it.pending }
        assertNull(vm.setEditorExit.value)
        assertEquals(session, stored(session.id))
        failWrite = false
        vm.saveAdditionalSet(LIFT, 115.0, 10, 8, false)
        val corrected = checkNotNull(vm.setEditorExit.awaitFirst { it != null })
        assertNotEquals(checkNotNull(frozen).setId, corrected.setId)
        assertEquals(115.0, stored(session.id).sets.last().weightKg, 0.0)
    }

    @Test fun acknowledgedIdenticalAddsRemainSeparateSetsAndRestorationDoesNotRepeatTheFirst() = runBlocking {
        val session = seed()
        val handle = SavedStateHandle(mapOf("sessionId" to session.id))
        val vm = model(session, handle)
        armed = true
        vm.saveAdditionalSet(LIFT, 100.0, 5, 8, false)
        val first = checkNotNull(vm.setEditorExit.awaitFirst { it != null })
        vm.clearAndJoinForTest()
        val recreated = model(session, handle)
        assertEquals(first, recreated.setEditorExit.value)
        assertFalse(recreated.setSave.value.pending)
        assertEquals(1, inserts)
        recreated.onSetEditorExitHandled(first)
        recreated.saveAdditionalSet(LIFT, 100.0, 5, 8, false)
        val second = checkNotNull(recreated.setEditorExit.awaitFirst { it != null })
        assertNotEquals(first.setId, second.setId)
        assertEquals(2, inserts)
        assertEquals(listOf(1, 2, 3), stored(session.id).sets.map { it.setNumber })
    }

    @Test fun missingEffortRetainsEditableValuesWithoutAnyWriteOrPendingOperation() = runBlocking {
        val session = seed()
        val vm = model(session)
        armed = true
        vm.saveCorrection(session.sets.single().id, 125.0, 10, null, false, null)
        assertEquals(SetLogRules.EFFORT_MISSING, vm.error.value)
        assertFalse(vm.setSave.value.pending)
        assertNull(vm.setEditorExit.value)
        assertEquals(0, corrections)
        assertEquals(session, stored(session.id))
    }

    @Test fun openingSnapshotSurvivesRecreationAndDismissReopenUsesTheNewSavedRecord() = runBlocking {
        val session = seed()
        val handle = SavedStateHandle(mapOf("sessionId" to session.id))
        val vm = model(session, handle)
        val original = session.sets.single()
        vm.beginSetEdit(original)
        deps.workoutRepository.updateSet(original.id, 125.0, 10, 10, false)
        val newer = stored(session.id)
        vm.clearAndJoinForTest()
        val recreated = model(session, handle)
        assertEquals(100.0, checkNotNull(recreated.editorOriginal.value).values.weightKg, 0.0)
        recreated.saveCorrection(original.id, 100.0, 5, 9, false, null)
        recreated.setSave.awaitFirst { it.phase == WorkoutSavePhase.CONFLICT }
        assertEquals(newer, stored(session.id))
        recreated.releaseSetSave(cancel = true)
        val closed = checkNotNull(recreated.setEditorExit.awaitFirst { it != null })
        recreated.onSetEditorExitHandled(closed)
        assertNull(recreated.editorOriginal.value)
        recreated.beginSetEdit(newer.sets.single())
        assertEquals(125.0, checkNotNull(recreated.editorOriginal.value).values.weightKg, 0.0)
        recreated.dismissSetEdit(original.id)
        assertNull(recreated.editorOriginal.value)
        recreated.beginSetEdit(newer.sets.single())
        recreated.saveCorrection(original.id, 125.0, 10, 9, false, null)
        recreated.setEditorExit.awaitFirst { it != null }
        assertEquals(newer.copy(sets = listOf(newer.sets.single().copy(rpe = 9))), stored(session.id))
    }

    private suspend fun seed(): WorkoutSession = seedTestWorkout(deps, exerciseId = LIFT,
        loggedSets = listOf(TestSetInput(100.0, 5)), finish = true,
    ).session

    private suspend fun model(session: WorkoutSession, handle: SavedStateHandle = SavedStateHandle(mapOf("sessionId" to session.id))): SessionDetailViewModel =
        SessionDetailViewModel(ApplicationProvider.getApplicationContext(), handle, deps).also {
            models += it
            it.uiState.awaitFirst { state -> !state.isLoading }
        }

    private suspend fun stored(id: String): WorkoutSession = withTimeout(TestWaits.FLOW_MS) {
        checkNotNull(deps.workoutRepository.observeSession(id).first())
    }

    private companion object { const val LIFT = "test-squat" }
}
