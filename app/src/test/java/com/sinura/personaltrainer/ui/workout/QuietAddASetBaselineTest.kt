package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.coach.AddASetTrace
import com.sinura.personaltrainer.domain.coach.TempoCoachTip
import com.sinura.personaltrainer.testutil.SteppingTime
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.awaitFirst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Native Quiet may rearrange coaching, but its existing Debug124 routes stay distinct.
 * Actual Room rows and the real VM produce the offer; no constructed policy Context,
 * manufactured tip, private-state reset or extra save is used to obtain it.
 *
 * This is the VM/Room routing lane. Quiet's real UI touches and constrained placement
 * are verified separately by its rendered/native journeys.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QuietAddASetBaselineTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = SteppingTime(nowMs = 0L, zoneId = "UTC")
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            time = clock,
        )
        runBlocking {
            deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
            deps.preferencesRepository.setTrainingGoal(TrainingGoal.HYPERTROPHY)
        }
    }

    @After
    fun tearDown() {
        try {
            runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
            viewModels.clear()
        } finally {
            if (::deps.isInitialized) {
                deps.restTimerController.stop()
                dispatcher.scheduler.advanceUntilIdle()
                deps.close()
            }
            Dispatchers.resetMain()
        }
    }

    @Test
    fun acceptingThePublishedOfferOnlyFillsTheDraftAndArmsAnExtraSet() = runBlocking {
        val vm = openAtLastPlannedSet()
        val tip = manualDraftWithOffer(vm)
        val storedBefore = stored(vm)
        assertFalse(vm.extraSetRequested.value)
        assertTrue("manual draft differs from the offered seed", tip.seedRec.nextWeightKg != MANUAL_WEIGHT)

        vm.applyTempoCoachTip(tip)

        val applied = vm.awaitState {
            it.draft.weightKg == tip.seedRec.nextWeightKg &&
                it.draft.reps == tip.seedRec.nextReps &&
                it.draft.rpe == tip.seedRec.nextRpe
        }
        assertEquals(tip.seedRec.nextWeightKg, applied.draft.weightKg, EPSILON)
        assertEquals(tip.seedRec.nextReps, applied.draft.reps)
        assertEquals(tip.seedRec.nextRpe, applied.draft.rpe)
        assertTrue(vm.extraSetRequested.value)
        assertTrue(vm.tempoCoachDismissed.value)
        vm.tempoCoachTip.awaitFirst { it == null }
        assertEquals(storedBefore, stored(vm))
        assertEquals(storedBefore.sets.map { it.id }, applied.session?.sets?.map { it.id })
        assertEquals(storedBefore.id, applied.session?.id)
        assertFalse(deps.restTimerStore.current().running)
        assertTrue(checkNotNull(deps.workoutDraftCache.get(storedBefore.id)).extraSetRequested)
    }

    @Test
    fun dismissingThePublishedOfferPreservesNumbersAndLeavesManualExtraAvailable() = runBlocking {
        val vm = openAtLastPlannedSet()
        val tip = manualDraftWithOffer(vm)
        val beforeDraft = vm.uiState.value.draft
        val storedBefore = stored(vm)

        vm.dismissTempoCoachTip(tip)

        // The published resolver can retain a NextSet fallback. Dismissal suppresses
        // this AddASet offer; it does not set the global NextSet dismissal flag.
        vm.tempoCoachTip.awaitFirst { it !is TempoCoachTip.AddASet }
        assertFalse(vm.tempoCoachDismissed.value)
        assertFalse(vm.extraSetRequested.value)
        assertEquals(beforeDraft, vm.uiState.value.draft)
        assertEquals(storedBefore, stored(vm))

        vm.requestExtraSet()

        vm.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.LOG_SET }
        assertTrue(vm.extraSetRequested.value)
        assertEquals(beforeDraft, vm.uiState.value.draft)
        assertEquals(storedBefore, stored(vm))
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun manualExtraAfterThePlanNeverAppliesTheCoachOrSavesASet() = runBlocking {
        val vm = openAtLastPlannedSet()
        manualDraftWithOffer(vm)
        val beforeDraft = vm.uiState.value.draft
        val storedBefore = stored(vm)

        vm.requestExtraSet()

        vm.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.LOG_SET }
        vm.tempoCoachTip.awaitFirst { it !is TempoCoachTip.AddASet }
        val state = vm.awaitState { it.canLog && !it.entryLocked }
        assertTrue(vm.extraSetRequested.value)
        assertFalse(vm.tempoCoachDismissed.value)
        assertEquals(beforeDraft, state.draft)
        assertEquals(MANUAL_WEIGHT, state.draft.weightKg, EPSILON)
        assertEquals(MANUAL_REPS, state.draft.reps)
        assertEquals(MANUAL_RPE, state.draft.rpe)
        assertEquals(storedBefore, stored(vm))
        assertEquals(PLANNED_SETS, state.session?.sets?.size)
        assertEquals(storedBefore.id, state.session?.id)
        assertFalse(deps.restTimerStore.current().running)
        assertTrue(checkNotNull(deps.workoutDraftCache.get(storedBefore.id)).extraSetRequested)
    }

    private suspend fun openAtLastPlannedSet(): ActiveWorkoutViewModel {
        deps.preferencesRepository.coachPreferences.awaitFirst { it.goal == TrainingGoal.HYPERTROPHY }
        val sessionId = seedLegExtension(
            deps = deps,
            targetSets = PLANNED_SETS,
            loggedSets = listOf(
                TestSetInput(weightKg = FLOOR_KG70, reps = 10, rpe = 6),
                TestSetInput(weightKg = FLOOR_KG70, reps = 10, rpe = 8),
            ),
        )
        // The published repository captures these real fixture rows with its wall clock.
        // Freeze the injected coach clock at their latest timestamp before creating the VM, so
        // the live-week accounting and exact trace window share one stable instant.
        val seeded = checkNotNull(deps.workoutRepository.getSession(sessionId))
        clock.advance(seeded.sets.maxOf { it.completedAt } - clock.nowMillis())
        val vm = floorViewModel(deps = deps, sessionId = sessionId).also(viewModels::add)
        vm.awaitState(FLOOR_LIFT_READY)
        val tip = vm.tempoCoachTip.awaitFirst { it is TempoCoachTip.AddASet } as TempoCoachTip.AddASet
        val facts = tip.trace.facts.associate { it.name to it.value }
        assertEquals(AddASetTrace.RULE_ID, tip.trace.ruleId)
        assertEquals(AddASetTrace.ACTION, tip.trace.action)
        assertEquals("7.0", facts["meanRpe"])
        assertEquals("4", facts["readinessCount"])
        assertEquals("false", facts["comparisonAvailable"])
        assertEquals("true", facts["performanceSignal"])
        assertEquals("false", facts["trendSignal"])
        assertEquals("false", facts["blockSignal"])
        assertEquals(AddASetTrace.CURRENT_PLAN_TARGET, facts["blockComparisonSource"])
        assertEquals("2.0", facts["weeklySets"])
        // The published trend accounting weights RPE 6 as 0.70 and RPE 8 as 1.00;
        // its current bucket differs from the live weekly-count estimate above.
        assertEquals("0.0,0.0,1.7", facts["weeklySetsByWeek"])
        val today = clock.civilDate(clock.nowMillis(), "UTC")
        val currentWeekStart = today.previousOrSame(SchedulePreferences.DEFAULT_WEEK_START).epochDay
        assertEquals(clock.nowMillis(), tip.trace.generatedAtMs)
        assertEquals(today.epochDay, tip.trace.evidenceEndEpochDay)
        assertEquals(currentWeekStart - 2L * Weekday.DAYS_IN_WEEK, tip.trace.evidenceStartEpochDay)
        assertEquals(currentWeekStart.toString(), facts["currentWeekStartEpochDay"])
        assertEquals("UTC", facts["zoneId"])
        assertEquals("unavailable", facts["comparisonDate"])
        // Its untouched extra-set seed has the published default trace timestamp.
        // The volume offer must not borrow that seed's unknown calendar window.
        assertEquals(0L, tip.seedRec.trace.generatedAtMs)
        val rows = stored(vm)
        assertEquals(PLANNED_SETS, rows.sets.size)
        assertEquals(PLANNED_SETS, rows.exercises.single().targetSets)
        assertFalse(rows.isFinished)
        assertFalse(deps.restTimerStore.current().running)
        return vm
    }

    private suspend fun manualDraftWithOffer(vm: ActiveWorkoutViewModel): TempoCoachTip.AddASet {
        vm.setWeight(MANUAL_WEIGHT)
        vm.setReps(MANUAL_REPS)
        vm.setRpe(MANUAL_RPE)
        vm.awaitState {
            !it.entryLocked && it.draft.weightKg == MANUAL_WEIGHT &&
                it.draft.reps == MANUAL_REPS && it.draft.rpe == MANUAL_RPE
        }
        return vm.tempoCoachTip.awaitFirst { it is TempoCoachTip.AddASet } as TempoCoachTip.AddASet
    }

    private suspend fun stored(vm: ActiveWorkoutViewModel): WorkoutSession =
        checkNotNull(deps.workoutRepository.getSession(checkNotNull(vm.uiState.value.session).id))

    private companion object {
        const val PLANNED_SETS = 2
        const val MANUAL_WEIGHT = 87.5
        const val MANUAL_REPS = 12
        const val MANUAL_RPE = 8
        const val EPSILON = 0.000001
    }
}
