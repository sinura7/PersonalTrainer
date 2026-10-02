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
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import com.sinura.personaltrainer.ui.theme.Motion
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Log follows a unit or a week's mark changed while a lift is open (W2e): the open lift's hint
 * is read again, once, and an entry nobody touched takes the new suggestion (owner decision of 25
 * September 2026: "untouched pre-filled numbers follow the new suggestion; typed numbers stay").
 * The guards hold what must not move: a typed number, the entry after a logged set, a warm-up, a
 * set open for correction, a save held for Retry, a lift with only a routine weight, a hold, the
 * dock's planned rest, a Log that reads the hint again for anything else, and a lift switched to
 * while the old one's hint is still being read.
 *
 * The settings are written the way Settings, sync and Body write them, through the preferences
 * repository, while the Log is open. The workout DAO counts the reads of a lift's hint (its caller
 * is `progressionFor`, the one path to a suggested weight) and can hold one lift's.
 *
 * A guard first gives the re-read a bounded chance to land ([TestWaits.FLOW_MS]; before W2e nothing
 * read the hint again, so there the wait runs out) and then watches for [NEVER_MS] that the thing
 * it guards does not move.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LogFollowsSettingsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val logs = mutableListOf<ActiveWorkoutViewModel>()

    /** Reads of a lift's hint that reached the DAO, counted as each starts. */
    private val hintReads = AtomicInteger(0)

    /** Reads of a lift's hint that have ended at the DAO: returned, failed, or cancelled while held. */
    private val hintReadsDone = AtomicInteger(0)

    /** Reads of a lift's hint that [failHintReads] made fail. */
    private val hintReadsFailed = AtomicInteger(0)

    /** While set, every read of a lift's hint throws, as a database that cannot answer would. */
    @Volatile private var failHintReads = false

    /** Armed by a test to hold every read of one lift's hint; null is a pass-through. */
    @Volatile private var hintGate: Pair<String, CompletableDeferred<Unit>>? = null

    /** Completed when a read of the gated lift's hint reached [hintGate]. */
    private val hintHeld = CompletableDeferred<Unit>()

    /** Armed by a test to hold the load's read of last session, after the hint; null is a pass-through. */
    @Volatile private var lastSessionGate: CompletableDeferred<Unit>? = null

    /** Completed when a read of last session reached [lastSessionGate]. */
    private val lastSessionHeld = CompletableDeferred<Unit>()

    /**
     * Armed by a test to hold every session row the workout's flow delivers, as a slow Room
     * delivery does: a save can then be acknowledged before the session carries its set.
     */
    @Volatile private var rowGate: CompletableDeferred<Unit>? = null

    /** While set, every session row the workout's flow delivers is dropped, as a skipped emission is. */
    @Volatile private var dropRows = false

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { real ->
                object : WorkoutDao by real {
                    override suspend fun finishedWorkingSetsForExercises(
                        exerciseIds: List<String>,
                    ): List<FinishedWorkingSetRow> {
                        val callers = Throwable().stackTrace.map { it.methodName }
                        if (callers.none { it.startsWith("progressionFor") }) {
                            if (callers.any { it.startsWith("lastPerformance") }) {
                                lastSessionGate?.let { gate ->
                                    lastSessionHeld.complete(Unit)
                                    gate.await()
                                }
                            }
                            return real.finishedWorkingSetsForExercises(exerciseIds)
                        }
                        hintReads.incrementAndGet()
                        try {
                            hintGate?.let { (lift, gate) ->
                                if (lift in exerciseIds) {
                                    hintHeld.complete(Unit)
                                    gate.await()
                                }
                            }
                            if (failHintReads) {
                                hintReadsFailed.incrementAndGet()
                                error("Injected: the hint could not be read")
                            }
                            return real.finishedWorkingSetsForExercises(exerciseIds)
                        } finally {
                            hintReadsDone.incrementAndGet()
                        }
                    }

                    override fun observeSession(id: String): Flow<SessionWithDetails?> =
                        real.observeSession(id).onEach { rowGate?.await() }.filter { !dropRows }
                }
            },
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        hintGate?.second?.complete(Unit)
        lastSessionGate?.complete(Unit)
        rowGate?.complete(Unit)
        runBlocking { logs.forEach { it.clearAndJoinForTest() } }
        if (::deps.isInitialized) deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    // --- Red before W2e -------------------------------------------------------------------------

    @Test
    fun aWeekMarkedLighterWhileALiftIsOpenHoldsItsFirstSetAtLastTimesWeight() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)
        log.awaitCall("the Squat's first-set call from last time's 100 kg × 5: 102.5 kg") { it?.nextWeightKg == 102.5 }
        assertEquals("before the week is marked", "Next: 102.5 kg × 5", log.shownNextLine(log.awaitState { !it.entryLocked }))

        markThisWeekLighter(fixture.session.id)
        log.awaitCall("the Squat's first-set call in a lighter week: last time's 100 kg") { it?.nextWeightKg == 100.0 }
        val held = log.awaitLog("the Log on the Squat's lighter-week hint") { it.hint?.lighterHold == true && !it.entryLocked }
        assertEquals(
            "in a lighter week the open Squat's Next card holds last time's weight",
            "Next: 100 kg × 5",
            log.shownNextLine(held),
        )
    }

    @Test
    fun aUnitSwitchedWhileALiftIsOpenMovesItsFirstSetToThePoundStep() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        val before = log.awaitReady(SQUAT, weightKg = 102.5)
        val history = log.exerciseHistory.value
        assertNotNull("precondition: the Squat's last session is on screen", before.lastPerformance)
        assertTrue("precondition: the Squat's history is on screen", history.isNotEmpty())
        assertEquals("precondition: the load read the Squat's hint once", 1, hintReads.get())

        switchToPounds()
        log.awaitCall("the Squat's first-set call on the pound step: 225.5 lb, 102.3 kg") { it?.nextWeightKg == 102.3 }
        val after = log.awaitLog("the Log on the Squat's pound hint") { it.hint?.suggestedWeightKg == 102.3 && !it.entryLocked }
        assertEquals(
            "in pounds the open Squat's Next card adds the pound step",
            "Next: 225.5 lb × 5",
            log.shownNextLine(after, WeightUnit.LBS),
        )
        val hint = checkNotNull(after.hint)
        assertEquals("the pound hint's suggestion", 102.3, hint.suggestedWeightKg, 0.0)
        assertEquals("the pound hint is read from the same last session", 100.0, hint.lastWeightKg, 0.0)
        assertEquals("the switch read the Squat's hint once more, and only that", 2, hintReads.get())
        assertSame("last session is not read again", before.lastPerformance, after.lastPerformance)
        assertSame("the Squat's history is not read again", history, log.exerciseHistory.value)
    }

    @Test
    fun aUnitSwitchedWhileTheLiftIsStillLoadingEndsOnThePoundStep() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val gate = CompletableDeferred<Unit>().also { hintGate = SQUAT to it }
        val log = createLog(fixture.session.id)
        awaitHintHeld("the Log's load of the Squat never reached its hint read")
        log.awaitCall("the Squat's first-set call from the routine while its hint is read: 100 kg") { it?.nextWeightKg == 100.0 }

        switchToPounds()
        hintGate = null
        gate.complete(Unit)
        val loaded = log.awaitLog("the Squat loaded on the pound step, its entry 102.3 kg") {
            it.hint?.suggestedWeightKg == 102.3 && it.draft.weightKg == 102.3 &&
                it.liftReadiness == LiftEntryReadiness.READY && !it.entryLocked
        }
        log.awaitCall("the Squat's first-set call on the pound step: 102.3 kg") { it?.nextWeightKg == 102.3 }
        assertEquals(
            "a unit switched during the load ends on the pound step",
            "Next: 225.5 lb × 5",
            log.shownNextLine(loaded, WeightUnit.LBS),
        )
    }

    @Test
    fun anUntouchedEntryFollowsThePoundStepWhenTheUnitChanges() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        val ready = log.awaitReady(SQUAT, weightKg = 102.5)
        assertFalse("precondition: nobody touched the entry", ready.draftDirty)

        switchToPounds()
        val followed = log.awaitLog("the untouched entry on the pound suggestion, 102.3 kg") {
            it.draft.weightKg == 102.3 && it.hint?.suggestedWeightKg == 102.3 && !it.entryLocked
        }
        assertFalse("a number the app moved is still untouched", followed.draftDirty)
        assertEquals("the entry reads the pound suggestion", "225.5", displayed(followed.draft.weightKg, WeightUnit.LBS))
        val call = checkNotNull(log.awaitCall("the Squat's first-set call on the pound step") { it?.nextWeightKg == 102.3 })
        assertTrue(
            "the entry equals the Next card, so the card reads as applied",
            call.isApplied(followed.draft.weightKg, followed.draft.reps, followed.draft.rpe, WeightUnit.LBS),
        )
    }

    @Test
    fun anUntouchedEntryFollowsALighterWeekToLastTimesWeight() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        val ready = log.awaitReady(SQUAT, weightKg = 102.5)
        assertFalse("precondition: nobody touched the entry", ready.draftDirty)

        markThisWeekLighter(fixture.session.id)
        val followed = log.awaitLog("the untouched entry on the lighter-week suggestion, last time's 100 kg") {
            it.draft.weightKg == 100.0 && it.hint?.lighterHold == true && !it.entryLocked
        }
        assertFalse("a number the app moved is still untouched", followed.draftDirty)
        assertEquals("the lighter week's suggestion is last time's weight", 100.0, checkNotNull(followed.hint).suggestedWeightKg, 0.0)
    }

    // --- Guards: green before and after W2e ----------------------------------------------------

    @Test
    fun aWeightTypedBeforeTheUnitChangesStaysInTheEntry() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)
        log.setWeight(105.0)
        log.setRpe(8)
        log.awaitLog("the typed 105 kg at RPE 8") { it.draft.weightKg == 105.0 && it.draft.rpe == 8 && it.draftDirty }

        switchToPounds()
        log.optionalReread { it.hint?.suggestedWeightKg == 102.3 }
        log.neverShows("the typed entry moved") { it.draft.weightKg != 105.0 || it.draft.rpe != 8 || !it.draftDirty }
        val after = log.awaitState { !it.entryLocked }
        assertEquals("a typed weight stays", 105.0, after.draft.weightKg, 0.0)
        assertEquals("a chosen effort stays", 8, after.draft.rpe)
        assertTrue("the entry is still typed", after.draftDirty)
    }

    @Test
    fun anEntryAfterALoggedSetStaysWhenTheUnitChanges() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)
        log.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        log.awaitEntryUnlocked()
        log.awaitLog("the entry after the logged 102.5 kg, never touched") { state ->
            state.session?.sets?.any { it.exerciseId == SQUAT && !it.isWarmup } == true &&
                state.draft.weightKg == 102.5 && !state.draftDirty && !state.entryLocked
        }

        switchToPounds()
        log.optionalReread { it.hint?.suggestedWeightKg == 102.3 }
        log.neverShows("the entry after a logged set moved") { it.draft.weightKg != 102.5 || it.draftDirty }
        val after = log.awaitState { !it.entryLocked }
        assertEquals("the set just done stays in the entry", 102.5, after.draft.weightKg, 0.0)
        assertFalse("the entry is still untouched", after.draftDirty)
    }

    @Test
    fun aWarmUpBroughtBackFromACorrectionStaysWhenTheUnitChanges() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0, warmUpTodayKg = 60.0)
        val warmUpId = checkNotNull(fixture.warmUpId)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)
        log.editSet(warmUpId)
        log.awaitLog("the warm-up open for correction") { it.editingSetId == warmUpId && it.draft.isWarmup && !it.entryLocked }
        log.cancelEdit()
        val back = log.awaitLog("the warm-up's values left in the entry after Cancel") {
            it.editingSetId == null && it.draft.isWarmup && it.draft.weightKg == 60.0 && !it.entryLocked
        }
        assertFalse("precondition: a correction cancelled leaves the entry untouched", back.draftDirty)

        switchToPounds()
        log.optionalReread { it.hint?.suggestedWeightKg == 102.3 }
        log.neverShows("the warm-up entry moved") { it.draft.weightKg != 60.0 || !it.draft.isWarmup }
        val after = log.awaitState { !it.entryLocked }
        assertEquals("the warm-up's weight stays", 60.0, after.draft.weightKg, 0.0)
        assertTrue("the entry is still a warm-up", after.draft.isWarmup)
    }

    @Test
    fun aSetOpenForCorrectionKeepsItsValuesWhenTheUnitChanges() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)
        log.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        val logged = log.awaitLog("the Squat's logged set") { state ->
            state.session?.sets?.any { it.exerciseId == SQUAT && !it.isWarmup } == true && !state.entryLocked
        }
        val setId = checkNotNull(logged.session).sets.first { it.exerciseId == SQUAT && !it.isWarmup }.id
        log.editSet(setId)
        log.awaitLog("the logged set open for correction") { it.editingSetId == setId && !it.entryLocked }
        log.setWeight(97.5)
        log.awaitLog("the correction at 97.5 kg") { it.editingSetId == setId && it.draft.weightKg == 97.5 }

        switchToPounds()
        log.optionalReread { it.hint?.suggestedWeightKg == 102.3 }
        log.neverShows("the correction moved") { it.editingSetId != setId || it.draft.weightKg != 97.5 }
        val after = log.awaitState { !it.entryLocked }
        assertEquals("the set is still open for correction", setId, after.editingSetId)
        assertEquals("the correction's weight stays", 97.5, after.draft.weightKg, 0.0)
        assertNull("the coach makes no call while a set is corrected", log.microRec.value)
    }

    @Test
    fun aSaveHeldForRetryKeepsItsValuesWhenTheUnitChanges() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id, container = failingInsert())
        log.awaitReady(SQUAT, weightKg = 102.5)
        log.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        val held = log.awaitLog("the failed save waiting for Retry") { it.save.phase == WorkoutSavePhase.FAILED }
        assertFalse("precondition: a failed save leaves the entry untouched", held.draftDirty)

        switchToPounds()
        log.optionalReread { it.hint?.suggestedWeightKg == 102.3 }
        log.neverShows("the entry behind the held save moved") { it.draft.weightKg != 102.5 }
        val after = log.awaitState { it.save.phase == WorkoutSavePhase.FAILED }
        assertEquals("the save still waits for Retry", WorkoutSavePhase.FAILED, after.save.phase)
        assertEquals("Retry still writes the 102.5 kg", 102.5, checkNotNull(after.save.command).values.weightKg, 0.0)
        assertEquals("the entry still shows what Retry will write", 102.5, after.draft.weightKg, 0.0)
        assertTrue("the entry is still held", after.entryLocked)
    }

    @Test
    fun aLiftWithOnlyARoutineWeightKeepsItExactlyWhenTheUnitChanges() = runBlocking {
        val fixture = seedWorkout(BENCH_3X5)
        val log = createLog(fixture.session.id)
        val ready = log.awaitReady(BENCH, weightKg = 60.0)
        assertNull("precondition: a lift never trained has no hint", ready.hint)
        assertEquals("precondition: the load read the Bench's hint once", 1, hintReads.get())

        switchToPounds()
        withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReadsDone.get() < 2) delay(10) }
        log.neverShows("the routine's weight moved") { it.draft.weightKg != 60.0 || it.draftDirty }
        log.awaitCall("the Bench's first-set call in pounds: 132.5 lb") {
            it != null && SetMicroRecCopy.line(it, LoadClass.LOADED, WeightUnit.LBS) == "Next: 132.5 lb × 5"
        }
        val after = log.awaitState { !it.entryLocked }
        assertEquals("the routine's 60 kg is kept exactly, never re-rounded to the pound grid", 60.0, after.draft.weightKg, 0.0)
        assertFalse("the entry is still untouched", after.draftDirty)
        assertNull("still no hint", after.hint)
        assertEquals("the Bench's Next card in pounds", "Next: 132.5 lb × 5", log.shownNextLine(after, WeightUnit.LBS))
    }

    @Test
    fun aHoldLiftGetsNoRepCallAndKeepsItsEntryWhenTheUnitChanges() = runBlocking {
        val fixture = seedWeightedPlank()
        val log = createLog(fixture.session.id)
        val ready = log.awaitLog("the plank loaded as a 30 s hold with its hint") {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == PLANK &&
                it.liftReadiness == LiftEntryReadiness.READY && it.draft.durationSeconds == 30 &&
                it.hint != null && !it.entryLocked
        }
        val kgHint = checkNotNull(ready.hint).suggestedWeightKg
        assertEquals("precondition: the load filled the plank's weight from its hint", kgHint, ready.draft.weightKg, 0.0)
        assertFalse("precondition: nobody touched the entry", ready.draftDirty)
        // Read straight from the one path to a suggested weight, so the guard is not empty: were the
        // pound suggestion the same number, an entry that followed it could not move.
        val plank = checkNotNull(ready.session).exercises.first { it.exercise.id == PLANK }
        val poundHint = deps.workoutRepository.progressionFor(
            exerciseId = PLANK,
            exerciseName = plank.exercise.name,
            targetReps = plank.targetReps,
            excludeSessionId = fixture.session.id,
            loadType = plank.exercise.loadType,
            unit = WeightUnit.LBS,
            equipment = plank.exercise.equipment,
        )
        assertNotEquals(
            "precondition: in pounds the plank's suggestion is another number, so an entry that followed it would move",
            kgHint,
            checkNotNull(poundHint).suggestedWeightKg,
            0.0,
        )

        switchToPounds()
        log.optionalReread { it.hint != null && it.hint.suggestedWeightKg != kgHint }
        log.neverShows("the hold's entry moved") { it.draft.weightKg != kgHint }
        val liftIsHold = HoldWork.isHold(plank.exercise)
        assertTrue("precondition: the plank is a hold", liftIsHold)
        assertNull(
            "a hold's call counts reps, so it is not shown",
            shownNextSet(log.microRec.value, draftIsWarmup = false, liftIsHold = liftIsHold),
        )
        log.applyMicroRec()
        log.neverShows("Use moved the hold's entry") { it.draft.weightKg != kgHint }
        val after = log.awaitState { !it.entryLocked }
        assertEquals("the hold's weight stays what the load filled in", kgHint, after.draft.weightKg, 0.0)
        assertFalse("the hold's entry is still untouched", after.draftDirty)
    }

    @Test
    fun theDocksPlannedRestDoesNotMoveWhenTheWeekTurnsLighter() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, BENCH_3X5)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 100.0)
        log.setRpe(7)
        log.awaitLog("the entry at RPE 7") { it.draft.rpe == 7 }
        log.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        awaitRestRunning()
        log.skipRest()
        // The rest a log starts is priced from the call when the save is acknowledged, which can be
        // the call from before the set reached the session (2:30, not 2:00). The plan a load seeds
        // reads the session: opening the Squat again seeds it from the call after the logged set,
        // "had more in you", 2:00, where a lighter week's hold would plan 2:30.
        log.selectExercise(BENCH)
        log.awaitReady(BENCH, weightKg = 60.0)
        log.selectExercise(SQUAT)
        log.awaitReady(SQUAT, weightKg = 100.0)
        val planned = withTimeoutOrNull(TestWaits.FLOW_MS) {
            log.restTimerState.first { !it.running && it.totalSeconds == 120 }
        } ?: throw AssertionError("precondition: the dock never planned 2:00 after Skip; it showed ${log.restTimerState.value}")
        assertEquals("precondition: the dock plans the coach's 2:00", 120, planned.totalSeconds)

        val doneBefore = hintReadsDone.get()
        markThisWeekLighter(fixture.session.id)
        withTimeoutOrNull(TestWaits.FLOW_MS) { log.microRec.first { it?.reasonCode == SetMicroRecCalculator.LIGHTER_HOLD } }
        withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReadsDone.get() <= doneBefore) delay(10) }
        val moved = withTimeoutOrNull(NEVER_MS) { log.restTimerState.first { it.totalSeconds != 120 } }
        assertNull("the dock's planned rest moved when the week turned lighter: $moved", moved)
        assertEquals("the dock still plans 2:00", 120, log.restTimerState.value.totalSeconds)
    }

    @Test
    fun onlyTheUnitAndTheWeeksMarkReadTheHintAgain() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)
        assertEquals("precondition: the load read the Squat's hint once", 1, hintReads.get())
        assertHintReadsStay(1, "the Log read the hint again with nothing changed")

        val thisWeek = ProgressionHintLoader(deps, fixture.session.id).thisWeekStart()
        deps.preferencesRepository.setRestSoundEnabled(false)
        deps.preferencesRepository.setTrainingGoal(TrainingGoal.STRENGTH)
        deps.preferencesRepository.setLighterWeekStartEpochDay(thisWeek - 7)
        deps.preferencesRepository.restTimerPreferences.awaitFirst { !it.soundEnabled }
        deps.preferencesRepository.coachPreferences.awaitFirst { it.goal == TrainingGoal.STRENGTH }
        deps.preferencesRepository.lighterWeekStartEpochDay.awaitFirst { it == thisWeek - 7 }
        dispatcher.scheduler.runCurrent()
        assertHintReadsStay(1, "a rest sound, a goal or last week's mark read the hint again")

        switchToPounds()
        withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReads.get() < 2) delay(10) }
        val afterSwitch = hintReads.get()
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        deps.preferencesRepository.setRestSoundEnabled(true)
        deps.preferencesRepository.restTimerPreferences.awaitFirst { it.soundEnabled }
        dispatcher.scheduler.runCurrent()
        assertHintReadsStay(afterSwitch, "the same unit written again, or a rest sound, read the hint again")
    }

    @Test
    fun aLiftSwitchedWhileItsHintIsReReadGetsOnlyItsOwnCall() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, BENCH_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)
        val gate = CompletableDeferred<Unit>().also { hintGate = SQUAT to it }

        switchToPounds()
        val held = withTimeoutOrNull(TestWaits.FLOW_MS) { hintHeld.await() } != null
        log.selectExercise(BENCH)
        val bench = log.awaitReady(BENCH, weightKg = 60.0)
        log.awaitCall("the Bench's first-set call in pounds: 132.5 lb") {
            it != null && SetMicroRecCopy.line(it, LoadClass.LOADED, WeightUnit.LBS) == "Next: 132.5 lb × 5"
        }
        val benchLine = log.shownNextLine(bench, WeightUnit.LBS)
        assertEquals("precondition: the Bench's own Next card", "Next: 132.5 lb × 5", benchLine)

        hintGate = null
        gate.complete(Unit)
        if (held) awaitNoReadInFlight()
        log.neverShows("the Squat's hint on the Bench") { it.selectedExerciseId == BENCH && it.hint?.exerciseId == SQUAT }
        val after = log.awaitState { it.selectedExerciseId == BENCH && !it.entryLocked }
        assertEquals("the Bench's entry stays the routine's 60 kg", 60.0, after.draft.weightKg, 0.0)
        assertEquals("the Bench's Next card stays its own", benchLine, log.shownNextLine(after, WeightUnit.LBS))
    }

    // --- The review round (W2e): a set clock, a set just saved, a saved correction, a failed re-read

    @Test
    fun aSetClockKeepsTheNumbersUntilItsTimeIsLoggedOrCleared() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)
        log.startSetStopwatch()
        assertNotNull(
            "precondition: the set clock never ran",
            withTimeoutOrNull(TestWaits.FLOW_MS) { log.setStopwatch.first { it.running } },
        )

        switchToPounds()
        awaitReadsDone(2, "the switch to pounds never read the Squat's hint again")
        log.awaitCall("the Next card offering the pound step, 102.3 kg, during the set") { it?.nextWeightKg == 102.3 }
        log.neverShows("the numbers on the bar moved during the set") { it.draft.weightKg != 102.5 }
        val during = log.awaitState { !it.entryLocked }
        assertEquals("the entry stays 102.5 kg while the set clock runs", 102.5, during.draft.weightKg, 0.0)
        assertFalse("the entry is still untouched", during.draftDirty)

        // Stopped, the clock still holds the time of a set not yet logged: the numbers just lifted
        // stay, when the clock stops and through the next change.
        log.stopSetStopwatch()
        assertNotNull(
            "precondition: the stopped clock keeps its time for Log",
            withTimeoutOrNull(TestWaits.FLOW_MS) { log.setStopwatch.first { !it.running && it.used } },
        )
        log.neverShows("the refused follow was tried again when the clock stopped") { it.draft.weightKg != 102.5 }
        markThisWeekLighter(fixture.session.id)
        awaitReadsDone(3, "the lighter week never read the Squat's hint again")
        log.awaitLog("the lighter week's hint on screen, the clock stopped") { it.hint?.lighterHold == true && !it.entryLocked }
        log.neverShows("a change after Stop moved the numbers of the set not yet logged") { it.draft.weightKg != 102.5 }

        // Start rest on the dock clears the clock (as Log would); the next change follows again.
        log.startSelectedRest()
        assertNotNull(
            "precondition: Start rest never cleared the set clock",
            withTimeoutOrNull(TestWaits.FLOW_MS) { log.setStopwatch.first { !it.used } },
        )
        switchToKilograms()
        val next = log.awaitLog("the untouched entry following the next change, the lighter week's 100 kg") {
            it.draft.weightKg == 100.0 && it.hint?.lighterHold == true && !it.entryLocked
        }
        assertFalse("a number the app moved is still untouched", next.draftDirty)
    }

    @Test
    fun aSetDeletedBeforeTheWorkoutShowedItDoesNotHoldBackTheNextFollow() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val sessionId = fixture.session.id
        val log = createLog(sessionId)
        log.awaitReady(SQUAT, weightKg = 102.5)
        // The workout's rows stop reaching the Log, as a flow that skips a row can: the set is
        // saved, acknowledged and deleted before the Log ever shows it.
        dropRows = true
        log.logWorkingSet()
        val setId = withTimeoutOrNull(TestWaits.FLOW_MS) {
            var id: String? = null
            while (id == null) {
                id = deps.workoutRepository.getSession(sessionId)?.sets?.firstOrNull()?.id
                if (id == null) delay(10)
            }
            id
        } ?: throw AssertionError("the set was never stored; the Log showed ${describe(log.uiState.value)}")
        log.awaitLog("the save acknowledged, the workout not showing its row") {
            !it.entryLocked && it.save.phase == WorkoutSavePhase.IDLE && it.session?.sets.isNullOrEmpty()
        }
        log.deleteSet(setId)
        withTimeoutOrNull(TestWaits.FLOW_MS) {
            while (deps.workoutRepository.getSession(sessionId)?.sets?.isNotEmpty() == true) delay(10)
        } ?: throw AssertionError("the set was never deleted; the Log showed ${describe(log.uiState.value)}")
        log.awaitLog("the delete finished") { !it.entryLocked }
        dropRows = false
        deps.workoutRepository.updateSessionNotes(sessionId, "rows reach the Log again")
        val back = log.awaitLog("the workout's row reaching the Log again, with no set") {
            it.session?.notes == "rows reach the Log again" && it.session.sets.isEmpty() && !it.entryLocked
        }
        assertEquals("precondition: the entry is the set that was deleted", 102.5, back.draft.weightKg, 0.0)
        assertFalse("precondition: the entry was never touched", back.draftDirty)

        switchToPounds()
        val followed = log.awaitLog("the untouched entry following, nothing logged holding it back") {
            it.draft.weightKg == 102.3 && it.hint?.suggestedWeightKg == 102.3 && !it.entryLocked
        }
        assertFalse("a number the app moved is still untouched", followed.draftDirty)
    }

    @Test
    fun aSetJustSavedStaysWhenTheUnitChangesBeforeTheWorkoutShowsItsRow() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val sessionId = fixture.session.id
        val log = createLog(sessionId)
        log.awaitReady(SQUAT, weightKg = 102.5)
        val gate = CompletableDeferred<Unit>().also { rowGate = it }
        log.logWorkingSet()
        withTimeoutOrNull(TestWaits.FLOW_MS) {
            while (deps.workoutRepository.getSession(sessionId)?.sets.isNullOrEmpty()) delay(10)
        } ?: throw AssertionError("the set was never stored; the Log showed ${describe(log.uiState.value)}")
        log.awaitLog("the save acknowledged while the workout's row is held") {
            !it.entryLocked && it.save.phase == WorkoutSavePhase.IDLE && it.session?.sets.isNullOrEmpty()
        }

        switchToPounds()
        awaitReadsDone(2, "the switch to pounds never read the Squat's hint again")
        log.neverShows("the set just saved moved before the workout showed its row") { it.draft.weightKg != 102.5 }
        rowGate = null
        gate.complete(Unit)
        log.awaitLog("the workout showing the saved set") { state ->
            state.session?.sets?.any { it.exerciseId == SQUAT && !it.isWarmup } == true && !state.entryLocked
        }
        log.neverShows("the set just saved moved once its row arrived") { it.draft.weightKg != 102.5 }
        val after = log.awaitState { !it.entryLocked }
        assertEquals("the logged 102.5 kg stays in the entry", 102.5, after.draft.weightKg, 0.0)
        assertEquals("it reads as the logged set does in pounds", "226", displayed(after.draft.weightKg, WeightUnit.LBS))
        assertFalse("the entry is still untouched", after.draftDirty)
    }

    @Test
    fun aWarmUpCorrectionSavedUnchangedStaysWhenTheUnitChanges() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0, warmUpTodayKg = 60.0)
        val warmUpId = checkNotNull(fixture.warmUpId)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)
        log.editSet(warmUpId)
        log.awaitLog("the warm-up open for correction") { it.editingSetId == warmUpId && it.draft.isWarmup && !it.entryLocked }
        log.logWorkingSet()
        val saved = log.awaitLog("the correction saved unchanged") {
            it.editingSetId == null && it.save.phase == WorkoutSavePhase.IDLE && !it.entryLocked && it.draft.weightKg == 60.0
        }
        assertFalse("precondition: saving clears the entry's Warm-up", saved.draft.isWarmup)

        switchToPounds()
        log.awaitLog("the Squat's pound hint read again") { it.hint?.suggestedWeightKg == 102.3 }
        log.neverShows("the saved correction's 60 kg moved") { it.draft.weightKg != 60.0 }
        val after = log.awaitState { !it.entryLocked }
        assertEquals("the correction's 60 kg stays in the entry", 60.0, after.draft.weightKg, 0.0)
    }

    @Test
    fun aHintThatCannotBeReadAgainKeepsTheOneOnScreen() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)

        failHintReads = true
        switchToPounds()
        withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReadsFailed.get() < 1) delay(10) }
            ?: throw AssertionError("the switch to pounds never read the hint again; ${hintReads.get()} reads")
        awaitNoReadInFlight()
        log.neverShows("the failed re-read changed the Log") {
            it.loadState != SessionLoadState.FOUND || it.hint?.suggestedWeightKg != 102.5 ||
                it.liftReadiness != LiftEntryReadiness.READY || it.suggestionUnavailable || it.draft.weightKg != 102.5
        }
        val after = log.awaitState { !it.entryLocked }
        assertEquals("the Log is still on the workout", SessionLoadState.FOUND, after.loadState)
        assertEquals("the hint on screen is kept", 102.5, checkNotNull(after.hint).suggestedWeightKg, 0.0)
        assertEquals("the lift is still ready to log", LiftEntryReadiness.READY, after.liftReadiness)
        assertFalse("the suggestion is not marked missing", after.suggestionUnavailable)
        assertEquals("the entry is kept", 102.5, after.draft.weightKg, 0.0)
    }

    @Test
    fun aFailedReReadIsReadAgainOnlyWhenTheSettingsDifferFromTheHintOnScreen() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)

        failHintReads = true
        switchToPounds()
        withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReadsFailed.get() < 1) delay(10) }
            ?: throw AssertionError("the switch to pounds never read the hint again; ${hintReads.get()} reads")
        awaitNoReadInFlight()
        failHintReads = false
        switchToKilograms()
        assertHintReadsStay(2, "back to the unit the hint on screen was read under, the hint was read again")

        switchToPounds()
        log.awaitLog("the pound hint, read once the database answers, and the entry on it") {
            it.hint?.suggestedWeightKg == 102.3 && it.draft.weightKg == 102.3 && !it.entryLocked
        }
        assertEquals("pounds again read the hint once more", 3, hintReads.get())
    }

    @Test
    fun aUnitSwitchedBackBeforeItsReReadReturnsIsNotReadAgain() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)
        val gate = CompletableDeferred<Unit>().also { hintGate = SQUAT to it }

        switchToPounds()
        awaitHintHeld("the switch to pounds never read the Squat's hint again")
        switchToKilograms()
        awaitNoReadInFlight()
        hintGate = null
        gate.complete(Unit)
        assertHintReadsStay(2, "the pound read, undone before it returned, landed and was read back")
        val after = log.awaitState { !it.entryLocked }
        assertEquals("the hint on screen is the kilogram one", 102.5, checkNotNull(after.hint).suggestedWeightKg, 0.0)
        assertEquals("the entry is the kilogram suggestion", 102.5, after.draft.weightKg, 0.0)
    }

    // --- The review round: the adversarial replay's gaps --------------------------------------

    @Test
    fun aFollowedEntryIsWhatTheCacheAndSavedStateHold() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val sessionId = fixture.session.id
        val handle = SavedStateHandle(mapOf("sessionId" to sessionId))
        val log = createLog(sessionId, handle = handle)
        log.awaitReady(SQUAT, weightKg = 102.5)

        switchToPounds()
        log.awaitLog("the untouched entry on the pound suggestion, 102.3 kg") { it.draft.weightKg == 102.3 && !it.entryLocked }
        assertEquals(
            "the draft cache holds the followed entry, for the rest page and a reopen",
            102.3,
            checkNotNull(deps.workoutDraftCache.getLift(sessionId, SQUAT)).weightKg,
            0.0,
        )
        assertEquals(
            "saved state holds the followed entry, for a process death",
            102.3,
            checkNotNull(SavedStateWorkoutDraft(handle).readLift(sessionId, SQUAT)).weightKg,
            0.0,
        )
    }

    @Test
    fun aUnitSwitchedToPoundsAndBackTakesTheKilogramStepAgain() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)

        switchToPounds()
        log.awaitLog("the untouched entry on the pound suggestion, 102.3 kg") {
            it.hint?.suggestedWeightKg == 102.3 && it.draft.weightKg == 102.3 && !it.entryLocked
        }
        switchToKilograms()
        val back = log.awaitLog("the untouched entry back on the kilogram suggestion, 102.5 kg") {
            it.hint?.suggestedWeightKg == 102.5 && it.draft.weightKg == 102.5 && !it.entryLocked
        }
        log.awaitCall("the Squat's first-set call on the kilogram step") { it?.nextWeightKg == 102.5 }
        assertEquals("back in kilograms the Next card adds the kilogram step", "Next: 102.5 kg × 5", log.shownNextLine(back))
    }

    @Test
    fun aUnitSwitchedWhileTheLoadReadsLastSessionEndsOnThePoundStep() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        val gate = CompletableDeferred<Unit>().also { lastSessionGate = it }
        val log = createLog(fixture.session.id)
        assertNotNull(
            "the Log's load never reached its read of last session",
            withTimeoutOrNull(TestWaits.FLOW_MS) { lastSessionHeld.await() },
        )

        switchToPounds()
        // The load is still reading: a hint read now, before the load ends, is what this test is
        // about. Give one the time to happen (with W2e nothing reads until the load has ended).
        withTimeoutOrNull(NEVER_MS) { while (hintReads.get() < 2) delay(10) }
        if (hintReads.get() >= 2) {
            withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReadsDone.get() < hintReads.get()) delay(10) }
                ?: throw AssertionError("a hint read during the load never ended: ${hintReadsDone.get()} of ${hintReads.get()}")
        }
        lastSessionGate = null
        gate.complete(Unit)
        log.awaitLog("the Squat loaded on the pound step, its entry 102.3 kg") {
            it.hint?.suggestedWeightKg == 102.3 && it.draft.weightKg == 102.3 &&
                it.liftReadiness == LiftEntryReadiness.READY && !it.entryLocked
        }
        // The screen's state can show a moment that never settles; the entry must stay.
        log.neverShows("the load put its older kilogram suggestion back over the entry") { it.draft.weightKg != 102.3 }
        val loaded = log.awaitState { !it.entryLocked }
        log.awaitCall("the Squat's first-set call on the pound step: 102.3 kg") { it?.nextWeightKg == 102.3 }
        assertEquals("the Next card in pounds", "Next: 225.5 lb × 5", log.shownNextLine(loaded, WeightUnit.LBS))
        assertEquals("the entry reads the pound suggestion", "225.5", displayed(loaded.draft.weightKg, WeightUnit.LBS))
    }

    @Test
    fun anUntouchedEntryWithAWarmUpLoggedTodayFollowsThePoundStep() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0, warmUpTodayKg = 60.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT, weightKg = 102.5)

        switchToPounds()
        val followed = log.awaitLog("the untouched working entry on the pound suggestion, a warm-up logged before it") {
            it.draft.weightKg == 102.3 && it.hint?.suggestedWeightKg == 102.3 && !it.entryLocked
        }
        assertFalse("a number the app moved is still untouched", followed.draftDirty)
    }

    @Test
    fun aLiftWhoseLoadDegradedIsNotReadAgainLive() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        failHintReads = true
        val log = createLog(fixture.session.id)
        log.awaitLog("the Squat degraded to the routine's 100 kg, its hint unreadable") {
            it.liftReadiness == LiftEntryReadiness.DEGRADED && it.draft.weightKg == 100.0 && !it.entryLocked
        }
        failHintReads = false

        switchToPounds()
        markThisWeekLighter(fixture.session.id)
        assertHintReadsStay(1, "the degraded lift's hint was read again in the live Log")
        log.neverShows("the degraded lift changed in the live Log") {
            it.draft.weightKg != 100.0 || it.hint != null || it.liftReadiness != LiftEntryReadiness.DEGRADED
        }
        val after = log.awaitState { !it.entryLocked }
        assertEquals("the routine's weight stays in the entry", 100.0, after.draft.weightKg, 0.0)
        assertTrue("the suggestion is still marked missing", after.suggestionUnavailable)
    }

    @Test
    fun aWeekStartMovedOffTheMarkedWeekTakesTheFullStepAgain() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5, priorSquatKg = 100.0)
        markThisWeekLighter(fixture.session.id)
        val log = createLog(fixture.session.id)
        log.awaitLog("the Squat loaded in a lighter week at last time's 100 kg") {
            it.hint?.lighterHold == true && it.draft.weightKg == 100.0 &&
                it.liftReadiness == LiftEntryReadiness.READY && !it.entryLocked
        }

        val other = otherWeekStart()
        deps.preferencesRepository.setWeekStart(other)
        deps.preferencesRepository.schedulePreferences.awaitFirst { it.weekStart == other }
        val full = log.awaitLog("the untouched entry on the full step again, the marked week no longer this one") {
            it.hint?.lighterHold == false && it.hint.suggestedWeightKg == 102.5 && it.draft.weightKg == 102.5 && !it.entryLocked
        }
        assertFalse("a number the app moved is still untouched", full.draftDirty)
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun createLog(
        sessionId: String,
        container: AppDependencies = deps,
        handle: SavedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
    ): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = handle,
            container = container,
        ).also(logs::add)

    /** A copy of the graph whose set insert always throws; every read, and the draft cache, is shared. */
    private fun failingInsert(): AppDependencies {
        val repository = WorkoutRepository(
            deps.database,
            object : WorkoutDao by deps.database.workoutDao() {
                override suspend fun insertSet(set: SetLogEntity) {
                    error("Injected write failure")
                }
            },
        )
        return object : AppDependencies by deps {
            override val workoutRepository: WorkoutRepository = repository
        }
    }

    /** Switched in Settings, or pulled in by sync, while the Log is open. */
    private suspend fun switchToPounds() {
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        deps.preferencesRepository.weightUnit.awaitFirst { it == WeightUnit.LBS }
    }

    /** Switched back in Settings, or pulled in by sync, while the Log is open. */
    private suspend fun switchToKilograms() {
        deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
        deps.preferencesRepository.weightUnit.awaitFirst { it == WeightUnit.KG }
    }

    /** A first weekday whose week starts on another day than this week does. */
    private suspend fun otherWeekStart(): Weekday {
        val now = deps.preferencesRepository.schedulePreferences.first().weekStart
        val today = deps.time.civilDate(deps.time.nowMillis())
        val current = LighterWeek.weekStartEpochDay(today, now)
        return Weekday.entries.first { LighterWeek.weekStartEpochDay(today, it) != current }
    }

    /** Waits until [count] reads of a hint have ended at the DAO, or fails with [failure]. */
    private suspend fun awaitReadsDone(count: Int, failure: String) {
        withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReadsDone.get() < count) delay(10) }
            ?: throw AssertionError("$failure: ${hintReadsDone.get()} of ${hintReads.get()} reads ended, expected $count")
    }

    /** Waits, with a ceiling, until every read of a hint that started has ended. */
    private suspend fun awaitNoReadInFlight() {
        withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReadsDone.get() < hintReads.get()) delay(10) }
    }

    /** "Mark this week lighter" on Body, while the Log is open. */
    private suspend fun markThisWeekLighter(sessionId: String) {
        val thisWeek = ProgressionHintLoader(deps, sessionId).thisWeekStart()
        deps.preferencesRepository.setLighterWeekStartEpochDay(thisWeek)
        deps.preferencesRepository.lighterWeekStartEpochDay.awaitFirst { it == thisWeek }
    }

    private suspend fun awaitHintHeld(failure: String) {
        withTimeoutOrNull(TestWaits.FLOW_MS) { hintHeld.await() } ?: throw AssertionError(failure)
    }

    /** Fails if the hint is read again within [NEVER_MS] of [expected] reads. */
    private suspend fun assertHintReadsStay(expected: Int, failure: String) {
        val moved = withTimeoutOrNull(NEVER_MS) { while (hintReads.get() == expected) delay(10) }
        assertNull("$failure: ${hintReads.get()} reads, expected $expected", moved)
    }

    private suspend fun awaitRestRunning() {
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (!deps.restTimerStore.current().running) {
                    // As RestFloorInputsTest: the rest after a logged set waits on the virtual
                    // clock, scheduled only when Room's write returns on a real thread.
                    dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS.toLong())
                    dispatcher.scheduler.runCurrent()
                    if (!deps.restTimerStore.current().running) delay(10)
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Rest did not start; snapshot=${deps.restTimerStore.current()}", timedOut)
        }
    }

    /** The Log on [liftId] with its load done, at [weightKg]; waited for with a ceiling. */
    private suspend fun ActiveWorkoutViewModel.awaitReady(liftId: String, weightKg: Double): ActiveWorkoutUiState =
        awaitLog("the Log on $liftId, loaded at $weightKg kg") {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == liftId &&
                it.liftReadiness == LiftEntryReadiness.READY && !it.entryLocked && it.draft.weightKg == weightKg
        }

    /** The Log's state once it is the one [what] names, or a failure that says what it showed. */
    private suspend fun ActiveWorkoutViewModel.awaitLog(
        what: String,
        predicate: (ActiveWorkoutUiState) -> Boolean,
    ): ActiveWorkoutUiState = withTimeoutOrNull(TestWaits.FLOW_MS) { uiState.first(predicate) }
        ?: throw AssertionError("Never saw $what; the Log showed ${describe(uiState.value)}")

    /** A guard's bounded chance for the re-read to land; before W2e nothing reads again and it runs out. */
    private suspend fun ActiveWorkoutViewModel.optionalReread(predicate: (ActiveWorkoutUiState) -> Boolean) {
        withTimeoutOrNull(TestWaits.FLOW_MS) { uiState.first(predicate) }
    }

    /** Fails if the Log shows [predicate] within [NEVER_MS]. */
    private suspend fun ActiveWorkoutViewModel.neverShows(what: String, predicate: (ActiveWorkoutUiState) -> Boolean) {
        val shown = withTimeoutOrNull(NEVER_MS) { uiState.first(predicate) }
        assertNull("$what: the Log showed ${shown?.let(::describe)}", shown)
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

    private fun displayed(weightKg: Double, unit: WeightUnit): String =
        WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(weightKg, unit))

    private fun describe(state: ActiveWorkoutUiState): String =
        "lift=${state.selectedExerciseId}, entry=${state.draft.weightKg} kg × ${state.draft.reps} " +
            "rpe=${state.draft.rpe} warmUp=${state.draft.isWarmup} typed=${state.draftDirty}, " +
            "hint=${state.hint?.let { "${it.exerciseId} ${it.suggestedWeightKg} kg lighter=${it.lighterHold}" }}, " +
            "readiness=${state.liftReadiness}, locked=${state.entryLocked}, editing=${state.editingSetId}, " +
            "save=${state.save.phase}, load=${state.loadState}"

    /**
     * A workout on [lifts], in order, after last session's Squat at [priorSquatKg] × 5 when given,
     * with a Squat warm-up at [warmUpTodayKg] × 5 already logged today when given.
     */
    private suspend fun seedWorkout(
        vararg lifts: PlannedLift,
        priorSquatKg: Double? = null,
        warmUpTodayKg: Double? = null,
    ): SeededWorkout {
        deps.database.exerciseDao().insertAll(lifts.map { exerciseEntity(id = it.id, name = it.name) })
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
        val live = deps.workoutRepository.startRoutine(routine)
        val warmUpId = warmUpTodayKg?.let { kg ->
            deps.workoutRepository.logSet(
                sessionId = live.id,
                exerciseId = SQUAT,
                weightKg = kg,
                reps = 5,
                rpe = null,
                isWarmup = true,
            ).setId
        }
        return SeededWorkout(session = checkNotNull(deps.workoutRepository.getSession(live.id)), warmUpId = warmUpId)
    }

    /**
     * A weighted plank, a 30 s hold, after last session's one hold at 20 kg with a rep on record:
     * the shape older builds stored holds in, which gives the hold a rep-based hint that steps
     * (22.5 kg in kilograms, 49 lb in pounds). A hold logged today stores no reps, which leaves it
     * a rep short of its target and its hint at exactly last time's 20 kg in either unit, where a
     * unit change could move nothing; so the row is written as the older build wrote it.
     */
    private suspend fun seedWeightedPlank(): SeededWorkout {
        deps.database.exerciseDao().insertAll(listOf(exerciseEntity(id = PLANK, name = "Weighted Plank")))
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Core", notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        deps.database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-$PLANK",
                routineId = ROUTINE,
                exerciseId = PLANK,
                sortOrder = 0,
                targetSets = 3,
                targetReps = 1,
                targetWeightKg = 20.0,
                restSeconds = 60,
                targetSeconds = 30,
            ),
        )
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE))
        val prior = deps.workoutRepository.startRoutine(routine)
        deps.database.workoutDao().insertSet(
            SetLogEntity(
                id = "prior-plank-hold",
                sessionId = prior.id,
                exerciseId = PLANK,
                setNumber = 1,
                weightKg = 20.0,
                reps = 1,
                rpe = null,
                isWarmup = false,
                completedAt = deps.time.nowMillis(),
                durationSeconds = 30,
            ),
        )
        deps.workoutRepository.finishSession(prior.id, notes = "")
        return SeededWorkout(session = deps.workoutRepository.startRoutine(routine), warmUpId = null)
    }

    private fun exerciseEntity(id: String, name: String) = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
        loadType = "EXTERNAL",
        nameKey = name.lowercase(),
    )

    private data class PlannedLift(val id: String, val name: String, val reps: Int, val weightKg: Double)

    private data class SeededWorkout(val session: WorkoutSession, val warmUpId: String?)

    private companion object {
        /** How long a guard watches for what it guards to move, after the cause has happened. */
        const val NEVER_MS = 2_000L
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val PLANK = "weighted-plank"
        const val ROUTINE = "routine-full"
        const val STAMP = 1_700_000_000_000L
        val SQUAT_3X5 = PlannedLift(id = SQUAT, name = "Squat", reps = 5, weightKg = 100.0)
        val BENCH_3X5 = PlannedLift(id = BENCH, name = "Bench", reps = 5, weightKg = 60.0)
    }
}
