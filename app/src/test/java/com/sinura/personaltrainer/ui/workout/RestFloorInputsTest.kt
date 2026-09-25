package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.ui.theme.Motion
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
 * The rest page's floor follows each thing it is drawn from (W2c review). W2c draws the floor
 * again only when one of those things changes, so each test here draws the page, changes one of
 * them and nothing else, and the page must follow: the session (a lift renamed), the lift the
 * Log is on, a warm-up entry, a save the Log holds, and the unit.
 *
 * The page does not watch the Log's entry; it reads it at each redraw, and a rest redraws it
 * each second. The fake timer's clock is private, so a +15 stands in for that second, reaching
 * the page through the same rest state a tick does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestFloorInputsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val pages = mutableListOf<RestTimerViewModel>()
    private val logs = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        runBlocking {
            pages.forEach { it.clearAndJoinForTest() }
            logs.forEach { it.clearAndJoinForTest() }
        }
        if (::deps.isInitialized) deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aLiftRenamedWhileTheRestPageIsOpenIsNamedThere() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5)
        val page = createPage(fixture.session.id)
        val before = page.awaitRestOn("Squat")
        assertEquals("the page's Next line", "Next: 100 kg × 5", before.floor.sessionTargetLine)

        // Renamed as the library's editor saves it, or as sync writes a rename from another device.
        val saved = deps.exerciseRepository.updateCustom(id = SQUAT, name = "Back Squat", muscleGroup = "Legs", notes = "")
        assertTrue("the rename is saved: $saved", saved is SaveExerciseResult.Saved)
        val renamed = page.awaitPage("the page naming the lift as it is now, Back Squat") {
            it.floor.exerciseName == "Back Squat"
        }
        assertEquals("a rename leaves the page's Next line as it was", "Next: 100 kg × 5", renamed.floor.sessionTargetLine)
    }

    @Test
    fun theRestPageNamesTheLiftTheLogMovesToWhenBothGetTheSameCall() = runBlocking {
        // Squat and Front Squat, both 3 × 5 at 100 kg and neither trained before: the coach reads
        // the same of both, and calls both "Next: 100 kg × 5".
        val fixture = seedWorkout(SQUAT_3X5, FRONT_3X5)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT)
        val page = createPage(fixture.session.id)
        val before = page.awaitRestOn("Squat")
        assertEquals("the page's Next line for the Squat", "Next: 100 kg × 5", before.floor.sessionTargetLine)

        log.selectExercise(FRONT)
        log.awaitReady(FRONT)
        page.adjustRest(15)
        val moved = page.awaitPage("the page on the Front Squat, the lift the Log moved to") {
            it.floor.exerciseName == "Front Squat"
        }
        assertEquals("the Front Squat gets the same call", "Next: 100 kg × 5", moved.floor.sessionTargetLine)
    }

    @Test
    fun aWarmUpChosenOnTheLogAfterThePageDrewTakesItsNextLineAway() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT)
        val page = createPage(fixture.session.id)
        val before = page.awaitRestOn("Squat")
        assertEquals("the page's Next line before the warm-up", "Next: 100 kg × 5", before.floor.sessionTargetLine)

        log.setWarmup(true)
        log.awaitState { it.draft.isWarmup && !it.entryLocked }
        page.adjustRest(15)
        val after = page.awaitPage("the page after a +15") { it.rest.totalSeconds == before.rest.totalSeconds + 15 }
        assertNull(
            "with a warm-up in the Log's entry the page shows no Next line, as the Log shows its ramp instead",
            after.floor.sessionTargetLine,
        )
    }

    @Test
    fun aSaveTheLogHoldsAfterThePageDrewTakesItsNextLineAway() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5)
        val log = createLog(fixture.session.id, container = failingInsert())
        log.awaitReady(SQUAT)
        val page = createPage(fixture.session.id)
        page.awaitRestOn("Squat")

        log.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        val held = log.awaitState { it.save.phase == WorkoutSavePhase.FAILED }
        assertTrue("the failed save holds the Log's entry until Retry", held.entryLocked)
        val drawn = page.awaitPage("the page with a rest running after the failed save") {
            it.rest.running && it.floor.exerciseName == "Squat"
        }
        page.adjustRest(15)
        val after = page.awaitPage("the page after a +15") { it.rest.totalSeconds == drawn.rest.totalSeconds + 15 }
        assertNull(
            "while the Log holds a failed save the page shows no Next line, as the Log hides its card",
            after.floor.sessionTargetLine,
        )
    }

    @Test
    fun aUnitChangeReachesTheRestPagesLastSetAndNextLines() = runBlocking {
        val fixture = seedWorkout(SQUAT_3X5)
        val log = createLog(fixture.session.id)
        log.awaitReady(SQUAT)
        log.setRpe(7)
        log.awaitState { it.draft.rpe == 7 }
        log.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        awaitRestRunning()
        val page = createPage(fixture.session.id)
        val kilograms = page.awaitPage("the page with the Squat's last set") {
            it.rest.running && it.floor.lastSetLine != null
        }
        assertEquals("the last set in kilograms", "Last set · 100 kg × 5", kilograms.floor.lastSetLine)
        assertEquals("the Next line in kilograms", "Next: 102.5 kg × 5 · RPE 7", kilograms.floor.sessionTargetLine)

        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        val pounds = page.awaitPage("the page in pounds") { it.floor.lastSetLine?.contains(" lb") == true }
        assertEquals("the last set in pounds", "Last set · 220.5 lb × 5", pounds.floor.lastSetLine)
        assertEquals("the Next line in pounds, by the pound step", "Next: 225.5 lb × 5 · RPE 7", pounds.floor.sessionTargetLine)
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

    /** The Log on [liftId] at the routine's 100 kg, its load done; waited for with a ceiling. */
    private suspend fun ActiveWorkoutViewModel.awaitReady(liftId: String): ActiveWorkoutUiState =
        awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == liftId &&
                it.liftReadiness == LiftEntryReadiness.READY && it.draft.weightKg == 100.0 && !it.entryLocked
        }

    /** The page loaded on [liftName]; starts its planned rest there and waits for it to run. */
    private suspend fun RestTimerViewModel.awaitRestOn(liftName: String): RestTimerScreenState {
        awaitPage("the page loaded on $liftName with its Next line") {
            it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == liftName &&
                it.floor.sessionTargetLine != null
        }
        startSelectedRest()
        awaitRestRunning()
        return awaitPage("the page on $liftName with its rest running") {
            it.rest.running && it.floor.exerciseName == liftName
        }
    }

    /** The rest page's state once it is the one [what] names, waited for with a ceiling. */
    private suspend fun RestTimerViewModel.awaitPage(
        what: String,
        predicate: (RestTimerScreenState) -> Boolean,
    ): RestTimerScreenState = withTimeoutOrNull(TestWaits.FLOW_MS) { uiState.first(predicate) }
        ?: throw AssertionError("Never saw $what; the page showed ${uiState.value}")

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

    /** A workout on [lifts], in order. The lifts are custom, so the library's editor can rename one. */
    private suspend fun seedWorkout(vararg lifts: PlannedLift): SeededWorkout {
        deps.database.exerciseDao().insertAll(
            lifts.map { lift ->
                ExerciseEntity(
                    id = lift.id,
                    name = lift.name,
                    muscleGroup = "Legs",
                    notes = "",
                    isCustom = true,
                    loadType = "EXTERNAL",
                    nameKey = lift.name.lowercase(),
                )
            },
        )
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Lower", notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        lifts.forEachIndexed { index, lift ->
            deps.database.routineDao().upsertRoutineExercise(
                RoutineExerciseEntity(
                    id = "re-${lift.id}",
                    routineId = ROUTINE,
                    exerciseId = lift.id,
                    sortOrder = index,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = 100.0,
                    restSeconds = 90,
                ),
            )
        }
        return SeededWorkout(deps.workoutRepository.startRoutine(checkNotNull(deps.routineRepository.getById(ROUTINE))))
    }

    private data class PlannedLift(val id: String, val name: String)

    private data class SeededWorkout(val session: WorkoutSession)

    private companion object {
        const val SQUAT = "squat"
        const val FRONT = "front-squat"
        const val ROUTINE = "routine-lower"
        const val STAMP = 1_700_000_000_000L
        val SQUAT_3X5 = PlannedLift(SQUAT, "Squat")
        val FRONT_3X5 = PlannedLift(FRONT, "Front Squat")
    }
}
