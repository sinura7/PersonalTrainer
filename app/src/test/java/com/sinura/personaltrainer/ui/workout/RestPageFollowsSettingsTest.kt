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
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.theme.Motion
import java.util.concurrent.atomic.AtomicInteger
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rest page's Next line follows a unit or a week's mark changed while it is open (W2e): only
 * the drawn lift's hint is read again. Its planned length, last session's RPE on a first set, the
 * lift it reads for and the save the Log holds stay as they were.
 *
 * Built as RestTimerViewModelTest builds it: the Log and the page on one graph, the settings
 * written through the preferences repository while both are open. The workout DAO counts the reads
 * of a lift's hint (its caller is `progressionFor`) and can hold one lift's, the Log's and the
 * page's alike. The fake timer's clock is private, so a +15 stands in for the second a running
 * rest redraws the page with.
 *
 * A guard first gives the re-read a bounded chance to land ([TestWaits.FLOW_MS]; before W2e the
 * page read its hint once and the wait runs out) and then watches for [NEVER_MS] that the thing it
 * guards does not move.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestPageFollowsSettingsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val pages = mutableListOf<RestTimerViewModel>()
    private val logs = mutableListOf<ActiveWorkoutViewModel>()

    /** Reads of a lift's hint that reached the DAO, counted as each starts. */
    private val hintReads = AtomicInteger(0)

    /** Reads of a lift's hint that have ended at the DAO: returned, or cancelled while held. */
    private val hintReadsDone = AtomicInteger(0)

    /** Armed by a test to hold every read of one lift's hint; null is a pass-through. */
    @Volatile private var hintGate: Pair<String, CompletableDeferred<Unit>>? = null

    /** Completed when a read of the gated lift's hint reached [hintGate]. */
    private val hintHeld = CompletableDeferred<Unit>()

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
                        val hintRead = Throwable().stackTrace.any { it.methodName.startsWith("progressionFor") }
                        if (!hintRead) return real.finishedWorkingSetsForExercises(exerciseIds)
                        hintReads.incrementAndGet()
                        try {
                            hintGate?.let { (lift, gate) ->
                                if (lift in exerciseIds) {
                                    hintHeld.complete(Unit)
                                    gate.await()
                                }
                            }
                            return real.finishedWorkingSetsForExercises(exerciseIds)
                        } finally {
                            hintReadsDone.incrementAndGet()
                        }
                    }
                }
            },
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        hintGate?.second?.complete(Unit)
        runBlocking {
            pages.forEach { it.clearAndJoinForTest() }
            logs.forEach { it.clearAndJoinForTest() }
        }
        if (::deps.isInitialized) deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    // --- Red before W2e -------------------------------------------------------------------------

    @Test
    fun aWeekMarkedLighterWhileTheRestPageIsOpenHoldsItsFirstSetLine() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(weightKg = 102.5)
        val page = createPage(fixture.session.id)
        page.awaitLine("the page's first-set line from last time's 100 kg × 5", "Next: 102.5 kg × 5")

        markThisWeekLighter(fixture.session.id)
        val lighter = page.awaitLine("the page's first-set line in a lighter week, last time's 100 kg", "Next: 100 kg × 5")
        assertEquals("in a lighter week the page's first set holds last time's weight", "Next: 100 kg × 5", lighter.floor.sessionTargetLine)
    }

    @Test
    fun aWeekMarkedLighterWhileTheRestPageIsOpenReachesItsInSetLine() = runBlocking {
        val fixture = seedWorkout()
        val log = createLog(fixture.session.id)
        log.awaitReady(weightKg = 100.0)
        log.logOneSetAtRpe(7)
        awaitRestRunning()
        val page = createPage(fixture.session.id)
        page.awaitLine("the page's line after 100 kg × 5 at RPE 7: add weight", "Next: 102.5 kg × 5 · RPE 7")

        markThisWeekLighter(fixture.session.id)
        val held = page.awaitLine("the page's lighter-week line: hold where it would add weight", "Next: 100 kg × 5 · RPE 7")
        assertEquals("in a lighter week the page's Next line holds the weight", "Next: 100 kg × 5 · RPE 7", held.floor.sessionTargetLine)
    }

    @Test
    fun aUnitSwitchedWhileTheRestPageIsOpenMovesItsFirstSetLineToThePoundStep() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 100.0)
        val log = createLog(fixture.session.id)
        log.awaitReady(weightKg = 102.5)
        val page = createPage(fixture.session.id)
        page.awaitLine("the page's first-set line in kilograms", "Next: 102.5 kg × 5")

        switchToPounds()
        val pounds = page.awaitLine("the page's first-set line on the pound step", "Next: 225.5 lb × 5")
        assertEquals("in pounds the page's first set adds the pound step", "Next: 225.5 lb × 5", pounds.floor.sessionTargetLine)
    }

    // --- Guards: green before and after W2e ----------------------------------------------------

    @Test
    fun theRestPagesPlannedLengthDoesNotMoveWhenTheWeekTurnsLighter() = runBlocking {
        val fixture = seedWorkout()
        val log = createLog(fixture.session.id)
        log.awaitReady(weightKg = 100.0)
        log.logOneSetAtRpe(7)
        awaitRestRunning()
        log.skipRest()
        val page = createPage(fixture.session.id)
        page.awaitPage("the page planning the coach's 2:00 with no rest running") {
            it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == "Squat" &&
                !it.rest.running && it.rest.totalSeconds == 120
        }

        markThisWeekLighter(fixture.session.id)
        withTimeoutOrNull(TestWaits.FLOW_MS) { page.uiState.first { it.floor.sessionTargetLine == "Next: 100 kg × 5 · RPE 7" } }
        page.neverShows("the page's planned length moved") { it.rest.totalSeconds != 120 }
        assertEquals("the page still plans 2:00", 120, page.awaitPage("the page") { true }.rest.totalSeconds)
    }

    @Test
    fun aReReadHintKeepsLastSessionsRpeOnTheRestPage() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 100.0, priorRpe = 8)
        val log = createLog(fixture.session.id)
        log.awaitReady(weightKg = 102.5)
        val page = createPage(fixture.session.id)
        page.awaitLine("the page's first-set line with last session's RPE", "Next: 102.5 kg × 5 · RPE 8")

        switchToPounds()
        withTimeoutOrNull(TestWaits.FLOW_MS) { page.uiState.first { it.floor.sessionTargetLine == "Next: 225.5 lb × 5 · RPE 8" } }
        val line = page.awaitPage("the page in pounds") { it.floor.sessionTargetLine?.contains(" lb ") == true }
            .floor.sessionTargetLine
        assertTrue("last session's RPE stays on the page's first-set line: $line", line?.endsWith("· RPE 8") == true)
    }

    @Test
    fun aLiftTheLogMovesToWhileThePageReReadsGetsItsOwnLine() = runBlocking {
        val fixture = seedSquatThenBench(priorSquatRpe = 8)
        val log = createLog(fixture.session.id)
        log.awaitReady(weightKg = 102.5)
        log.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        awaitRestRunning()
        val page = createPage(fixture.session.id)
        page.awaitPage("the page on the Squat with its rest running") {
            it.loadState == SessionLoadState.FOUND && it.rest.running && it.floor.exerciseName == "Squat" &&
                it.floor.sessionTargetLine != null
        }
        val gate = CompletableDeferred<Unit>().also { hintGate = SQUAT to it }

        switchToPounds()
        val held = withTimeoutOrNull(TestWaits.FLOW_MS) { hintHeld.await() } != null
        log.selectExercise(BENCH)
        log.awaitLog("the Log on the Bench at 60 kg") {
            it.selectedExerciseId == BENCH && it.liftReadiness == LiftEntryReadiness.READY &&
                it.draft.weightKg == 60.0 && !it.entryLocked
        }
        page.adjustRest(15)
        val bench = page.awaitPage("the page on the Bench with its own line") {
            it.floor.exerciseName == "Bench" && it.floor.sessionTargetLine == "Next: 132.5 lb × 5"
        }

        hintGate = null
        gate.complete(Unit)
        if (held) withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReadsDone.get() < hintReads.get()) delay(10) }
        page.neverShows("the page left the Bench's line") {
            it.floor.exerciseName != "Bench" || it.floor.sessionTargetLine != bench.floor.sessionTargetLine
        }
        assertEquals("the page is on the Bench", "Bench", page.awaitPage("the page") { true }.floor.exerciseName)
    }

    @Test
    fun aSaveTheLogHoldsStillHidesTheRestPagesLineAfterAUnitChange() = runBlocking {
        val fixture = seedWorkout()
        val log = createLog(fixture.session.id, container = failingInsert())
        log.awaitReady(weightKg = 100.0)
        val page = createPage(fixture.session.id)
        page.awaitPage("the page loaded on the Squat with its Next line") {
            it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == "Squat" && it.floor.sessionTargetLine != null
        }
        page.startSelectedRest()
        awaitRestRunning()

        log.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        val held = log.awaitLog("the failed save waiting for Retry") { it.save.phase == WorkoutSavePhase.FAILED }
        assertTrue("precondition: the failed save holds the Log's entry until Retry", held.entryLocked)
        val drawn = page.awaitPage("the page with a rest running after the failed save") {
            it.rest.running && it.floor.exerciseName == "Squat"
        }

        val doneBefore = hintReadsDone.get()
        switchToPounds()
        withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReadsDone.get() <= doneBefore) delay(10) }
        page.adjustRest(15)
        val after = page.awaitPage("the page after a +15") { it.rest.totalSeconds == drawn.rest.totalSeconds + 15 }
        assertNull(
            "while the Log holds a failed save the page shows no Next line, after a unit change too",
            after.floor.sessionTargetLine,
        )
    }

    // --- The review round: the adversarial replay's gaps --------------------------------------

    @Test
    fun aUnitSwitchedToPoundsAndBackGivesThePageItsKilogramLineAgain() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 100.0, priorRpe = 8)
        val log = createLog(fixture.session.id)
        log.awaitReady(weightKg = 102.5)
        val page = createPage(fixture.session.id)
        page.awaitLine("the page's first-set line with last session's RPE", "Next: 102.5 kg × 5 · RPE 8")

        switchToPounds()
        page.awaitLine("the page's first-set line on the pound step", "Next: 225.5 lb × 5 · RPE 8")
        switchToKilograms()
        val back = page.awaitLine("the page's first-set line on the kilogram step again", "Next: 102.5 kg × 5 · RPE 8")
        assertEquals("back in kilograms the page adds the kilogram step", "Next: 102.5 kg × 5 · RPE 8", back.floor.sessionTargetLine)
    }

    @Test
    fun theRestPageReadsItsHintOnceWhenItOpensAndOnceForEachChange() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 100.0)
        val page = createPage(fixture.session.id)
        page.awaitLine("the page's first-set line from last time's 100 kg × 5", "Next: 102.5 kg × 5")
        assertPageReadsStay(1, "the page read its hint again with nothing changed")

        switchToPounds()
        page.awaitLine("the page's first-set line on the pound step", "Next: 225.5 lb × 5")
        assertPageReadsStay(2, "the switch to pounds read the page's hint more than once")
        markThisWeekLighter(fixture.session.id)
        page.awaitLine("the page's first-set line in a lighter week, in pounds", "Next: 220.5 lb × 5")
        assertPageReadsStay(3, "the lighter week read the page's hint more than once")

        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        deps.preferencesRepository.setRestSoundEnabled(false)
        deps.preferencesRepository.restTimerPreferences.awaitFirst { !it.soundEnabled }
        assertPageReadsStay(3, "the same unit written again, or a rest sound, read the page's hint again")
    }

    @Test
    fun aUnitSwitchedBackBeforeThePagesReReadReturnsIsNotReadAgain() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 100.0)
        val page = createPage(fixture.session.id)
        page.awaitLine("the page's first-set line from last time's 100 kg × 5", "Next: 102.5 kg × 5")
        assertPageReadsStay(1, "the page read its hint again with nothing changed")
        val gate = CompletableDeferred<Unit>().also { hintGate = SQUAT to it }

        switchToPounds()
        assertTrue(
            "the switch to pounds never read the page's hint again",
            withTimeoutOrNull(TestWaits.FLOW_MS) { hintHeld.await() } != null,
        )
        switchToKilograms()
        withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReadsDone.get() < hintReads.get()) delay(10) }
        hintGate = null
        gate.complete(Unit)
        assertPageReadsStay(2, "the pound read, undone before it returned, landed and was read back")
        page.neverShows("the page left its kilogram line") { it.floor.sessionTargetLine != "Next: 102.5 kg × 5" }
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun createPage(sessionId: String): RestTimerViewModel =
        RestTimerViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(pages::add)

    private fun createLog(sessionId: String, container: AppDependencies = deps): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
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

    /** Switched in Settings, or pulled in by sync, while the page is open. */
    private suspend fun switchToPounds() {
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        deps.preferencesRepository.weightUnit.awaitFirst { it == WeightUnit.LBS }
    }

    /** Switched back in Settings, or pulled in by sync, while the page is open. */
    private suspend fun switchToKilograms() {
        deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
        deps.preferencesRepository.weightUnit.awaitFirst { it == WeightUnit.KG }
    }

    /** Fails if a hint is read again within [NEVER_MS] of [expected] reads. */
    private suspend fun assertPageReadsStay(expected: Int, failure: String) {
        withTimeoutOrNull(TestWaits.FLOW_MS) { while (hintReadsDone.get() < hintReads.get()) delay(10) }
        val moved = withTimeoutOrNull(NEVER_MS) { while (hintReads.get() == expected) delay(10) }
        assertNull("$failure: ${hintReads.get()} reads, expected $expected", moved)
        assertEquals("$failure: reads, expected $expected", expected, hintReads.get())
    }

    /** "Mark this week lighter" on Body, while the page is open. */
    private suspend fun markThisWeekLighter(sessionId: String) {
        val thisWeek = ProgressionHintLoader(deps, sessionId).thisWeekStart()
        deps.preferencesRepository.setLighterWeekStartEpochDay(thisWeek)
        deps.preferencesRepository.lighterWeekStartEpochDay.awaitFirst { it == thisWeek }
    }

    private suspend fun awaitRestRunning() {
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (!deps.restTimerStore.current().running) {
                    // As RestTimerViewModelTest: the rest after a logged set waits on the virtual
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

    /** The Log on the Squat with its load done, at [weightKg]; waited for with a ceiling. */
    private suspend fun ActiveWorkoutViewModel.awaitReady(weightKg: Double): ActiveWorkoutUiState =
        awaitLog("the Log on the Squat, loaded at $weightKg kg") {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT &&
                it.liftReadiness == LiftEntryReadiness.READY && it.draft.weightKg == weightKg && !it.entryLocked
        }

    /** Logs the Squat's entry at [rpe] and waits for the set to land. */
    private suspend fun ActiveWorkoutViewModel.logOneSetAtRpe(rpe: Int) {
        setRpe(rpe)
        awaitLog("the entry at RPE $rpe") { it.draft.rpe == rpe }
        logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        awaitLog("the Squat's set logged") { state -> state.session?.sets?.any { it.exerciseId == SQUAT } == true && !state.entryLocked }
    }

    /** The Log's state once it is the one [what] names, or a failure that says what it showed. */
    private suspend fun ActiveWorkoutViewModel.awaitLog(
        what: String,
        predicate: (ActiveWorkoutUiState) -> Boolean,
    ): ActiveWorkoutUiState = withTimeoutOrNull(TestWaits.FLOW_MS) { uiState.first(predicate) }
        ?: throw AssertionError("Never saw $what; the Log showed ${uiState.value}")

    /** The page loaded on the Squat showing [line], or a failure that names the line it showed. */
    private suspend fun RestTimerViewModel.awaitLine(what: String, line: String): RestTimerScreenState =
        awaitPage("$what, \"$line\"") {
            it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == "Squat" && it.floor.sessionTargetLine == line
        }

    /** The rest page's state once it is the one [what] names, waited for with a ceiling. */
    private suspend fun RestTimerViewModel.awaitPage(
        what: String,
        predicate: (RestTimerScreenState) -> Boolean,
    ): RestTimerScreenState = withTimeoutOrNull(TestWaits.FLOW_MS) { uiState.first(predicate) }
        ?: throw AssertionError(
            "Never saw $what; the page showed \"${uiState.value.floor.sessionTargetLine}\" on " +
                "${uiState.value.floor.exerciseName} (${uiState.value})",
        )

    /** Fails if the page shows [predicate] within [NEVER_MS]. */
    private suspend fun RestTimerViewModel.neverShows(what: String, predicate: (RestTimerScreenState) -> Boolean) {
        val shown = withTimeoutOrNull(NEVER_MS) { uiState.first(predicate) }
        assertNull("$what: the page showed $shown", shown)
    }

    private suspend fun seedWorkout(priorWeightKg: Double? = null, priorRpe: Int? = null): SeededWorkout {
        insertExercise(SQUAT, "Squat")
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Lower", notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        deps.database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-$SQUAT",
                routineId = ROUTINE,
                exerciseId = SQUAT,
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 90,
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
        return SeededWorkout(session = deps.workoutRepository.startRoutine(routine))
    }

    /** Squat (3 × 5 at 100 kg) then Bench (60 kg × 5, one set), with last session's Squat 100 kg × 5 at [priorSquatRpe]. */
    private suspend fun seedSquatThenBench(priorSquatRpe: Int): SeededWorkout {
        insertExercise(SQUAT, "Squat")
        insertExercise(BENCH, "Bench")
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Full", notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        listOf(Triple(SQUAT, 3, 100.0), Triple(BENCH, 1, 60.0)).forEachIndexed { order, (id, sets, targetKg) ->
            deps.database.routineDao().upsertRoutineExercise(
                RoutineExerciseEntity(
                    id = "re-$id",
                    routineId = ROUTINE,
                    exerciseId = id,
                    sortOrder = order,
                    targetSets = sets,
                    targetReps = 5,
                    targetWeightKg = targetKg,
                    restSeconds = 90,
                ),
            )
        }
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE))
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
        return SeededWorkout(session = deps.workoutRepository.startRoutine(routine))
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
        /** How long a guard watches for what it guards to move, after the cause has happened. */
        const val NEVER_MS = 2_000L
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val ROUTINE = "routine-lower"
        const val STAMP = 1_700_000_000_000L
    }
}
