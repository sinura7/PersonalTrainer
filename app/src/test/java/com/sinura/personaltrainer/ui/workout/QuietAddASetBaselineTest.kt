package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.coach.TempoCoachTip
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
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
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
        val vm = openLegExtension(
            deps = deps,
            viewModels = viewModels,
            targetSets = PLANNED_SETS,
            loggedSets = listOf(
                TestSetInput(weightKg = FLOOR_KG70, reps = 10, rpe = 6),
                TestSetInput(weightKg = FLOOR_KG70, reps = 10, rpe = 8),
            ),
        )
        vm.awaitState(FLOOR_LIFT_READY)
        vm.tempoCoachTip.awaitFirst { it is TempoCoachTip.AddASet }
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
