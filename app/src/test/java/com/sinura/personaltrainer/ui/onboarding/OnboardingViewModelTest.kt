package com.sinura.personaltrainer.ui.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingFocus
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestWaits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
class OnboardingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: OnboardingViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun previewStaysNullUntilTheCatalogArrivesThenTracksDays() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), SavedStateHandle(), deps)
        assertNull(viewModel!!.uiState.value.preview)

        deps.dbMaintenance.seedCatalog()
        val afterSeed = withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.preview != null }.preview!! }
        assertEquals(OnboardingAnswers().daysPerWeek, afterSeed.trainingDayCount)

        viewModel!!.setDaysPerWeek(6)
        assertEquals(6, withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.preview?.trainingDayCount == 6 } }.preview!!.trainingDayCount)
    }

    @Test
    fun retryCatalogFillsAPreviewThatWasWaiting() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), SavedStateHandle(), deps)
        assertNull(viewModel!!.uiState.value.preview)

        // Retry is the recovery path: it seeds, then waits for Room. Closing the
        // in-memory database to force a failure hangs `observeAll().first()` instead
        // of throwing, so the fail-empty branch is locked by the constant, not by
        // breaking Room.
        viewModel!!.retryCatalog()
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.preview != null } }
        assertNull(viewModel!!.uiState.value.error)
        assertEquals(
            "Couldn't load the lift catalog. Try again, or build your own.",
            CATALOG_MISSING_MESSAGE,
        )
    }

    @Test
    fun backOnFirstQuestionWithAnExistingProgramRestoresComplete() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        deps.routineRepository.create("Upper")
        deps.preferencesRepository.setOnboardingComplete(false)
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), SavedStateHandle(), deps)

        withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.existingProgram } }
        assertFalse(viewModel!!.back())
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.finished.first { it } }
        withTimeout(TestWaits.FLOW_MS) { deps.preferencesRepository.onboardingComplete.first { it } }
        Unit
    }

    @Test
    fun backOnFirstQuestionWithNoProgramDoesNotMarkComplete() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), SavedStateHandle(), deps)

        assertFalse(viewModel!!.back())
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { !it.existingProgram } }
        assertFalse(viewModel!!.finished.value)
        assertFalse(deps.preferencesRepository.onboardingComplete.first())
    }

    @Test
    fun togglingWeightUnitDoesNotWriteUntilThePlanIsApplied() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        deps.dbMaintenance.seedCatalog()
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), SavedStateHandle(), deps)

        withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.preview != null } }
        val storedBefore = deps.preferencesRepository.weightUnit.first()
        assertEquals(WeightUnit.LBS, storedBefore)

        viewModel!!.setWeightUnit(WeightUnit.KG)
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.weightUnit == WeightUnit.KG } }
        assertEquals(WeightUnit.LBS, deps.preferencesRepository.weightUnit.first())

        viewModel!!.applyPlan()
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.finished.first { it } }
        withTimeout(TestWaits.FLOW_MS) { deps.preferencesRepository.weightUnit.first { it == WeightUnit.KG } }
        assertFalse(deps.preferencesRepository.restAlarmEligible.first())
        Unit
    }

    /**
     * UX07-AC04: a recreated process reopens setup on the same question with the same answers,
     * and restoring writes nothing — no routine, no schedule, no completion flag — until the
     * lifter taps Use this plan.
     */
    @Test
    fun processRecreationRestoresTheStepAndAnswersWithoutWritingAPlan() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        val handle = SavedStateHandle()
        val first = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), handle, deps)
        first.setFocus(TrainingFocus.STRENGTH)
        first.setExperience(TrainingAge.RETURNING)
        first.setDaysPerWeek(4)
        first.toggleDay(Weekday.MONDAY)
        first.toggleDay(Weekday.THURSDAY)
        first.setWeightUnit(WeightUnit.KG)
        val before = withTimeout(5_000) {
            first.uiState.first { it.answers.daysPerWeek == 4 && it.answers.preferredDays.size == 2 }
        }
        assertEquals(OnboardingStep.DAYS_PER_WEEK, before.step)
        first.clearAndJoinForTest()

        // The same handle is what the framework hands the recreated ViewModel.
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), handle, deps)
        val restored = withTimeout(5_000) {
            viewModel!!.uiState.first { it.answers.daysPerWeek == 4 && it.weightUnit == WeightUnit.KG }
        }
        assertEquals(OnboardingStep.DAYS_PER_WEEK, restored.step)
        assertEquals(TrainingAge.RETURNING, restored.answers.trainingAge)
        assertEquals(setOf(Weekday.MONDAY, Weekday.THURSDAY), restored.answers.preferredDays)
        assertEquals(TrainingFocus.STRENGTH, restored.answers.focus)

        // The stored-answers seed runs after recreation too; it must not clobber the draft.
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(4, viewModel!!.uiState.value.answers.daysPerWeek)

        // Nothing was written by either instance.
        assertFalse(deps.onboardingApplier.hasExistingProgram())
        assertEquals(0, deps.routineRepository.count())
        assertFalse(deps.preferencesRepository.onboardingComplete.first())
        assertEquals(WeightUnit.LBS, deps.preferencesRepository.weightUnit.first())
    }

    @Test
    fun skippingBodyweightLeavesTheWeighInUnset() {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), SavedStateHandle(), deps)
        assertNull(viewModel!!.uiState.value.answers.bodyweightKg)
        viewModel!!.setBodyweight(80.0)
        viewModel!!.setBodyweight(null)
        assertNull(viewModel!!.uiState.value.answers.bodyweightKg)
    }
}
