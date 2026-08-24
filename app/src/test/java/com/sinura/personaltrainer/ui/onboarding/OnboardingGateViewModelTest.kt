package com.sinura.personaltrainer.ui.onboarding

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The launch gate must not flash Home on a first install or the questionnaire
 * on a finished one. UNKNOWN is the only honest unread value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OnboardingGateViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: OnboardingGateViewModel? = null

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
    fun unreadPreferencesStayUnknownUntilTheFirstEmission() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val vm = createViewModel()
        assertEquals(OnboardingGate.UNKNOWN, vm.gate.value)
    }

    @Test
    fun firstInstallResolvesToSetup() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val vm = createViewModel()
        assertEquals(OnboardingGate.SETUP, vm.gate.first { it != OnboardingGate.UNKNOWN })
    }

    @Test
    fun finishedOnboardingResolvesToApp() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        deps.preferencesRepository.setOnboardingComplete(true)
        val vm = createViewModel()
        assertEquals(OnboardingGate.APP, vm.gate.first { it != OnboardingGate.UNKNOWN })
    }

    @Test
    fun completingSetupFlipsTheGateWithoutRecreation() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val vm = createViewModel()
        vm.gate.first { it == OnboardingGate.SETUP }

        deps.preferencesRepository.setOnboardingComplete(true)

        assertEquals(OnboardingGate.APP, vm.gate.first { it == OnboardingGate.APP })
    }

    @Test
    fun unreadSettingsFailureIsUnavailableNotSetup() {
        val unread = com.sinura.personaltrainer.domain.DataHealthFold.onFailure<Boolean>(
            last = null,
            what = "settings",
        )
        assertEquals(OnboardingGate.UNAVAILABLE, gateFromHealth(unread))
        val lastTrue = com.sinura.personaltrainer.domain.DataHealthFold.onFailure(
            last = true,
            what = "settings",
        )
        assertEquals(OnboardingGate.APP, gateFromHealth(lastTrue))
    }

    private fun createViewModel(): OnboardingGateViewModel =
        OnboardingGateViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            deps,
        ).also { viewModel = it }
}
