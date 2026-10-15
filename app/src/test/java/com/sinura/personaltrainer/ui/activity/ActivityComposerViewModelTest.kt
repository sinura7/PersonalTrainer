package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.CompleteTrainingOutcome
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.testutil.GatedPickerCreation
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ActivityComposerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: ActivityComposerViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun pendingCreationOwnsTheDraftUntilSelectionIsDelivered() = runBlocking {
        val existing = seedLift()
        val creation = GatedPickerCreation(deps)
        val vm = composer(SavedStateHandle(mapOf("mode" to "strength")), creation.container)
        viewModel = vm
        try {
            vm.uiState.awaitFirst { it.catalog.isNotEmpty() }
            vm.addStrength(existing, 100.0, 5)
            vm.createExercise("Accepted row", "Back")
            assertFalse(vm.canLeave())
            withTimeout(TestWaits.FLOW_MS) { creation.entered.await() }
            vm.createExercise("Duplicate tap", "Back")
            vm.save()
            vm.discardDraft()
            assertFalse(vm.uiState.value.saving)
            assertEquals(null, vm.savedId.value)
            assertEquals(1, vm.uiState.value.strength.size)
            creation.release.complete(Unit)
            val created = withTimeout(TestWaits.FLOW_MS) { vm.createdExercise.first { it != null } }!!
            assertFalse(vm.canLeave())
            vm.onCreatedExerciseHandled()
            assertTrue(vm.canLeave())
            vm.addStrength(created, 50.0, 8)
            vm.save()
            val id = withTimeout(TestWaits.FLOW_MS) { vm.savedId.first { it != null } }!!
            assertEquals(1, creation.inserts.get())
            assertEquals(listOf(existing.id, created.id), deps.activityRepository.get(id)!!.strengthBlocks.map { it.exerciseId })
        } finally { creation.release.complete(Unit) }
    }

    @Test
    fun cancellingAnActivityKeepsTheLiftAlreadySavedToLibrary() = runBlocking {
        val handle = SavedStateHandle(mapOf("mode" to "strength"))
        val vm = composer(handle)
        viewModel = vm
        vm.uiState.awaitFirst { it.todayEpochDay != 0L }
        vm.createExercise("Library keeps this lift", "Chest")
        val created = withTimeout(TestWaits.FLOW_MS) { vm.createdExercise.first { it != null } }!!
        vm.onCreatedExerciseHandled()
        // The saved library result is delivered before the picker releases its write owner.
        // Deliberate Cancel is permitted only after both have settled.
        vm.uiState.awaitFirst { !it.pickerProgress.creating && vm.canLeave() }
        vm.addStrength(created, 50.0, 8)
        vm.discardDraft()
        assertFalse(SavedStateComposerDraft(handle).exists())
        assertEquals(created, deps.exerciseRepository.getById(created.id))
        assertEquals(listOf(created.id), deps.exerciseRepository.observeAll().first().map { it.id })
        assertTrue(deps.activityRepository.all().isEmpty())
        assertTrue(deps.database.workoutDao().getAllSessions().isEmpty())
        assertTrue(deps.database.workoutDao().getAllSets().isEmpty())
    }

    @Test
    fun backdatedMixedKeepsTheChosenDayAndTheRowOrder() = runBlocking {
        val squat = seedLift()
        val benchSaved = deps.exerciseRepository.createCustom("Bench", "Chest")
        check(benchSaved is com.sinura.personaltrainer.data.repository.SaveExerciseResult.Saved)
        val bench = benchSaved.exercise
        val handle = SavedStateHandle(mapOf("mode" to "mixed"))
        val first = composer(handle)
        first.setEpochDay(20_000L)
        first.addStrength(squat, 100.0, 5)
        first.addStrength(bench, 60.0, 8)
        first.addCardio(CardioType.RIDE, 20, 4.0, true)
        first.addCardio(CardioType.RUN, 10, null, false)
        first.clearAndJoinForTest()

        viewModel = composer(handle)
        val state = viewModel!!.uiState.awaitFirst { it.strength.size == 2 && it.cardio.size == 2 }
        assertEquals(20_000L, state.epochDay)
        assertEquals(listOf(squat.id, bench.id), state.strength.map { it.exercise.id })
        assertEquals(100.0, state.strength[0].weightKg, 0.0)
        assertEquals(5, state.strength[0].reps)
        assertEquals(60.0, state.strength[1].weightKg, 0.0)
        assertEquals(8, state.strength[1].reps)
        assertEquals(listOf(CardioType.RIDE, CardioType.RUN), state.cardio.map { it.type })
        assertEquals(20, state.cardio[0].minutes)
        assertTrue(state.cardio[0].indoor)
        assertEquals(4.0, state.cardio[0].distanceKm!!, 0.0)
        assertEquals(10, state.cardio[1].minutes)
        assertFalse(state.cardio[1].indoor)
        assertEquals(null, state.cardio[1].distanceKm)

        viewModel!!.save()
        val id = withTimeout(TestWaits.FLOW_MS) { viewModel!!.savedId.first { it != null } }!!
        val session = deps.activityRepository.get(id)!!
        assertEquals(ActivityOrigin.BACKDATED, session.origin)
        assertEquals(20_000L, session.localEpochDay)
        assertEquals(listOf(squat.id, bench.id), session.strengthBlocks.map { it.exerciseId })
        assertEquals(listOf(0, 1), session.strengthBlocks.map { it.sortOrder })
        assertEquals(listOf(CardioType.RIDE, CardioType.RUN), session.cardioBlocks.map { it.type })
        assertEquals(listOf(2, 3), session.cardioBlocks.map { it.sortOrder })
        assertEquals(null, deps.activityRepository.getLive())
        assertEquals(null, deps.cardioTimerPersistence.load())
    }

    @Test
    fun backdatedStrengthWritesAnActivityWithSetsAndNoFutureDate() = runBlocking {
        val exercise = seedLift()
        viewModel = composer("strength")
        viewModel!!.addStrength(exercise, 100.0, 5)
        viewModel!!.setEpochDay(20_000L)
        viewModel!!.save()
        val id = withTimeout(TestWaits.FLOW_MS) { viewModel!!.savedId.first { it != null } }!!
        val session = deps.activityRepository.get(id)!!
        assertEquals(ActivityOrigin.BACKDATED, session.origin)
        assertEquals(1, session.strengthSetCount())
        assertTrue(session.cardioBlocks.isEmpty())
        assertEquals(0, deps.database.workoutDao().getAllSets().size)
    }

    @Test
    fun typedCardioWritesZeroStrengthRows() = runBlocking {
        viewModel = composer("cardio")
        viewModel!!.addCardio(CardioType.RUN, 30, 5.0, false)
        viewModel!!.save()
        val id = withTimeout(TestWaits.FLOW_MS) { viewModel!!.savedId.first { it != null } }!!
        val session = deps.activityRepository.get(id)!!
        assertTrue(session.isCardioOnly)
        assertEquals(0, session.strengthSetCount())
        assertEquals(0, deps.database.workoutDao().getAllSets().size)
    }

    @Test
    fun mixedSessionKeepsSeparateModalityBlocks() = runBlocking {
        val exercise = seedLift()
        viewModel = composer("mixed")
        viewModel!!.addStrength(exercise, 80.0, 8)
        viewModel!!.addCardio(CardioType.RIDE, 20, null, true)
        viewModel!!.save()
        val id = withTimeout(TestWaits.FLOW_MS) { viewModel!!.savedId.first { it != null } }!!
        val session = deps.activityRepository.get(id)!!
        assertTrue(session.isMixed)
        assertEquals(1, session.strengthBlocks.size)
        assertEquals(1, session.cardioBlocks.size)
        assertEquals(0, session.strengthBlocks.first().sortOrder)
        assertEquals(1, session.cardioBlocks.first().sortOrder)
    }

    @Test
    fun untitledCardioUsesGymNameNotSchemaEnum() = runBlocking {
        viewModel = composer("cardio")
        viewModel!!.addCardio(CardioType.RUN, 30, 5.0, false)
        viewModel!!.save()
        val id = withTimeout(TestWaits.FLOW_MS) { viewModel!!.savedId.first { it != null } }!!
        val session = deps.activityRepository.get(id)!!
        assertEquals("Run", session.title)
        assertNotEquals("RUN", session.title)
    }

    @Test
    fun removeStrengthDropsTheLineWithoutSaving() = runBlocking {
        val exercise = seedLift()
        viewModel = composer("strength")
        viewModel!!.addStrength(exercise, 100.0, 5)
        viewModel!!.removeStrength(0)
        val write = viewModel!!.confirmDraft()
        assertTrue(write is CompleteTrainingOutcome.RuledOut)
        assertEquals("Nothing to save.", (write as CompleteTrainingOutcome.RuledOut).reason)
        assertEquals(null, viewModel!!.savedId.value)
    }

    @Test
    fun futureDateIsClampedToToday() = runBlocking {
        viewModel = composer("cardio")
        val today = viewModel!!.uiState.value.epochDay
        viewModel!!.setEpochDay(today + 3)
        assertEquals(today, viewModel!!.uiState.value.epochDay)
    }

    @Test
    fun pastModeOpensTheStrengthComposer() = runBlocking {
        viewModel = composer("past")
        val state = viewModel!!.uiState.awaitFirst { it.todayEpochDay != 0L }
        assertEquals(ComposerMode.STRENGTH, state.mode)
    }

    @Test
    fun processRecreationRestoresTheTypedDraft() = runBlocking {
        val exercise = seedLift()
        val handle = SavedStateHandle(mapOf("mode" to "mixed"))
        val first = composer(handle)
        first.setTitle("Leg day")
        first.setEpochDay(20_000L)
        first.addStrength(exercise, 100.0, 5)
        first.addCardio(CardioType.RIDE, 20, 4.0, true)
        first.clearAndJoinForTest()

        // The same handle is what the framework hands the recreated ViewModel.
        viewModel = composer(handle)
        val state = viewModel!!.uiState.awaitFirst { it.todayEpochDay != 0L }

        assertEquals("Leg day", state.title)
        assertEquals(20_000L, state.epochDay)
        val line = state.strength.single()
        assertEquals(exercise.id, line.exercise.id)
        assertEquals(exercise.name, line.exercise.name)
        assertEquals(exercise.loadType, line.exercise.loadType)
        assertEquals(100.0, line.weightKg, 0.0)
        assertEquals(5, line.reps)
        val ride = state.cardio.single()
        assertEquals(CardioType.RIDE, ride.type)
        assertEquals(20, ride.minutes)
        assertEquals(4.0, ride.distanceKm!!, 0.0)
        assertTrue(ride.indoor)
    }

    @Test
    fun anAcceptedSaveClearsTheDraft() = runBlocking {
        val handle = SavedStateHandle(mapOf("mode" to "cardio"))
        viewModel = composer(handle)
        viewModel!!.setTitle("Tempo")
        viewModel!!.addCardio(CardioType.RUN, 30, 5.0, false)
        viewModel!!.save()
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.savedId.first { it != null } }
        viewModel!!.clearAndJoinForTest()

        viewModel = composer(handle)
        val state = viewModel!!.uiState.awaitFirst { it.todayEpochDay != 0L }
        assertEquals("", state.title)
        assertTrue(state.cardio.isEmpty())
        assertEquals(1, deps.activityRepository.all().size)
    }

    @Test
    fun leavingClearsTheDraft() = runBlocking {
        val handle = SavedStateHandle(mapOf("mode" to "cardio"))
        viewModel = composer(handle)
        viewModel!!.addCardio(CardioType.RUN, 30, null, false)
        viewModel!!.discardDraft()
        viewModel!!.clearAndJoinForTest()

        viewModel = composer(handle)
        val state = viewModel!!.uiState.awaitFirst { it.todayEpochDay != 0L }
        assertTrue(state.cardio.isEmpty())
    }

    /**
     * UX07-AC02: Cancel during a save is refused rather than acted on. The draft survives
     * until the write answers, the write lands exactly once, and only the accepted save
     * clears the draft — so nothing on screen can call committed work discarded.
     */
    @Test
    fun cancelWhileSavingIsRefusedAndTheSaveStillLandsOnce() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            activityDaoDecorator = { dao -> GatedInsertDao(dao, gate) },
        )
        val exercise = seedLift()
        val handle = SavedStateHandle(mapOf("mode" to "strength"))
        viewModel = composer(handle)
        viewModel!!.addStrength(exercise, 100.0, 5)
        val draft = SavedStateComposerDraft(handle)
        assertTrue(draft.exists())

        viewModel!!.save()
        try {
            val saving = viewModel!!.uiState.awaitFirst { it.saving }
            assertTrue(saving.saving)

            // Cancel mid-write: refused. The draft is the only record of what is being written.
            assertFalse(viewModel!!.canLeave())
            viewModel!!.discardDraft()
            assertTrue(draft.exists())
            assertEquals(1, viewModel!!.uiState.value.strength.size)
            assertEquals(null, viewModel!!.savedId.value)
        } finally {
            gate.complete(Unit)
        }

        val id = withTimeout(TestWaits.FLOW_MS) { viewModel!!.savedId.first { it != null } }!!
        val settled = viewModel!!.uiState.awaitFirst { !it.saving }
        assertEquals(null, settled.error)
        assertTrue(viewModel!!.canLeave())
        // The accepted save is what spends the draft, and there is exactly one row.
        assertFalse(draft.exists())
        assertEquals(listOf(id), deps.activityRepository.all().map { it.id })
    }

    @Test
    fun aFailingReminderCleanupStillSavesExactlyOnce() = runBlocking {
        // The activity commits before its reminders are cancelled. A throw from that
        // cleanup used to surface as "Could not save", with the plan link still held, so
        // the retry wrote the day a second time.
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            occurrenceCleanup = { error("WorkManager is unavailable") },
        )
        deps.pendingOccurrenceId.value = "occ-1"
        viewModel = composer("cardio")
        viewModel!!.addCardio(CardioType.RUN, 30, 5.0, false)
        viewModel!!.save()

        val id = withTimeout(TestWaits.FLOW_MS) { viewModel!!.savedId.first { it != null } }!!
        val saved = deps.activityRepository.all().single()
        assertEquals(id, saved.id)
        assertEquals("occ-1", saved.occurrenceId)
        assertEquals(null, viewModel!!.uiState.value.error)
    }

    private fun composer(mode: String) = composer(SavedStateHandle(mapOf("mode" to mode)))

    private fun composer(handle: SavedStateHandle, container: AppDependencies = deps) = ActivityComposerViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        handle,
        container,
    )

    /** The activity DAO with its session insert held at a gate, so a save can be caught mid-write. */
    private class GatedInsertDao(
        private val delegate: ActivityDao,
        private val gate: CompletableDeferred<Unit>,
    ) : ActivityDao by delegate {
        override suspend fun insertSession(session: ActivitySessionEntity) {
            gate.await()
            delegate.insertSession(session)
        }
    }

    private suspend fun seedLift(): Exercise {
        val saved = deps.exerciseRepository.createCustom("Squat", "Quads")
        check(saved is com.sinura.personaltrainer.data.repository.SaveExerciseResult.Saved)
        return saved.exercise
    }
}
