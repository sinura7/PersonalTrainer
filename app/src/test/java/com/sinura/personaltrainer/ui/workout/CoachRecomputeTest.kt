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
import org.junit.Assert.assertSame
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
        val hold = log.awaitCall("the Log's call after a set at RPE 8") {
            it?.reasonCode == SetMicroRecCalculator.QUALITY
        }
        val holdLine = log.shownNextLine(log.awaitState { !it.entryLocked })
        assertEquals("the card after 100 kg × 5 at RPE 8", "Next: 100 kg × 5 · RPE 8", holdLine)

        log.setWeight(102.5)
        val stepped = log.awaitState { it.draft.weightKg == 102.5 && !it.entryLocked }
        assertEquals("a weight step with no RPE chosen yet leaves the Log's card as it was", holdLine, log.shownNextLine(stepped))
        assertEquals(
            "a weight step with no RPE chosen yet leaves the Log's call as it was",
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

    // --- Red before W2c, green after it --------------------------------------------------------

    @Test
    fun aWeightStepAndASecondOfClockDoNotReissueTheLogsCall() = runBlocking {
        val fixture = seedWorkout()
        val log = createLog(fixture.session.id)
        log.logTheFirstSet()
        log.awaitState { it.draft.rpe == 8 && !it.entryLocked }
        dispatcher.scheduler.advanceUntilIdle()
        val hold = checkNotNull(
            log.awaitCall("the Log's call after a set at RPE 8") {
                it?.reasonCode == SetMicroRecCalculator.QUALITY
            },
        )

        val issued = CopyOnWriteArrayList<SetMicroRec?>()
        val watch = launch(dispatcher) { log.microRec.collect { issued += it } }

        clock.advance(1_000)
        log.setWeight(102.5)
        dispatcher.scheduler.advanceUntilIdle()
        log.awaitState { it.draft.weightKg == 102.5 && it.draft.rpe == 8 && !it.entryLocked }
        watch.cancel()

        val stamps = issued.map { it?.trace?.generatedAtMs }
        assertEquals(
            "a weight step alone, with effort already on the draft from the last log, keeps the Log's call: $stamps",
            1,
            issued.size,
        )
        assertSame("the Log's call is the one it already had", hold, log.microRec.value)

        // The call still carries the time it was made (ADR-008): a change the coach reads is
        // asked about with the clock as it is now, not as it was when the key last changed.
        // RPE 9, not 8: the logged set was an 8, so choosing 8 again asks the same question.
        log.setRpe(9)
        val asked = checkNotNull(
            log.awaitCall("the Log's call once RPE 9 is chosen") { it != null && it.content() != hold.content() },
        )
        assertEquals("the new call is stamped with the clock at the ask", clock.nowMillis(), asked.trace.generatedAtMs)
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** The call less its trace's clock, the only part of it a second of clock can move. */
    private fun SetMicroRec.content(): SetMicroRec =
        copy(trace = trace.copy(generatedAtMs = 0L, evidenceStartEpochDay = 0L, evidenceEndEpochDay = 0L))

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

    private suspend fun awaitRestRunning() {
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (!deps.restTimerStore.current().running) {
                    // The rest after a logged set waits on the virtual
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
