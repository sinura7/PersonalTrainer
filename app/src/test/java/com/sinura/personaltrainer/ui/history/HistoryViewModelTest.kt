package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Repeat while a session is already live must surface the blocked state.
 * Quietly opening Tuesday's half-finished Legs is the failure this exists to stop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HistoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: HistoryViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun repeatWhileLiveSurfacesBlockedNotSilentResume() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        val finished = deps.workoutRepository.startFreeWorkout("Push")
        deps.workoutRepository.finishSession(finished.id, notes = "")
        val live = deps.workoutRepository.startFreeWorkout("Legs")

        viewModel = HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.repeatSession(finished.id)

        val blocked = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.blockedRepeat.first { it != null }
        }
        assertEquals(live.id, blocked!!.inProgressSessionId)
        assertEquals("Legs", blocked.inProgressName)
        assertNull(viewModel!!.navigateToSession.value)
        assertNull(viewModel!!.error.value)
    }

    @Test
    fun repeatMissingSessionSurfacesFailedWithoutNavigating() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.repeatSession("missing")

        val message = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.error.first { it == "That session is no longer available." }
        }
        assertEquals("That session is no longer available.", message)
        assertNull(viewModel!!.navigateToSession.value)
        assertNull(viewModel!!.blockedRepeat.value)
        viewModel!!.onErrorShown()
        assertNull(viewModel!!.error.value)
    }

    @Test
    fun aSuccessfulRepeatClearsItsOwnEarlierRefusal() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        val finished = deps.workoutRepository.startFreeWorkout("Push")
        deps.workoutRepository.finishSession(finished.id, notes = "")

        viewModel = HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.repeatSession("missing")
        withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.error.first { it == "That session is no longer available." }
        }

        viewModel!!.repeatSession(finished.id)

        val newId = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.navigateToSession.first { it != null }
        }
        assertEquals(newId, deps.workoutRepository.getInProgress()?.id)
        assertNull(viewModel!!.error.value)
        assertNull(viewModel!!.blockedRepeat.value)
    }

    @Test
    fun editingAFinishedSetRefreshesTheHorizonReadoutWithoutRelaunch() = runBlocking {
        // Session count and newest id never moved for an edit, so the PRs in the readout
        // stayed stale until the horizon was switched. The revision moves on the edit.
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        val fixture = seedTestWorkout(
            deps = deps,
            loggedSets = listOf(TestSetInput(weightKg = 100.0, reps = 5)),
            finish = true,
        )
        val later = deps.workoutRepository.startRoutine(fixture.routine)
        val logged = deps.workoutRepository.logSet(
            sessionId = later.id,
            exerciseId = fixture.exercise.id,
            weightKg = 90.0,
            reps = 5,
            rpe = null,
            isWarmup = false,
        )
        deps.workoutRepository.finishSession(sessionId = later.id, notes = "")

        viewModel = HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val before = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.uiState.first { it.horizonProgress != null && it.summaries.size == 2 }
        }
        assertEquals(0, before.horizonProgress!!.recordsBroken)

        deps.workoutRepository.updateSet(
            setId = logged.setId,
            weightKg = 110.0,
            reps = 5,
            rpe = null,
            isWarmup = false,
        )

        val after = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.uiState.first { (it.horizonProgress?.recordsBroken ?: 0) > 0 }
        }
        assertEquals(2, after.summaries.size)
    }

    @Test
    fun recordsComeFromTheWholeLogNotTheLastMonth() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        val bench = insertTestExercise(deps = deps, id = "bench", name = "Bench", muscleGroup = "Chest")
        val now = System.currentTimeMillis()
        insertFinishedSession(id = "old", at = now - 60 * DAY, exerciseId = bench.id, weightKg = 120.0)
        insertFinishedSession(id = "recent", at = now - DAY, exerciseId = bench.id, weightKg = 100.0)

        viewModel = HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.records.isNotEmpty() } }

        val record = state.records.single()
        assertEquals(bench.id, record.exerciseId)
        assertEquals("Bench", record.exerciseName)
        assertEquals(120.0, record.valueKg, 0.0)
        assertEquals(now - 60 * DAY, record.achievedAt)
    }

    @Test
    fun aSidecarReadFailureMarksThePageStaleInsteadOfVanishing() {
        val unread = sidecarFromHealth<String>(DataHealth.Unavailable("activity records"))
        assertTrue(unread.stale)
        assertTrue(unread.value.isEmpty())

        val held = sidecarFromHealth(DataHealth.Degraded(listOf("kept"), "activity records"))
        assertTrue(held.stale)
        assertEquals(listOf("kept"), held.value)

        val fine = sidecarFromHealth(DataHealth.Available(listOf("fresh")))
        assertFalse(fine.stale)
        assertEquals(listOf("fresh"), fine.value)
    }

    @Test
    fun aBackdatedStrengthActivityCountsTowardTheHorizonReadout() = runBlocking {
        // A first set against an empty prior is a baseline, not a broken record.
        // The activity has to beat a finished strength session of the same lift.
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        val fixture = seedTestWorkout(
            deps = deps,
            loggedSets = listOf(TestSetInput(weightKg = 100.0, reps = 5)),
            finish = true,
        )
        val now = com.sinura.personaltrainer.util.JvmTime.captureNow()
        val write = deps.confirmActivity(
            ActivityDraft(
                status = ActivityStatus.COMPLETED,
                origin = ActivityOrigin.BACKDATED,
                title = "Make-up squat",
                performedStart = now,
                performedEnd = now,
                blocks = listOf(backdatedSquat(fixture.exercise.id, now.instantMillis + 1_000L)),
            ),
            now,
        )
        assertTrue(write is ActivityWrite.Accepted)

        viewModel = HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.uiState.first {
                it.summaries.size >= 2 && (it.horizonProgress?.recordsBroken ?: 0) > 0
            }
        }
        assertEquals(2, state.summaries.size)
        assertTrue((state.horizonProgress?.recordsBroken ?: 0) > 0)
    }

    private fun backdatedSquat(exerciseId: String, completedAtMs: Long) = StrengthBlock(
        id = "blk-1",
        sortOrder = 0,
        exerciseId = exerciseId,
        exerciseName = "Test squat",
        loadType = LoadType.EXTERNAL,
        equipment = EquipmentType.BARBELL,
        muscles = emptyList(),
        sets = listOf(
            StrengthSet(
                id = "set-act-1",
                setNumber = 1,
                weightKg = 110.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAtMs = completedAtMs,
            ),
        ),
    )

    private suspend fun insertFinishedSession(
        id: String,
        at: Long,
        exerciseId: String,
        weightKg: Double,
    ) {
        deps.database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = id,
                routineId = null,
                routineName = "Push",
                date = at,
                notes = "",
                durationMinutes = 45,
                startedAt = at,
                finishedAt = at + 45L * 60L * 1000L,
            ),
        )
        deps.database.workoutDao().insertSet(
            SetLogEntity(
                id = "$id-set",
                sessionId = id,
                exerciseId = exerciseId,
                setNumber = 1,
                weightKg = weightKg,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAt = at,
            ),
        )
    }

    @Test
    fun emptyHistoryIsAvailableNotTheFaultScreen() {
        val empty = historyListFromHealth(DataHealth.Available(emptyList()))
        assertFalse(empty.unavailable)
        assertFalse(empty.stale)
        assertTrue(empty.summaries.isEmpty())
    }

    @Test
    fun unreadHistoryFailureIsUnavailableNotNoSessionsYet() {
        val unread = historyListFromHealth(DataHealth.Unavailable("workout history"))
        assertTrue(unread.unavailable)
        assertFalse(unread.stale)
        assertTrue(unread.summaries.isEmpty())
    }

    @Test
    fun laterHistoryFailureKeepsSessionsAndMarksStale() {
        val summary = com.sinura.personaltrainer.domain.SessionSummary(
            id = "s1",
            routineId = null,
            routineName = "Push",
            date = 1L,
            finishedAt = 2L,
            durationMinutes = 40,
            workingSets = 0,
            volumeKg = 0.0,
            localEpochDay = 1L,
        )
        val stale = historyListFromHealth(DataHealth.Degraded(listOf(summary), "workout history"))
        assertFalse(stale.unavailable)
        assertTrue(stale.stale)
        assertEquals("s1", stale.summaries.single().id)
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
