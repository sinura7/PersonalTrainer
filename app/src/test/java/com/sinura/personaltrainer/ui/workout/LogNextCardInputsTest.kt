package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.FinishedWorkingSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestWaits
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Log's Next card follows each thing its coach reads (W2c review). W2c asks the coach again
 * only when what it reads changes, so each test here changes one of those things after the card
 * is drawn, and nothing else, and the card must follow: the coaching goal, a lighter week, the
 * lift's hint, the unit, and the lift's own targets. The last test holds the unit the Log's
 * receipt and undo offer name to the setting too.
 *
 * Two inputs reach the Log only when it opens a lift, in its load of that lift: the week, and
 * the hint. The load reads the week, then the hint, then last session; last session's sets are
 * themselves an input, so they would carry any change before them onto the card. Those two tests
 * hold a read so the one change lands on a card already drawn: the week's read until the lift is
 * drawn, and last session's read while the hint lands, as a slow phone would.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LogNextCardInputsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val logs = mutableListOf<ActiveWorkoutViewModel>()

    /** Armed by a test to hold every settings read, the Log's read of the week among them. */
    @Volatile private var settingsReadGate: CompletableDeferred<Unit>? = null

    /** Armed by a test to hold the read of a lift's hint; null is a pass-through. */
    @Volatile private var hintReadGate: CompletableDeferred<Unit>? = null

    /** Armed by a test to hold the read of a lift's last session; null is a pass-through. */
    @Volatile private var lastSessionReadGate: CompletableDeferred<Unit>? = null

    /**
     * Set when a read of a lift's last session reached [lastSessionReadGate] while it was armed.
     * The gate knows that read by its caller's name, so a renamed read would slip past it; the
     * test that arms the gate checks this instead of trusting it.
     */
    @Volatile private var lastSessionReadHeld = false

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            prefsStoreDecorator = { real ->
                object : DataStore<Preferences> by real {
                    override val data: Flow<Preferences> = real.data.onEach { settingsReadGate?.await() }
                }
            },
            workoutDaoDecorator = { real ->
                object : WorkoutDao by real {
                    override suspend fun finishedWorkingSetsForExercises(
                        exerciseIds: List<String>,
                    ): List<FinishedWorkingSetRow> {
                        val callers = Throwable().stackTrace.map { it.methodName }
                        if (callers.any { it.startsWith("progressionFor") }) hintReadGate?.await()
                        if (callers.any { it.startsWith("lastPerformance") }) {
                            lastSessionReadGate?.let { gate ->
                                lastSessionReadHeld = true
                                gate.await()
                            }
                        }
                        return real.finishedWorkingSetsForExercises(exerciseIds)
                    }
                }
            },
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        settingsReadGate?.complete(Unit)
        hintReadGate?.complete(Unit)
        lastSessionReadGate?.complete(Unit)
        runBlocking { logs.forEach { it.clearAndJoinForTest() } }
        if (::deps.isInitialized) deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aNewCoachingGoalReachesTheWhySheetOfTheLogsNextCard() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5)
        val log = createLog(fixture.session.id)
        log.logOneSet(SQUAT, weightKg = 100.0, rpe = 7)
        val general = checkNotNull(
            log.awaitCall("the Log's call after 100 kg × 5 at RPE 7: add weight") {
                it?.reasonCode == SetMicroRecCalculator.IN_TANK
            },
        )
        assertEquals("the general goal's Rule line", "Rule: Had more in you — add weight", general.ruleLine())

        deps.preferencesRepository.setTrainingGoal(TrainingGoal.STRENGTH)
        val strength = checkNotNull(
            log.awaitCall("the Log's call in the strength goal's words") {
                it?.ruleLine()?.contains("strength bias") == true
            },
        )
        assertEquals(
            "a strength goal reaches the Why sheet's Rule line of the Log's Next card",
            "Rule: Had more in you — add weight · strength bias keeps reps before big jumps",
            strength.ruleLine(),
        )
    }

    @Test
    fun aWeekMarkedLighterReachesTheLogsCallWhenItsLiftIsOpenedAgain() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, BENCH_3X5)
        val log = createLog(fixture.session.id)
        log.logOneSet(SQUAT, weightKg = 100.0, rpe = 7)
        log.awaitCall("the Log's call after 100 kg × 5 at RPE 7: add weight") {
            it?.reasonCode == SetMicroRecCalculator.IN_TANK
        }
        log.selectExercise(BENCH)
        log.awaitReady(BENCH, weightKg = 60.0)
        val thisWeek = ProgressionHintLoader(deps, fixture.session.id).thisWeekStart()

        // The week is marked lighter; the Log reads it when it opens Squat again. That read
        // waits until Squat is drawn, so the lighter week is the one thing that changes after.
        val weekRead = CompletableDeferred<Unit>().also { settingsReadGate = it }
        deps.preferencesRepository.setLighterWeekStartEpochDay(thisWeek)
        log.selectExercise(SQUAT)
        log.awaitCall("the Log's Squat call, drawn before it reads the week") {
            it?.reasonCode == SetMicroRecCalculator.IN_TANK
        }
        assertEquals(
            "Squat drawn again, the week not yet read: add weight",
            "Next: 102.5 kg × 5 · RPE 7",
            log.shownNextLine(log.awaitState { it.selectedExerciseId == SQUAT && !it.entryLocked }),
        )

        settingsReadGate = null
        weekRead.complete(Unit)
        log.awaitCall("the Log's lighter-week call: hold where it would add weight") {
            it?.reasonCode == SetMicroRecCalculator.LIGHTER_HOLD
        }
        assertEquals(
            "in a lighter week the Log's Next card holds the weight",
            "Next: 100 kg × 5 · RPE 7",
            log.shownNextLine(log.awaitReady(SQUAT, weightKg = null)),
        )
    }

    @Test
    fun aLiftsHintReachesTheLogsNextCardWhileLastSessionIsStillBeingRead() = runBlocking {
        // Last session's Squat was 100 kg × 5 with no RPE, so the hint moves the first set's call
        // off the routine's 100 kg; last session's sets would add nothing to it but an RPE.
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val hintRead = CompletableDeferred<Unit>().also { hintReadGate = it }
        lastSessionReadGate = CompletableDeferred()
        val log = createLog(fixture.session.id)
        log.awaitCall("the Log's first-set call before the hint is read") {
            it?.reasonCode == SetMicroRecCalculator.FIRST_SET
        }
        assertEquals(
            "before its hint is read the Log calls the routine's 100 kg",
            "Next: 100 kg × 5",
            log.shownNextLine(log.awaitState { it.selectedExerciseId == SQUAT && !it.entryLocked }),
        )

        hintRead.complete(Unit)
        val hinted = log.awaitState { it.hint != null && !it.entryLocked }
        assertEquals("the hint moves the first set to 102.5 kg", 102.5, checkNotNull(hinted.hint).suggestedWeightKg, 0.0)
        log.awaitCall("the Log's first-set call from the hint, 102.5 kg") { it?.nextWeightKg == 102.5 }
        assertEquals(
            "with last session still being read the Log's Next card already takes the hint",
            "Next: 102.5 kg × 5",
            log.shownNextLine(log.awaitState { it.hint != null && !it.entryLocked }),
        )
        assertNotNull(
            "the Log's read of last session never reached its gate, so last session's sets were " +
                "not held back and the hint was not the one thing that changed",
            withTimeoutOrNull(TestWaits.FLOW_MS) {
                while (!lastSessionReadHeld) delay(10)
            },
        )
        lastSessionReadGate?.complete(Unit)
        log.awaitReady(SQUAT, weightKg = 102.5)
        Unit
    }

    @Test
    fun aUnitChangeReachesTheLogsNextCard() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5)
        val log = createLog(fixture.session.id)
        log.logOneSet(SQUAT, weightKg = 100.0, rpe = 7)
        log.awaitCall("the Log's call after 100 kg × 5 at RPE 7: add weight") {
            it?.reasonCode == SetMicroRecCalculator.IN_TANK
        }
        val entry = log.awaitState { !it.entryLocked }
        assertEquals("in kilograms the call adds the kilogram step", "Next: 102.5 kg × 5 · RPE 7", log.shownNextLine(entry))

        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        log.awaitCall("the Log's call asked in pounds: 225.5 lb") {
            it != null && SetMicroRecCopy.line(it, LoadClass.LOADED, WeightUnit.LBS) == "Next: 225.5 lb × 5 · RPE 7"
        }
        assertEquals(
            "in pounds the Log's Next card adds the pound step",
            "Next: 225.5 lb × 5 · RPE 7",
            log.shownNextLine(log.awaitState { !it.entryLocked }, WeightUnit.LBS),
        )
    }

    @Test
    fun switchingToALiftWithOtherTargetsShowsThatLiftsOwnCall() = runBlocking {
        // Squat 3 × 5 at 100 kg and Bench 3 × 8 at 60 kg: both barbell lifts, neither trained
        // before, nothing logged today. The coach reads the same of both but their targets.
        val fixture = seedWorkout(SQUAT_3X5, BENCH_3X8)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 100.0)
        log.awaitCall("the Log's Squat call") { it?.nextWeightKg == 100.0 && it.nextReps == 5 }
        assertEquals("the Squat's first set", "Next: 100 kg × 5", log.shownNextLine(log.awaitState { !it.entryLocked }))

        log.selectExercise(BENCH)
        val bench = log.awaitReady(BENCH, weightKg = 60.0)
        log.awaitCall("the Log's Bench call: 60 kg × 8") { it?.nextWeightKg == 60.0 && it.nextReps == 8 }
        assertEquals("the Bench's first set, not the Squat's", "Next: 60 kg × 8", log.shownNextLine(bench))
    }

    @Test
    fun aUnitChangeReachesTheLogReceiptAndTheUndoOffer() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 100.0)

        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        log.awaitCall("the Log's call asked in pounds") {
            it != null && SetMicroRecCopy.whyLines(it).any { line -> " lb" in line }
        }
        log.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        val receipt = withTimeoutOrNull(TestWaits.FLOW_MS) { log.logReceipt.first { it != null } }
            ?: throw AssertionError("Never saw the log receipt; the Log showed ${log.uiState.value}")
        assertEquals(
            "the log receipt names the set in pounds",
            "Working set 1 of 3 logged · 220.5 lb × 5",
            receipt.line,
        )

        log.awaitEntryUnlocked()
        log.deleteSet(receipt.setId)
        val offer = withTimeoutOrNull(TestWaits.FLOW_MS) { log.undoEntries.first { it.isNotEmpty() } }?.last()?.offer
            ?: throw AssertionError("Never saw the undo offer; the Log showed ${log.uiState.value}")
        assertEquals("the undo offer names the deleted set in pounds", "Set deleted · 220.5 lb × 5", offer.message)
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun createLog(sessionId: String): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(logs::add)

    /** The Log on [liftId] with its load done, at [weightKg] if given; waited for with a ceiling. */
    private suspend fun ActiveWorkoutViewModel.awaitReady(liftId: String, weightKg: Double?): ActiveWorkoutUiState =
        awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == liftId &&
                it.liftReadiness == LiftEntryReadiness.READY && !it.entryLocked &&
                (weightKg == null || it.draft.weightKg == weightKg)
        }

    /** The Log on [liftId], prefilled at [weightKg]; logs one set at [rpe] and settles. */
    private suspend fun ActiveWorkoutViewModel.logOneSet(liftId: String, weightKg: Double, rpe: Int?) {
        awaitReady(liftId, weightKg)
        if (rpe != null) {
            setRpe(rpe)
            awaitState { it.draft.rpe == rpe }
        }
        logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        awaitState { entry -> entry.session?.sets?.any { it.exerciseId == liftId } == true && !entry.entryLocked }
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

    /** The Log's Next line as its card shows it in [unit], or null where the card is hidden. */
    private fun ActiveWorkoutViewModel.shownNextLine(
        entry: ActiveWorkoutUiState,
        unit: WeightUnit = WeightUnit.KG,
    ): String? {
        val call = microRec.value ?: return null
        val shown = !entry.entryLocked && !entry.draft.isWarmup && SetMicroRecCopy.visibleOnEntry(call)
        val loadClass = entry.selectedExerciseId?.let { entry.session?.loadClassOf(it) } ?: LoadClass.LOADED
        return if (shown) SetMicroRecCopy.line(call, loadClass, unit) else null
    }

    /** The Rule line of the call's Why sheet, where the goal's words are shown. */
    private fun SetMicroRec.ruleLine(): String? = SetMicroRecCopy.whyLines(this).firstOrNull { it.startsWith("Rule: ") }

    /** A workout on [lifts], in order, after last session's Squat at [priorSquatKg] × 5 when given. */
    private suspend fun seedWorkout(vararg lifts: PlannedLift, priorSquatKg: Double? = null): SeededWorkout {
        deps.database.exerciseDao().insertAll(
            lifts.map { lift ->
                ExerciseEntity(
                    id = lift.id,
                    name = lift.name,
                    muscleGroup = "Legs",
                    notes = "",
                    isCustom = false,
                    loadType = "EXTERNAL",
                    nameKey = lift.name.lowercase(),
                )
            },
        )
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Full", notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        lifts.forEachIndexed { index, lift ->
            deps.database.routineDao().upsertRoutineExercise(
                RoutineExerciseEntity(
                    id = "re-${lift.id}",
                    routineId = ROUTINE,
                    exerciseId = lift.id,
                    sortOrder = index,
                    targetSets = 3,
                    targetReps = lift.reps,
                    targetWeightKg = lift.weightKg,
                    restSeconds = 90,
                ),
            )
        }
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE))
        if (priorSquatKg != null) {
            val prior = deps.workoutRepository.startRoutine(routine)
            deps.workoutRepository.logSet(
                sessionId = prior.id,
                exerciseId = SQUAT,
                weightKg = priorSquatKg,
                reps = 5,
                rpe = null,
                isWarmup = false,
            )
            deps.workoutRepository.finishSession(prior.id, notes = "")
        }
        return SeededWorkout(deps.workoutRepository.startRoutine(routine))
    }

    private data class PlannedLift(val id: String, val name: String, val reps: Int, val weightKg: Double)

    private data class SeededWorkout(val session: WorkoutSession)

    private companion object {
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val ROUTINE = "routine-full"
        const val STAMP = 1_700_000_000_000L
        val SQUAT_3X5 = PlannedLift(id = SQUAT, name = "Squat", reps = 5, weightKg = 100.0)
        val BENCH_3X5 = PlannedLift(id = BENCH, name = "Bench", reps = 5, weightKg = 60.0)
        val BENCH_3X8 = PlannedLift(id = BENCH, name = "Bench", reps = 8, weightKg = 60.0)
    }
}
