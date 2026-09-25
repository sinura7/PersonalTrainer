package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.FinishedWorkingSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.RestHonestyCopy
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingGoal
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

    /** Armed by a test to hold, or fail, the read of a lift's last session (and only that read). */
    @Volatile private var lastSessionReadGate: CompletableDeferred<Unit>? = null
    @Volatile private var lastSessionReadFails = false

    /** When set, [lastSessionReadGate] holds only this lift's last session. */
    @Volatile private var lastSessionReadLift: String? = null

    /** Armed by a test to hold every history read of one lift; [liftReadHeld] says one is waiting. */
    @Volatile private var liftReadGate: Pair<String, CompletableDeferred<Unit>>? = null
    @Volatile private var liftReadHeld = CompletableDeferred<Unit>()

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
                    if (lastSessionReadFails || lastSessionReadGate != null) {
                        val lastSession = Throwable().stackTrace.any { it.methodName.startsWith("lastPerformance") }
                        if (lastSession && lastSessionReadFails) error("Injected: last session could not be read")
                        val thisLift = lastSessionReadLift?.let { it in exerciseIds } ?: true
                        if (lastSession && thisLift) lastSessionReadGate?.await()
                    }
                    liftReadGate?.let { (lift, gate) ->
                        if (lift in exerciseIds) {
                            liftReadHeld.complete(Unit)
                            gate.await()
                        }
                    }
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
        lastSessionReadGate?.complete(Unit)
        liftReadGate?.second?.complete(Unit)
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

        vm.skipTheRestItShows()
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

        floor.skipTheRestItShows()
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
        vm.skipTheRestItShows()
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

        floor.skipTheRestItShows()
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
        // the prior set the page carries it too: onALiftsFirstSetTheRestPageKeepsLastSessionsRpeAsTheLogDoes.)
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

    @Test
    fun afterAnotherSetTheRestPagesNextLineIsTheLogs() = runBlocking {
        // K1 (W2b-4). The lift's one planned set, logged at RPE 7, then Another set: the Log asks
        // the coach about the extra set (add weight). The page did not read Another set, so its
        // coach still called the lift done and repeated the last set: "Next: 100 kg × 5 · RPE 7".
        val fixture = seedWorkout(targetSets = 1)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.logTheLiftsOnlyPlannedSetAtRpe(7)
        workout.requestExtraSet()
        workout.awaitCall("the Log's call for the extra set") { it?.reasonCode == SetMicroRecCalculator.IN_TANK }
        workout.startSelectedRest()
        awaitRestRunning()
        val logLine = workout.shownNextLine(workout.awaitState { !it.entryLocked })
        assertEquals("the Log shows the extra set's call", "Next: 102.5 kg × 5 · RPE 7", logLine)

        val floor = createViewModel(fixture.session.id)
        assertEquals(
            "after Another set the rest page's Next line is the Log's",
            logLine,
            floor.settledNextLine(logLine) { it.loadState == SessionLoadState.FOUND && it.rest.running },
        )
    }

    @Test
    fun onALiftsFirstSetTheRestPageKeepsLastSessionsRpeAsTheLogDoes() = runBlocking {
        // K2 (W2b-4). Last session's 100 kg × 5 at RPE 8: the Log's first-set call carries it
        // ("· RPE 8"). The page passed the coach no history and lost it.
        val fixture = seedWorkout(priorWeightKg = 100.0, priorRpe = 8)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.awaitState {
            it.loadState == SessionLoadState.FOUND && it.liftReadiness == LiftEntryReadiness.READY &&
                it.hint != null && !it.entryLocked
        }
        workout.awaitCall("the Log's first-set call with last session's RPE") { it?.nextRpe == 8 }
        workout.startSelectedRest()
        awaitRestRunning()
        val logLine = workout.shownNextLine(workout.awaitState { !it.entryLocked })
        assertEquals("the Log shows last session's RPE", "Next: 102.5 kg × 5 · RPE 8", logLine)

        val floor = createViewModel(fixture.session.id)
        assertEquals(
            "on a lift's first set the rest page's Next line is the Log's, RPE included",
            logLine,
            floor.settledNextLine(logLine) { it.loadState == SessionLoadState.FOUND && it.rest.running },
        )
    }

    @Test
    fun afterTheLiftsLastPlannedSetTheRestPageShowsNoNextLineAsTheLogShowsNone() = runBlocking {
        // W2b-4. The Log hides its Next card once the lift's planned sets are done (unless
        // Another set); the page went on printing the last set as "Next: 100 kg × 5".
        val fixture = seedWorkout(targetSets = 1)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.logTheLiftsOnlyPlannedSetAtRpe(null)
        workout.awaitCall("the Log's call after the lift's planned sets") {
            it?.reasonCode == SetMicroRecCalculator.LIFT_DONE
        }
        assertNull("the Log shows no Next card", workout.shownNextLine(workout.awaitState { !it.entryLocked }))

        val floor = createViewModel(fixture.session.id)
        assertNull(
            "after the lift's last planned set the rest page shows no Next line",
            floor.settledNextLine(null) { it.loadState == SessionLoadState.FOUND && it.floor.lastSetLine != null },
        )
    }

    @Test
    fun whileASetIsOpenForCorrectionTheRestPageShowsNoNextLineAsTheLogShowsNone() = runBlocking {
        // W2b-4. The Log's coach makes no call while a set is open for correction; the page
        // asked as if none were open and printed "Next: 100 kg × 5".
        val fixture = seedWorkout(targetSets = 3)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.logTheFirstSet()
        awaitRestRunning()
        val setId = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single().id
        workout.editSet(setId)
        workout.awaitState { it.editingSetId == setId && !it.entryLocked }
        workout.awaitCall("no call from the Log's coach while the set is open") { it == null }
        assertNull("the Log shows no Next card", workout.shownNextLine(workout.awaitState { it.editingSetId == setId }))

        val floor = createViewModel(fixture.session.id)
        assertNull(
            "while a set is open for correction the rest page shows no Next line",
            floor.settledNextLine(null) { it.loadState == SessionLoadState.FOUND && it.floor.lastSetLine != null },
        )
    }

    @Test
    fun onAWarmUpEntryTheRestPageShowsNoNextLineAsTheLogShowsNone() = runBlocking {
        // W2b-4. With a warm-up in the entry the Log shows the ramp, not its Next card; the page
        // printed the first working set's call.
        val fixture = seedWorkout(targetSets = 3)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.awaitState {
            it.loadState == SessionLoadState.FOUND && it.liftReadiness == LiftEntryReadiness.READY &&
                it.draft.weightKg == 100.0 && !it.entryLocked
        }
        workout.setWarmup(true)
        val entry = workout.awaitState { it.draft.isWarmup && !it.entryLocked }
        workout.awaitCall("the Log's call for the first working set") { it != null }
        assertNull("the Log shows no Next card on a warm-up", workout.shownNextLine(entry))

        val floor = createViewModel(fixture.session.id)
        assertNull(
            "on a warm-up entry the rest page shows no Next line",
            floor.settledNextLine(null) { it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == "Squat" },
        )
    }

    @Test
    fun afterAnotherSetTheRestPageStillPlansTheLengthTheDockShows() = runBlocking {
        // W2b-4 moves the Next line, not the planned length (owner decision, 24 September 2026).
        // After Another set the Log's call is the extra set's, which rests 2:00, but the dock
        // still plans the finished lift's 2:30, and so does the page, as it did before W2b-4.
        val fixture = seedWorkout(targetSets = 1)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.logTheLiftsOnlyPlannedSetAtRpe(7)
        workout.requestExtraSet()
        val call = workout.awaitCall("the Log's call for the extra set") {
            it?.reasonCode == SetMicroRecCalculator.IN_TANK
        }
        assertEquals("the extra set's call rests 2:00", 120, checkNotNull(call).restSeconds)
        assertFalse("the lift's last planned set starts no rest", deps.restTimerStore.current().running)
        assertEquals("the dock plans 2:30", 150, workout.restTimerState.awaitFirst { !it.running }.totalSeconds)

        val floor = createViewModel(fixture.session.id)
        val planned = withTimeoutOrNull(TestWaits.FLOW_MS) {
            floor.uiState.first { it.loadState == SessionLoadState.FOUND && it.rest.totalSeconds == 150 }
        }
        assertEquals(
            "after Another set the rest page still plans the dock's 2:30",
            150,
            (planned ?: floor.uiState.value).rest.totalSeconds,
        )
    }

    @Test
    fun whileLastSessionIsStillBeingReadTheRestPagePlansTheCoachsLength() = runBlocking {
        // W2b-4 review, F1. Last session's sets colour only the Next line's RPE, and the length
        // does not read them. Read before the seed, a slow read held the page at the 1:30
        // default, and Start ran 1:30 where it runs the coach's 2:30.
        val fixture = seedWorkout(priorWeightKg = 100.0, priorRpe = 8)
        lastSessionReadGate = CompletableDeferred()
        val floor = createViewModel(fixture.session.id)
        val planned = withTimeoutOrNull(TestWaits.FLOW_MS) {
            floor.uiState.first { it.loadState == SessionLoadState.FOUND && it.rest.totalSeconds == 150 }
        }
        assertEquals(
            "with last session still being read the page plans the coach's 2:30",
            150,
            (planned ?: floor.uiState.value).rest.totalSeconds,
        )
        floor.startSelectedRest()
        awaitRestRunning()
        assertEquals("Start runs the planned 2:30", 150, deps.restTimerStore.current().totalSeconds)
    }

    @Test
    fun whenLastSessionCannotBeReadTheRestPageStillPlansTheCoachsLength() = runBlocking {
        // W2b-4 review, F1: a failed read of last session left the page at 1:30 for the visit.
        val fixture = seedWorkout(priorWeightKg = 100.0, priorRpe = 8)
        lastSessionReadFails = true
        val floor = createViewModel(fixture.session.id)
        val planned = withTimeoutOrNull(TestWaits.FLOW_MS) {
            floor.uiState.first { it.loadState == SessionLoadState.FOUND && it.rest.totalSeconds == 150 }
        }
        assertEquals(
            "with last session unreadable the page still plans the coach's 2:30",
            150,
            (planned ?: floor.uiState.value).rest.totalSeconds,
        )
    }

    @Test
    fun aPageOpenedAsTheLogMovesToAnotherLiftShowsThatLiftsCall() = runBlocking {
        // W2b-4 review, F2 (adversarial S4b). The Log moves from Squat to Bench, a lift not yet
        // visited, and the page opens while the Log is still reading Bench. The page read Squat's
        // hint and last session once, at open, then drew Bench with them for the whole visit:
        // "Next: 102.5 kg × 5 · RPE 8" where the Log said "Next: 60 kg × 5".
        val fixture = seedSquatThenBench(squatSets = 3, priorSquatRpe = 8)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.awaitState {
            it.loadState == SessionLoadState.FOUND && it.liftReadiness == LiftEntryReadiness.READY &&
                it.hint != null && !it.entryLocked
        }
        workout.awaitCall("the Log's first Squat call with last session's RPE") { it?.nextRpe == 8 }
        workout.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        awaitRestRunning()

        val logReadsBench = CompletableDeferred<Unit>().also { liftReadGate = BENCH to it }
        workout.selectExercise(BENCH)
        workout.awaitState { it.selectedExerciseId == BENCH }
        assertTrue("the Log is reading Bench", withTimeoutOrNull(TestWaits.FLOW_MS) { liftReadHeld.await() } != null)
        // The page's read of Squat's last session is held, so nothing it reads for Squat lands
        // mid-switch and redraws it on Bench before the page's own Bench read is watched.
        lastSessionReadLift = SQUAT
        lastSessionReadGate = CompletableDeferred()
        val floor = createViewModel(fixture.session.id)
        floor.awaitPage("the page on Squat, the lift the Log last wrote, with Squat's hint") {
            it.floor.exerciseName == "Squat" && it.floor.sessionTargetLine?.startsWith("Next: 102.5 kg") == true
        }

        liftReadGate = null
        logReadsBench.complete(Unit)
        val bench = workout.awaitState {
            it.selectedExerciseId == BENCH && it.liftReadiness == LiftEntryReadiness.READY &&
                it.draft.weightKg == 60.0 && !it.entryLocked
        }
        val logLine = workout.shownNextLine(bench)
        assertEquals("the Log's Bench call", "Next: 60 kg × 5", logLine)

        // The page's own read of Bench now waits. A +15 redraws the page, as each second of the
        // clock does on the phone.
        liftReadHeld = CompletableDeferred()
        val pageReadsBench = CompletableDeferred<Unit>().also { liftReadGate = BENCH to it }
        floor.adjustRest(15)
        assertEquals(
            "while it reads Bench the page shows Bench's call with nothing read, as the Log does",
            logLine,
            floor.settledNextLine(logLine) { it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == "Bench" },
        )
        assertTrue("the page reads Bench", withTimeoutOrNull(TestWaits.FLOW_MS) { liftReadHeld.await() } != null)
        pageReadsBench.complete(Unit)
        liftReadGate = null
        floor.adjustRest(15)
        assertEquals(
            "with Bench read the page's line is still the Log's",
            logLine,
            floor.settledNextLine(logLine) { it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == "Bench" },
        )
    }

    @Test
    fun anotherSetOnOneLiftDoesNotReachTheRestPageForAnother() = runBlocking {
        // W2b-4 review, A1. Squat's Another set stays on Squat's entry after the Log moves on;
        // the page reads Another set from the entry of the lift it shows, Bench, which is done.
        val fixture = seedSquatThenBench(squatSets = 1)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.logOnePlannedSet(SQUAT, weightKg = 100.0, rpe = 7)
        workout.requestExtraSet()
        workout.awaitCall("the Log's call for Squat's extra set") { it?.reasonCode == SetMicroRecCalculator.IN_TANK }
        workout.selectExercise(BENCH)
        workout.logOnePlannedSet(BENCH, weightKg = 60.0, rpe = 7)
        workout.awaitCall("the Log's call after Bench's planned set") { it?.reasonCode == SetMicroRecCalculator.LIFT_DONE }
        assertTrue(
            "Squat's entry still asks for another set",
            deps.workoutDraftCache.getLift(fixture.session.id, SQUAT)?.extraSetRequested == true,
        )
        assertNull("the Log shows no Next card for Bench", workout.shownNextLine(workout.awaitState { !it.entryLocked }))

        val floor = createViewModel(fixture.session.id)
        assertNull(
            "Bench is done and nobody asked it for another set, so the page shows no Next line",
            floor.settledNextLine(null) {
                it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == "Bench" && it.floor.lastSetLine != null
            },
        )
    }

    @Test
    fun aTimedHoldWithAnRpeGetsTheLogsCallOnTheRestPage() = runBlocking {
        // W2b-4 review, F3. A hold's entry counts no reps; the page read it as one. The coach's
        // answer does not read the entry's reps (the Log asks with the RPE as intent), so the two
        // lines agreed before too: this holds the page to the Log's entry, not a visible fix.
        val fixture = seedDeadHang()
        val workout = createWorkoutViewModel(fixture.session.id)
        val ready = workout.awaitState {
            it.loadState == SessionLoadState.FOUND && it.liftReadiness == LiftEntryReadiness.READY &&
                it.draft.durationSeconds != null && !it.entryLocked
        }
        assertEquals("a hold's entry counts no reps", 0, ready.draft.reps)
        workout.setRpe(8)
        val entry = workout.awaitState { it.draft.rpe == 8 && !it.entryLocked }
        workout.awaitCall("the Log's call for the next hold") { it != null }
        val logLine = workout.shownNextLine(entry)
        assertTrue("the Log shows a Next card for the hold", logLine != null)

        val floor = createViewModel(fixture.session.id)
        assertEquals(
            "for a timed hold with an RPE the rest page's Next line is the Log's",
            logLine,
            floor.settledNextLine(logLine) { it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == "Dead Hang" },
        )
    }

    @Test
    fun whileAFailedSaveWaitsForRetryTheRestPageShowsNoNextLineAsTheLogShowsNone() = runBlocking {
        // W2b-4 review, F4. A failed save holds the Log's entry until Retry, and the Log hides its
        // Next card for as long; the page printed the call behind it.
        val fixture = seedWorkout(targetSets = 3)
        val workout = createWorkoutViewModel(fixture.session.id, container = failingInsert())
        workout.awaitState {
            it.loadState == SessionLoadState.FOUND && it.liftReadiness == LiftEntryReadiness.READY &&
                it.draft.weightKg == 100.0 && !it.entryLocked
        }
        workout.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        val entry = workout.awaitState { it.save.phase == WorkoutSavePhase.FAILED }
        assertTrue("the failed save holds the entry", entry.entryLocked)
        workout.awaitCall("the Log's call behind the lock") { it != null }
        assertNull("the Log shows no Next card while the save waits", workout.shownNextLine(entry))

        val floor = createViewModel(fixture.session.id)
        assertNull(
            "while a failed save waits for Retry the rest page shows no Next line",
            floor.settledNextLine(null) { it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == "Squat" },
        )
    }

    @Test
    fun withAStrengthGoalAndALighterWeekTheRestPagesNextLineIsTheLogs() = runBlocking {
        // W2b-4 review, T1. A lighter week turns "had more in you, add weight" into a hold; the
        // page must ask with it. The goal and emphasis travel too, but they change only the
        // call's wording, which the page does not show; NextSetInputsTest keeps them from being
        // left out silently.
        deps.preferencesRepository.setTrainingGoal(TrainingGoal.STRENGTH)
        deps.preferencesRepository.setTrainingEmphasis(TrainingEmphasis.LOWER)
        val fixture = seedWorkout(targetSets = 3)
        deps.preferencesRepository.setLighterWeekStartEpochDay(
            ProgressionHintLoader(deps, fixture.session.id).thisWeekStart(),
        )
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.logOnePlannedSet(SQUAT, weightKg = 100.0, rpe = 7)
        awaitRestRunning()
        workout.awaitCall("the Log's lighter-week call: hold where it would add weight") {
            it?.reasonCode == SetMicroRecCalculator.LIGHTER_HOLD
        }
        val logLine = workout.shownNextLine(workout.awaitState { !it.entryLocked })
        assertTrue("the Log shows its lighter-week call", logLine != null)

        val floor = createViewModel(fixture.session.id)
        assertEquals(
            "in a lighter week the rest page's Next line is the Log's",
            logLine,
            floor.settledNextLine(logLine) { it.loadState == SessionLoadState.FOUND && it.rest.running },
        )
    }

    private fun createViewModel(sessionId: String): RestTimerViewModel =
        RestTimerViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(viewModels::add)

    private fun createWorkoutViewModel(sessionId: String, container: AppDependencies = deps): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = container,
        ).also(workoutViewModels::add)

    /** A copy of the graph whose set insert always throws; every read, and the draft cache, is shared. */
    private fun failingInsert(): AppDependencies {
        val repo = WorkoutRepository(
            deps.database,
            object : WorkoutDao by deps.database.workoutDao() {
                override suspend fun insertSet(set: SetLogEntity) {
                    error("Injected write failure")
                }
            },
        )
        return object : AppDependencies by deps {
            override val workoutRepository: WorkoutRepository = repo
        }
    }

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

    /** The page's Skip as the page taps it: it names the running rest the page shows (W2b-3). */
    private suspend fun RestTimerViewModel.skipTheRestItShows() {
        skipRest(awaitState { it.rest.running }.rest.timerId)
    }

    private suspend fun ActiveWorkoutViewModel.awaitState(
        predicate: (ActiveWorkoutUiState) -> Boolean,
    ): ActiveWorkoutUiState = withTimeout(TestWaits.FLOW_MS) {
        uiState.first(predicate)
    }

    /** The Log on [liftId], prefilled at [weightKg]; logs one set at [rpe] and settles. */
    private suspend fun ActiveWorkoutViewModel.logOnePlannedSet(liftId: String, weightKg: Double, rpe: Int?) {
        awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == liftId &&
                it.liftReadiness == LiftEntryReadiness.READY && it.draft.weightKg == weightKg && !it.entryLocked
        }
        if (rpe != null) {
            setRpe(rpe)
            awaitState { it.draft.rpe == rpe }
        }
        logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
    }

    /** The rest page's state once it is the one [what] names, waited for with a ceiling. */
    private suspend fun RestTimerViewModel.awaitPage(
        what: String,
        predicate: (RestTimerScreenState) -> Boolean,
    ): RestTimerScreenState = withTimeoutOrNull(TestWaits.FLOW_MS) { uiState.first(predicate) }
        ?: throw AssertionError("Never saw $what; the page showed ${uiState.value}")

    /** The Log, prefilled at the routine's 100 kg × 5; logs its one planned set at [rpe] and settles. */
    private suspend fun ActiveWorkoutViewModel.logTheLiftsOnlyPlannedSetAtRpe(rpe: Int?) {
        awaitState {
            it.loadState == SessionLoadState.FOUND && it.liftReadiness == LiftEntryReadiness.READY &&
                it.draft.weightKg == 100.0 && !it.entryLocked
        }
        if (rpe != null) {
            setRpe(rpe)
            awaitState { it.draft.rpe == rpe }
        }
        logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        awaitState { it.session?.sets?.size == 1 && !it.entryLocked }
    }

    /** The Log, prefilled at the routine's 100 kg × 5; logs the lift's first set and settles. */
    private suspend fun ActiveWorkoutViewModel.logTheFirstSet() {
        awaitState {
            it.loadState == SessionLoadState.FOUND && it.liftReadiness == LiftEntryReadiness.READY &&
                it.draft.weightKg == 100.0 && !it.entryLocked
        }
        logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        awaitState { it.session?.sets?.size == 1 && !it.entryLocked }
    }

    /** The Log's coach call once it is the one [what] names, waited for with a ceiling. */
    private suspend fun ActiveWorkoutViewModel.awaitCall(
        what: String,
        predicate: (SetMicroRec?) -> Boolean,
    ): SetMicroRec? {
        var matched: SetMicroRec? = null
        withTimeoutOrNull(TestWaits.FLOW_MS) { matched = microRec.first(predicate); true }
            ?: throw AssertionError("Never saw $what; the Log's call was ${microRec.value}")
        return matched
    }

    /**
     * The Log's Next line as its card shows it, or null where the card is hidden: after the
     * lift's planned sets, on a warm-up entry, while the entry is locked, or with no call. Spelled
     * out from the Log's screen here rather than taken from [shownNextSet], so the rest page is
     * held to the Log and not to itself.
     */
    private fun ActiveWorkoutViewModel.shownNextLine(entry: ActiveWorkoutUiState): String? {
        val call = microRec.value ?: return null
        val shown = !entry.entryLocked && !entry.draft.isWarmup && SetMicroRecCopy.visibleOnEntry(call)
        val loadClass = entry.selectedExerciseId?.let { entry.session?.loadClassOf(it) } ?: LoadClass.LOADED
        return if (shown) SetMicroRecCopy.line(call, loadClass, WeightUnit.KG) else null
    }

    /**
     * The rest page's Next line once the page has [loaded] and shows [expected], waited for with
     * a ceiling; if it never does, the line it last showed. Fails if the page never loaded, so a
     * page that never read the session cannot pass for one that shows no line.
     */
    private suspend fun RestTimerViewModel.settledNextLine(
        expected: String?,
        loaded: (RestTimerScreenState) -> Boolean,
    ): String? {
        val settled = withTimeoutOrNull(TestWaits.FLOW_MS) {
            uiState.first { loaded(it) && it.floor.sessionTargetLine == expected }
        }
        val last = settled ?: uiState.value
        assertTrue("the rest page never loaded; its last state was $last", loaded(last))
        return last.floor.sessionTargetLine
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
        priorRpe: Int? = null,
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
                rpe = priorRpe,
                isWarmup = false,
            )
            deps.workoutRepository.finishSession(prior.id, notes = "")
        }
        return SeededWorkout(deps.workoutRepository.startRoutine(routine))
    }

    /** Squat (100 kg × 5) then Bench (60 kg × 5, one set), with last session's Squat at [priorSquatRpe]. */
    private suspend fun seedSquatThenBench(squatSets: Int, priorSquatRpe: Int? = null): SeededWorkout {
        insertExercise(SQUAT, "Squat")
        insertExercise(BENCH, "Bench")
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Full", notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        deps.database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-$SQUAT",
                routineId = ROUTINE,
                exerciseId = SQUAT,
                sortOrder = 0,
                targetSets = squatSets,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 90,
            ),
        )
        deps.database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-$BENCH",
                routineId = ROUTINE,
                exerciseId = BENCH,
                sortOrder = 1,
                targetSets = 1,
                targetReps = 5,
                targetWeightKg = 60.0,
                restSeconds = 90,
            ),
        )
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE))
        if (priorSquatRpe != null) {
            val prior = deps.workoutRepository.startRoutine(routine)
            deps.workoutRepository.logSet(
                sessionId = prior.id,
                exerciseId = SQUAT,
                weightKg = 100.0,
                reps = 5,
                rpe = priorSquatRpe,
                isWarmup = false,
            )
            deps.workoutRepository.finishSession(prior.id, notes = "")
        }
        return SeededWorkout(deps.workoutRepository.startRoutine(routine))
    }

    /** A dead hang, a timed hold of 30 s, with one 30 s hold already logged today. */
    private suspend fun seedDeadHang(): SeededWorkout {
        deps.database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = HANG,
                    name = "Dead Hang",
                    muscleGroup = "Back",
                    notes = "",
                    isCustom = false,
                    loadType = "BODYWEIGHT",
                    nameKey = "dead hang",
                ),
            ),
        )
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Hangs", notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        deps.database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-$HANG",
                routineId = ROUTINE,
                exerciseId = HANG,
                sortOrder = 0,
                targetSets = 3,
                targetReps = 1,
                targetWeightKg = null,
                restSeconds = 60,
                targetSeconds = 30,
            ),
        )
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE))
        val live = deps.workoutRepository.startRoutine(routine)
        deps.workoutRepository.logSet(
            sessionId = live.id,
            exerciseId = HANG,
            weightKg = 0.0,
            reps = 0,
            rpe = null,
            isWarmup = false,
            durationSeconds = 30,
        )
        return SeededWorkout(checkNotNull(deps.workoutRepository.getSession(live.id)))
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
        const val BENCH = "bench"
        const val HANG = "dead-hang"
        const val ROUTINE = "routine-rest-floor"
        const val STAMP = 1_700_000_000_000L
    }
}
