package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.Exercise
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
        assertTrue(write is ActivityWrite.Rejected)
        assertEquals("Nothing to save.", (write as ActivityWrite.Rejected).reason)
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

    private fun composer(handle: SavedStateHandle) = ActivityComposerViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        handle,
        deps,
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
