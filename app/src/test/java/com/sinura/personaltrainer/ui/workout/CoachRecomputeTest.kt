package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.SteppingTime
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.ui.theme.Motion
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
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
 * The coach is asked again only when what it is asked about changes (W2c, audit C-2).
 *
 * The clock is a [SteppingTime] that moves only when a test moves it. The coach's call carries
 * the time it was made (ADR-008), so with a real clock "was it asked again?" would depend on
 * whether a millisecond passed; here a second passes exactly when the test says so.
 *
 * The Log and the rest page share one draft cache, as they do in the app. The rest page's
 * "tick" is a +15: the fake timer's clock is private, and a +15 reaches the page through the same
 * rest state a tick does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CoachRecomputeTest {
    private val dispatcher = UnconfinedTestDispatcher()
    /** Today's UTC noon, so a second of clock never crosses midnight into another trace day. */
    private val clock = SteppingTime(System.currentTimeMillis() / DAY_MS * DAY_MS + DAY_MS / 2)
    private lateinit var deps: FakeAppDependencies
    private val pages = mutableListOf<RestTimerViewModel>()
    private val logs = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            time = clock,
        )
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

    // --- Pins: green before W2c and after it ---------------------------------------------------

    @Test
    fun theLogsNextCardHoldsAcrossAWeightStepAndFollowsAnRpe() = runBlocking {
        val fixture = seedWorkout()
        val log = createLog(fixture.session.id)
        log.logTheFirstSet()
        val hold = log.awaitCall("the Log's call after a set with no RPE") {
            it?.reasonCode == SetMicroRecCalculator.SKIP_RPE_HOLD
        }
        val holdLine = log.shownNextLine(log.awaitState { !it.entryLocked })
        assertEquals("the card after 100 kg × 5 with no RPE", "Next: 100 kg × 5", holdLine)

        log.setWeight(102.5)
        val stepped = log.awaitState { it.draft.weightKg == 102.5 && !it.entryLocked }
        assertEquals("a weight step with no RPE leaves the Log's card as it was", holdLine, log.shownNextLine(stepped))
        assertEquals(
            "a weight step with no RPE leaves the Log's call as it was",
            hold?.content(),
            log.microRec.value?.content(),
        )

        log.setRpe(9)
        log.awaitCall("the Log's call once RPE 9 is chosen") { it != null && it.content() != hold?.content() }
        val hard = log.shownNextLine(log.awaitState { it.draft.rpe == 9 && !it.entryLocked })
        assertNotEquals("choosing an RPE moves the Log's call", holdLine, hard)

        log.setRpe(null)
        log.awaitCall("the Log's call once the RPE is cleared") { it?.content() == hold?.content() }
        assertEquals(
            "clearing the RPE brings the Log's card back",
            holdLine,
            log.shownNextLine(log.awaitState { it.draft.rpe == null && !it.entryLocked }),
        )
    }

    @Test
    fun whileARestRunsTheRestPageFollowsTheLogsEntryWithoutASessionChange() = runBlocking {
        // Decision 1 of W2c: the page keeps its per-second look at what the Log left in the draft
        // cache. Another set is written to that cache and to nothing else (no row, no setting),
        // so a page that asked its coach again only on a session or settings change would keep
        // showing no Next line here until the next set was logged.
        val fixture = seedWorkout(targetSets = 1)
        val log = createLog(fixture.session.id)
        log.logTheFirstSet(rpe = 7)
        log.startSelectedRest()
        awaitRestRunning()
        val page = createPage(fixture.session.id)
        val before = page.awaitPage("the page with the lift done and no Next line") {
            it.loadState == SessionLoadState.FOUND && it.rest.running &&
                it.floor.lastSetLine != null && it.floor.sessionTargetLine == null
        }

        log.requestExtraSet()
        log.awaitCall("the Log's call for the extra set") { it?.reasonCode == SetMicroRecCalculator.IN_TANK }
        val logLine = log.shownNextLine(log.awaitState { !it.entryLocked })
        assertEquals("the Log shows the extra set's call", "Next: 102.5 kg × 5 · RPE 7", logLine)

        page.adjustRest(15)
        val after = page.awaitPage("the page after a +15") { it.rest.totalSeconds == before.rest.totalSeconds + 15 }
        assertEquals(
            "a tick after Another set shows the Log's new Next line on the rest page",
            logLine,
            after.floor.sessionTargetLine,
        )
    }

    // --- Red before W2c, green after it --------------------------------------------------------

    @Test
    fun aWeightStepAndASecondOfClockDoNotReissueTheLogsCall() = runBlocking {
        val fixture = seedWorkout()
        val log = createLog(fixture.session.id)
        log.logTheFirstSet()
        val hold = checkNotNull(
            log.awaitCall("the Log's call after a set with no RPE") {
                it?.reasonCode == SetMicroRecCalculator.SKIP_RPE_HOLD
            },
        )
        dispatcher.scheduler.advanceUntilIdle()

        val issued = CopyOnWriteArrayList<SetMicroRec?>()
        val watch = launch(dispatcher) { log.microRec.collect { issued += it } }
        clock.advance(1_000)
        log.setWeight(102.5)
        dispatcher.scheduler.advanceUntilIdle()
        log.awaitState { it.draft.weightKg == 102.5 && !it.entryLocked }
        watch.cancel()

        val stamps = issued.map { it?.trace?.generatedAtMs }
        assertEquals("a weight step with no RPE, a second later, asked the coach again: $stamps", 1, issued.size)
        assertSame("the Log's call is the one it already had", hold, log.microRec.value)

        // The call still carries the time it was made (ADR-008): a change the coach reads is
        // asked about with the clock as it is now, not as it was when the key last changed.
        log.setRpe(8)
        val asked = checkNotNull(
            log.awaitCall("the Log's call once RPE 8 is chosen") { it != null && it.content() != hold.content() },
        )
        assertEquals("the new call is stamped with the clock at the ask", clock.nowMillis(), asked.trace.generatedAtMs)
    }

    @Test
    fun aPlusFifteenAndASecondOfClockDoNotAskTheRestPagesCoachAgain() = runBlocking {
        val fixture = seedWorkout(targetSets = 3, restSeconds = 90)
        val page = createPage(fixture.session.id)
        page.awaitPage("the page with its coach's planned length") {
            it.loadState == SessionLoadState.FOUND && it.rest.totalSeconds == 150
        }
        dispatcher.scheduler.advanceUntilIdle()
        page.startSelectedRest()
        awaitRestRunning()
        val before = page.awaitPage("the page with its rest running") { it.rest.running }
        assertNotNull("the page's coach has a call to show", before.floor.sessionTargetLine)

        clock.advance(1_000)
        page.adjustRest(15)
        val after = page.awaitPage("the page after a +15") { it.rest.totalSeconds == before.rest.totalSeconds + 15 }

        assertNotEquals("the page redrew the rest", before.rest, after.rest)
        assertSame(
            "a +15 a second later, with nothing the coach reads changed, asked the page's coach again",
            before.floor,
            after.floor,
        )
        // Its second call, for a planned-rest line nothing draws, is gone with it.
        assertNull("the page asked its coach for a line it never draws", after.floor.prescribedRestLine)

        // A change the coach reads still reaches the page: the unit is one of its inputs.
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        val pounds = page.awaitPage("the page in pounds") { it.floor.sessionTargetLine?.contains("lb") == true }
        assertTrue("the rest keeps running through a setting change", pounds.rest.running)
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** The call less its trace's clock, the only part of it a second of clock can move. */
    private fun SetMicroRec.content(): SetMicroRec =
        copy(trace = trace.copy(generatedAtMs = 0L, evidenceStartEpochDay = 0L, evidenceEndEpochDay = 0L))

    private fun createPage(sessionId: String): RestTimerViewModel =
        RestTimerViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(pages::add)

    private fun createLog(sessionId: String): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(logs::add)

    /** The Log, prefilled at the routine's 100 kg × 5; logs the lift's first set at [rpe] and settles. */
    private suspend fun ActiveWorkoutViewModel.logTheFirstSet(rpe: Int? = null) {
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

    /** The Log's Next line as its card shows it, or null where the card is hidden. */
    private fun ActiveWorkoutViewModel.shownNextLine(entry: ActiveWorkoutUiState): String? {
        val call = microRec.value ?: return null
        val shown = !entry.entryLocked && !entry.draft.isWarmup && SetMicroRecCopy.visibleOnEntry(call)
        val loadClass = entry.selectedExerciseId?.let { entry.session?.loadClassOf(it) } ?: LoadClass.LOADED
        return if (shown) SetMicroRecCopy.line(call, loadClass, WeightUnit.KG) else null
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

    private suspend fun seedWorkout(targetSets: Int = 3, restSeconds: Int = 90): SeededWorkout {
        deps.database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = SQUAT,
                    name = "Squat",
                    muscleGroup = "Legs",
                    notes = "",
                    isCustom = false,
                    loadType = "EXTERNAL",
                    nameKey = "squat",
                ),
            ),
        )
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Lower", notes = "", createdAt = STAMP, updatedAt = STAMP),
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
        return SeededWorkout(deps.workoutRepository.startRoutine(routine))
    }

    private data class SeededWorkout(val session: WorkoutSession)

    private companion object {
        const val SQUAT = "squat"
        const val ROUTINE = "routine-lower"
        const val STAMP = 1_700_000_000_000L
        const val DAY_MS = 86_400_000L
    }
}
