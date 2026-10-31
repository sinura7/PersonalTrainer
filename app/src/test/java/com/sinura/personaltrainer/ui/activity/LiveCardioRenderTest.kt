package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v4 blueprint packet V1b (A-V1-8, A-V1-9): what a paused live cardio screen says. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class LiveCardioRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<LiveCardioViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun pausedUnderAMinuteShowsResumeCue(): Unit = runBlocking {
        val vm = pausedAfter(seconds = 20L)
        show(vm)
        compose.onNodeWithTag(CardioTags.PAUSED_UNDER_MINUTE).assertIsDisplayed()
        compose.onNodeWithText("Resume to reach one minute").assertIsDisplayed()
    }

    @Test
    fun pausedShowsLeavePaused(): Unit = runBlocking {
        val vm = pausedAfter(seconds = 90L)
        show(vm)
        compose.onNodeWithText("Leave paused").assertIsDisplayed()
        compose.onNodeWithText(CardioCopy.LEAVE_RUNNING).assertDoesNotExist()
        compose.onNodeWithTag(CardioTags.PAUSED_UNDER_MINUTE).assertDoesNotExist()
    }

    private suspend fun pausedAfter(seconds: Long): LiveCardioViewModel {
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock()), now)
        val id = (started as ActivityWrite.Accepted).session.id
        deps.cardioTimerPersistence.clear()
        val clock = TestCardioClock(now.instantMillis)
        val vm = LiveCardioViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to id)),
            container = deps,
            elapsedRealtime = { clock.elapsedMs },
            wallClock = { clock.wallMs },
        ).also { viewModels += it }
        vm.uiState.awaitFirst { it.session != null }
        clock.advance(seconds * 1_000L)
        vm.togglePause()
        vm.uiState.awaitFirst { it.paused }
        return vm
    }

    private fun show(vm: LiveCardioViewModel) {
        compose.setContent { PersonalTrainerTheme { LiveCardioScreen(onExit = {}, onFinished = {}, viewModel = vm) } }
        compose.waitForIdle()
    }

    private fun runBlock() = CardioBlock(
        id = "blk-live", sortOrder = 0, type = CardioType.RUN, indoor = false, elapsedSeconds = 0L,
        movingSeconds = 0L, distanceMeters = null, elevationMeters = null, heartRateBpm = null,
        energyKj = null, rpe = null, routeRef = null,
    )
}
