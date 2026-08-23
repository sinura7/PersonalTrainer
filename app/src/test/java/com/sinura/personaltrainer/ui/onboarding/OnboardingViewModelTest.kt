package com.sinura.personaltrainer.ui.onboarding

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearForTest
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.WeightUnit
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
        viewModel?.clearForTest()
        viewModel = null
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun previewStaysNullUntilTheCatalogArrivesThenTracksDays() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        assertNull(viewModel!!.uiState.value.preview)

        deps.dbMaintenance.seedCatalog()
        val afterSeed = withTimeout(5_000) { viewModel!!.uiState.first { it.preview != null }.preview!! }
        assertEquals(OnboardingAnswers().daysPerWeek, afterSeed.trainingDayCount)

        viewModel!!.setDaysPerWeek(6)
        assertEquals(6, withTimeout(5_000) { viewModel!!.uiState.first { it.preview?.trainingDayCount == 6 } }.preview!!.trainingDayCount)
    }

    @Test
    fun retryCatalogFillsAPreviewThatWasWaiting() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        assertNull(viewModel!!.uiState.value.preview)

        // Retry is the recovery path: it seeds, then waits for Room. Closing the
        // in-memory database to force a failure hangs `observeAll().first()` instead
        // of throwing, so the fail-empty branch is locked by the constant, not by
        // breaking Room.
        viewModel!!.retryCatalog()
        withTimeout(5_000) { viewModel!!.uiState.first { it.preview != null } }
        assertNull(viewModel!!.uiState.value.error)
        assertEquals(
            "Couldn't load the lift catalog. Try again, or build your own.",
            CATALOG_MISSING_MESSAGE,
        )
    }

    @Test
    fun backOnForkWithAnExistingProgramRestoresTheAppGate() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        deps.routineRepository.create("Upper")
        deps.preferencesRepository.setOnboardingComplete(false)
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        withTimeout(5_000) { viewModel!!.uiState.first { it.existingProgram } }
        assertFalse(viewModel!!.back())
        withTimeout(5_000) { deps.preferencesRepository.onboardingComplete.first { it } }
        assertTrue(viewModel!!.finished.value)
    }

    @Test
    fun backOnForkWithNoProgramStaysOnSetup() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        assertFalse(viewModel!!.back())
        withTimeout(5_000) { viewModel!!.uiState.first { !it.existingProgram } }
        assertFalse(viewModel!!.finished.value)
        assertFalse(deps.preferencesRepository.onboardingComplete.first())
    }

    @Test
    fun togglingWeightUnitDoesNotWriteUntilThePlanIsApplied() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        deps.dbMaintenance.seedCatalog()
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        withTimeout(5_000) { viewModel!!.uiState.first { it.preview != null } }
        val storedBefore = deps.preferencesRepository.weightUnit.first()
        assertEquals(WeightUnit.LBS, storedBefore)

        viewModel!!.setWeightUnit(WeightUnit.KG)
        withTimeout(5_000) { viewModel!!.uiState.first { it.weightUnit == WeightUnit.KG } }
        assertEquals(WeightUnit.LBS, deps.preferencesRepository.weightUnit.first())

        viewModel!!.applyPlan()
        withTimeout(5_000) { viewModel!!.finished.first { it } }
        withTimeout(5_000) { deps.preferencesRepository.weightUnit.first { it == WeightUnit.KG } }
        Unit
    }

    @Test
    fun skippingBodyweightLeavesTheWeighInUnset() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        assertNull(viewModel!!.uiState.value.answers.bodyweightKg)
        viewModel!!.setBodyweight(80.0)
        viewModel!!.setBodyweight(null)
        assertNull(viewModel!!.uiState.value.answers.bodyweightKg)
    }
}
