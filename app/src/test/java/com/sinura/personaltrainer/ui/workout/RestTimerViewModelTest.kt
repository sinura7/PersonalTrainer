package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.FinishedWorkingSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.RestHonestyCopy
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.theme.Motion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestTimerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<RestTimerViewModel>()
    private val workoutViewModels = mutableListOf<ActiveWorkoutViewModel>()
    /** Armed by a test to hold the floor's coach read after it has read Room; null is a pass-through. */
    private var coachReadGate: CompletableDeferred<Unit>? = null
    private val coachReadHeld = CompletableDeferred<Unit>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { real -> object : WorkoutDao by real {
                override suspend fun finishedWorkingSetsForExercises(
                    exerciseIds: List<String>,
                ): List<FinishedWorkingSetRow> {
                    val rows = real.finishedWorkingSetsForExercises(exerciseIds)
                    coachReadGate?.let { gate -> coachReadHeld.complete(Unit); gate.await() }
                    return rows
                }
            } },
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        coachReadGate?.complete(Unit)
        runBlocking {
            viewModels.forEach { it.clearAndJoinForTest() }
            workoutViewModels.forEach { it.clearAndJoinForTest() }
        }
        if (::deps.isInitialized) deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun blankSessionIdResolvesMissing() = runBlocking {
        val vm = createViewModel("")
        val state = vm.awaitState { it.loadState == SessionLoadState.MISSING }
        assertNull(state.floor.exerciseName)
    }

    @Test
    fun liveSessionShowsExerciseAndStartsTheSharedClock() = runBlocking {
        val fixture = seedWorkout(restSeconds = 75)
        val vm = createViewModel(fixture.session.id)
        val state = vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == "Squat"
        }
        assertEquals("Squat", state.floor.exerciseName)
        assertNull(state.floor.lastSetLine)

        // Wait for the number startSelectedRest will actually read. Prefill seeds
        // restTotal from the prescribed starting rest (150 for a heavy five), not
        // the 75 stamped on the routine. Start before that lands and the clock
        // is the 90 s default.
        vm.awaitState { it.rest.totalSeconds == 150 }
        vm.startSelectedRest()
        awaitRestRunning()
        val rest = deps.restTimerStore.current()
        assertEquals(fixture.session.id, rest.sessionId)
        assertEquals(150, rest.totalSeconds)

        vm.skipRest()
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun lastSetLineAppearsAfterAWorkingSetOnTheSharedStore() = runBlocking {
        val fixture = seedWorkout(targetSets = 3, restSeconds = 90)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        workout.logSet()
        workout.awaitState { !it.logging }
        dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS.toLong())
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceUntilIdle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        awaitRestRunning()

        val floor = createViewModel(fixture.session.id)
        val state = floor.awaitState {
            it.loadState == SessionLoadState.FOUND &&
                it.floor.lastSetLine != null &&
                it.floor.sessionTargetLine != null
        }
        assertEquals("Last set · 100 kg × 5", state.floor.lastSetLine)
        assertEquals("Next: 100 kg × 5", state.floor.sessionTargetLine)
        assertTrue(state.rest.running)
        assertEquals(fixture.session.id, deps.restTimerStore.current().sessionId)

        floor.skipRest()
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun finishedSessionIsMissingOnTheFloor() = runBlocking {
        val fixture = seedWorkout()
        deps.workoutRepository.logSet(
            sessionId = fixture.session.id,
            exerciseId = SQUAT,
            weightKg = 100.0,
            reps = 5,
            rpe = null,
            isWarmup = false,
        )
        deps.workoutRepository.finishSession(fixture.session.id, notes = "")
        val vm = createViewModel(fixture.session.id)
        val state = vm.awaitState { it.loadState == SessionLoadState.MISSING }
        assertNull(state.floor.lastSetLine)
    }

    @Test
    fun floorAdjustHitsTheSameStoreAsTheLog() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND }
        vm.startSelectedRest()
        awaitRestRunning()
        vm.adjustRest(15)
        assertTrue(deps.restTimerStore.current().totalSeconds >= 90)
        vm.skipRest()
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun anUnsavedRestReachesTheFloorStateAndClearsWhenTheRowLands() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        val vm = createViewModel(fixture.session.id)
        val healthy = vm.awaitState { it.loadState == SessionLoadState.FOUND }
        assertTrue(healthy.rest.persistenceHealthy)

        deps.setRestPersistenceHealthy(false)
        val unsaved = vm.awaitState { !it.rest.persistenceHealthy }
        assertFalse(unsaved.rest.persistenceHealthy)

        deps.setRestPersistenceHealthy(true)
        val landed = vm.awaitState { it.rest.persistenceHealthy }
        assertTrue(landed.rest.persistenceHealthy)
    }

    @Test
    fun selectingDurationOnTheFloorUpdatesTheLogPlannedRest() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        val workout = createWorkoutViewModel(fixture.session.id)
        // READY, not FOUND, so this test is about the floor reaching the Log, not load timing.
        // A length chosen before READY used to be replaced by prefill's seed (the coach's 2:30
        // for five reps), and the wait below for 105 ran out (23 Sept). Both pages now keep a
        // length someone picked: see the two "…OutlivesThe…" tests.
        workout.awaitState { it.loadState == SessionLoadState.FOUND && it.liftReadiness == LiftEntryReadiness.READY }
        val floor = createViewModel(fixture.session.id)
        floor.awaitState { it.loadState == SessionLoadState.FOUND }
        workout.restTimerState.awaitFirst { !it.running && it.totalSeconds > 0 }
        deps.preferencesRepository.restTimerPreferences.first()

        floor.selectRestDuration(105)
        withTimeout(TestWaits.FLOW_MS) {
            workout.restTimerState.first { it.totalSeconds == 105 && !it.running }
        }
        // The floor's uiState is its own combine of the same clock and can publish a beat
        // after the workout's flow just awaited; wait on it rather than read it.
        assertEquals(105, floor.awaitState { it.rest.totalSeconds == 105 }.rest.totalSeconds)
    }

    @Test
    fun aLengthPickedWhileTheFloorIsStillLoadingOutlivesTheCoachSeed() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        // Hold the floor's load in its coach read, the last slow step before it seeds the
        // prescribed 2:30. The page is already FOUND and its chips already answer a tap —
        // the window a slow phone, or a busy test JVM on 23 Sept, opens by chance.
        val gate = CompletableDeferred<Unit>().also { coachReadGate = it }
        val floor = createViewModel(fixture.session.id)
        floor.awaitState { it.loadState == SessionLoadState.FOUND }
        withTimeout(TestWaits.FLOW_MS) { coachReadHeld.await() }

        floor.selectRestDuration(105)
        // Main is unconfined here, so the held load resumes on this thread and runs to its
        // seed before complete() returns: what follows reads the settled plan.
        gate.complete(Unit)

        assertEquals(105, floor.uiState.value.rest.totalSeconds)
        floor.startSelectedRest()
        awaitRestRunning()
        assertEquals(105, deps.restTimerStore.current().totalSeconds)
    }

    @Test
    fun exactDeniedSurfacesBestEffortNeverPrecise() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        deps.setExactAlarmAttempt(ExactAlarmAttempt.BEST_EFFORT)
        val vm = createViewModel(fixture.session.id)
        val state = vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.rest.exactAlarmBestEffort
        }
        assertTrue(state.rest.exactAlarmBestEffort)
        assertFalse(RestHonestyCopy.EXACT_DENIED.contains("precise", ignoreCase = true))
    }

    @Test
    fun aPickWhileTheRestRunsNamesTheNextRestNotTheRunningOne() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        val floor = createViewModel(fixture.session.id)
        floor.awaitState { it.loadState == SessionLoadState.FOUND && it.rest.totalSeconds == 150 }
        floor.startSelectedRest()
        awaitRestRunning()

        floor.selectRestDuration(105)
        val running = floor.awaitState { it.rest.running }
        assertEquals("a running rest shows its own length", 150, running.rest.totalSeconds)

        floor.skipRest()
        val idle = floor.awaitState { !it.rest.running }
        assertEquals("once it ends, the page names the pick", 105, idle.rest.totalSeconds)
    }

    @Test
    fun theStoredLastPresetIsNotANewPickWhenThePageOpens() = runBlocking {
        // The last preset saved before the page opened is not an echo of anything: the page
        // seeds its own coach length (2:30 for a heavy five), not the 1:45 saved earlier.
        val fixture = seedWorkout(restSeconds = 90)
        deps.preferencesRepository.setLastRestPresetSeconds(105)
        val floor = createViewModel(fixture.session.id)

        val seeded = withTimeoutOrNull(TestWaits.FLOW_MS) {
            floor.uiState.first { it.loadState == SessionLoadState.FOUND && it.rest.totalSeconds == 150 }
        }
        assertEquals(
            "the page seeds the coach's length, not the preset it found saved",
            150,
            (seeded ?: floor.uiState.value).rest.totalSeconds,
        )
    }

    @Test
    fun aSettingChangeIsNotAPickSoTheOpenPageKeepsItsLength() = runBlocking {
        // Only a new last preset is news. Turning the rest sound off rewrites the same stored
        // preferences with the saved 1:45 unchanged; the open page must keep its 2:30 rather
        // than hear 1:45 as a pick (the W2b-2 adversarial review: dropping the first value
        // before filtering repeats did exactly that).
        val fixture = seedWorkout(restSeconds = 90)
        deps.preferencesRepository.setLastRestPresetSeconds(105)
        val floor = createViewModel(fixture.session.id)
        floor.awaitState { it.loadState == SessionLoadState.FOUND && it.rest.totalSeconds == 150 }

        deps.preferencesRepository.setRestSoundEnabled(false)
        deps.preferencesRepository.restTimerPreferences.awaitFirst { !it.soundEnabled && it.lastPresetSeconds == 105 }
        dispatcher.scheduler.runCurrent()
        assertEquals("a setting change is not a pick", 150, floor.uiState.value.rest.totalSeconds)
    }

    @Test
    fun aDockPickWhileTheRestPageIsStillLoadingReachesIt() = runBlocking {
        // The page hears the dock's picks from the moment it opens, not once it has loaded: a
        // length picked on the Log while the page's load is held in its coach read is the
        // page's next rest when the load lands (the W2b-2 adversarial review: a page that
        // began listening after its seed kept its own length instead).
        val fixture = seedWorkout(restSeconds = 90)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.awaitState {
            it.loadState == SessionLoadState.FOUND && it.liftReadiness == LiftEntryReadiness.READY && !it.entryLocked
        }
        val gate = CompletableDeferred<Unit>().also { coachReadGate = it }
        val floor = createViewModel(fixture.session.id)
        floor.awaitState { it.loadState == SessionLoadState.FOUND }
        withTimeout(TestWaits.FLOW_MS) { coachReadHeld.await() }

        workout.selectRestDuration(90)
        deps.preferencesRepository.restTimerPreferences.awaitFirst { it.lastPresetSeconds == 90 }
        gate.complete(Unit)

        val loaded = withTimeoutOrNull(TestWaits.FLOW_MS) { floor.uiState.first { it.rest.totalSeconds == 90 } }
        assertEquals(
            "a dock pick made while the page loaded is the page's next rest",
            90,
            (loaded ?: floor.uiState.value).rest.totalSeconds,
        )
    }

    @Test
    fun aDockPickReachesAnOpenRestPageButNotWhileARestRuns() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.awaitState {
            it.loadState == SessionLoadState.FOUND && it.liftReadiness == LiftEntryReadiness.READY && !it.entryLocked
        }
        val floor = createViewModel(fixture.session.id)
        floor.awaitState { it.loadState == SessionLoadState.FOUND && it.rest.totalSeconds == 150 }

        workout.selectRestDuration(105)
        val echoed = withTimeoutOrNull(TestWaits.FLOW_MS) {
            floor.uiState.first { it.rest.totalSeconds == 105 }
        }
        assertEquals("a pick on the dock reaches the open rest page", 105, (echoed ?: floor.uiState.value).rest.totalSeconds)

        workout.startSelectedRest()
        awaitRestRunning()
        // The echo of this pick reaches the page while the rest still runs: the fake preference
        // store writes on the unconfined test dispatcher, so the page hears it before the Skip
        // below, as in the Log's own echo test.
        workout.selectRestDuration(120)
        deps.preferencesRepository.restTimerPreferences.awaitFirst { it.lastPresetSeconds == 120 }
        workout.skipRest()
        val idle = floor.awaitState { !it.rest.running }
        assertEquals("a pick heard while a rest ran is not the page's next rest", 105, idle.rest.totalSeconds)
    }

    @Test
    fun customLengthAndTheBatteryLineOnTheRestPage() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        val floor = createViewModel(fixture.session.id)
        floor.awaitState { it.loadState == SessionLoadState.FOUND && it.rest.totalSeconds == 150 }

        assertTrue(floor.selectCustomRest("2:15"))
        assertEquals(135, floor.awaitState { it.rest.totalSeconds == 135 }.rest.totalSeconds)
        assertFalse(floor.selectCustomRest("abc"))
        assertEquals(135, floor.uiState.value.rest.totalSeconds)
        deps.preferencesRepository.restTimerPreferences.awaitFirst { it.lastPresetSeconds == 135 }

        floor.startSelectedRest()
        awaitRestRunning()
        assertEquals(135, deps.restTimerStore.current().totalSeconds)
        val warned = withTimeoutOrNull(TestWaits.FLOW_MS) {
            floor.uiState.first { it.rest.running && it.rest.batteryHint }
        }
        assertTrue("the first rest names the battery rule", (warned ?: floor.uiState.value).rest.batteryHint)

        floor.acknowledgeRestBatteryHint()
        val read = withTimeoutOrNull(TestWaits.FLOW_MS) {
            floor.uiState.first { it.rest.running && !it.rest.batteryHint }
        }
        assertFalse("an acknowledged battery line goes", (read ?: floor.uiState.value).rest.batteryHint)
        assertTrue("acknowledging it leaves the rest running", deps.restTimerStore.current().running)
    }

    @Test
    fun theRestPagesNextLineIsTheLogsFirstSetCallForALiftWithHistory() = runBlocking {
        // A prior 100 kg × 5 with no RPE: the hint moves the first set's call off the routine's
        // 100 kg, so the page can only match the Log by loading the same hint. (With an RPE on
        // the prior set the two lines differ today; that is packet W2b-4, not this test.)
        val fixture = seedWorkout(restSeconds = 90, priorWeightKg = 100.0)
        val workout = createWorkoutViewModel(fixture.session.id)
        val ready = workout.awaitState {
            it.loadState == SessionLoadState.FOUND && it.liftReadiness == LiftEntryReadiness.READY && it.hint != null
        }
        val suggested = checkNotNull(ready.hint).suggestedWeightKg
        assertTrue("the history must move the call, or this test proves nothing", suggested != 100.0)
        val rec = checkNotNull(
            withTimeout(TestWaits.FLOW_MS) {
                workout.microRec.first { it != null && it.nextWeightKg == suggested }
            },
        )
        val logLine = SetMicroRecCopy.line(rec, LoadClass.LOADED, WeightUnit.KG)

        val floor = createViewModel(fixture.session.id)
        val page = withTimeoutOrNull(TestWaits.FLOW_MS) {
            floor.uiState.first { it.floor.sessionTargetLine == logLine }
        }
        assertEquals(
            "the rest page's Next line is the Log's",
            logLine,
            (page ?: floor.uiState.value).floor.sessionTargetLine,
        )
    }

    private fun createViewModel(sessionId: String): RestTimerViewModel =
        RestTimerViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(viewModels::add)

    private fun createWorkoutViewModel(sessionId: String): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(workoutViewModels::add)

    private suspend fun awaitRestRunning() {
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (!deps.restTimerStore.current().running) {
                    // The rest after a logged set waits Motion.ROW_SETTLE_MS on the virtual clock,
                    // and that delay is scheduled only when Room's write returns — on a real
                    // thread, possibly after the test's one advance. Waiting on the store alone
                    // then waits for a delay nothing will ever run (it timed out on a loaded
                    // machine, 23 September 2026). Drive it while yielding to Room, as
                    // ActiveWorkoutViewModelTest does; the store stays the success condition.
                    dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS.toLong())
                    dispatcher.scheduler.runCurrent()
                    if (!deps.restTimerStore.current().running) delay(10)
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError(
                "Rest did not start; snapshot=${deps.restTimerStore.current()}, " +
                    "virtualTime=${dispatcher.scheduler.currentTime}",
                timedOut,
            )
        }
    }

    private suspend fun RestTimerViewModel.awaitState(
        predicate: (RestTimerScreenState) -> Boolean,
    ): RestTimerScreenState = withTimeout(TestWaits.FLOW_MS) {
        uiState.first(predicate)
    }

    private suspend fun ActiveWorkoutViewModel.awaitState(
        predicate: (ActiveWorkoutUiState) -> Boolean,
    ): ActiveWorkoutUiState = withTimeout(TestWaits.FLOW_MS) {
        uiState.first(predicate)
    }

    private suspend fun awaitSession(
        sessionId: String,
        predicate: (WorkoutSession) -> Boolean,
    ): WorkoutSession = withTimeout(TestWaits.FLOW_MS) {
        checkNotNull(
            deps.workoutRepository.observeSession(sessionId).first { session ->
                session != null && predicate(session)
            },
        )
    }

    private suspend fun seedWorkout(
        targetSets: Int = 3,
        restSeconds: Int = 90,
        priorWeightKg: Double? = null,
    ): SeededWorkout {
        insertExercise(SQUAT, "Squat")
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(
                id = ROUTINE,
                name = "Lower",
                notes = "",
                createdAt = STAMP,
                updatedAt = STAMP,
            ),
        )
        deps.database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-$SQUAT",
                routineId = ROUTINE,
                exerciseId = SQUAT,
                sortOrder = 0,
                targetSets = targetSets,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = restSeconds,
            ),
        )
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE))
        if (priorWeightKg != null) {
            val prior = deps.workoutRepository.startRoutine(routine)
            deps.workoutRepository.logSet(
                sessionId = prior.id,
                exerciseId = SQUAT,
                weightKg = priorWeightKg,
                reps = 5,
                rpe = null,
                isWarmup = false,
            )
            deps.workoutRepository.finishSession(prior.id, notes = "")
        }
        return SeededWorkout(deps.workoutRepository.startRoutine(routine))
    }

    private suspend fun insertExercise(id: String, name: String) {
        deps.database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = id,
                    name = name,
                    muscleGroup = "Legs",
                    notes = "",
                    isCustom = false,
                    nameKey = name.lowercase(),
                ),
            ),
        )
    }

    private data class SeededWorkout(val session: WorkoutSession)

    private companion object {
        const val SQUAT = "squat"
        const val ROUTINE = "routine-rest-floor"
        const val STAMP = 1_700_000_000_000L
    }
}
