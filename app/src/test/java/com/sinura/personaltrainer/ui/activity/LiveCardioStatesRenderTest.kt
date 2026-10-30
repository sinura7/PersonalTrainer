package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.ActivityReadGate
import com.sinura.personaltrainer.testutil.FailingGetGraphDao
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint 4.17 (A43): live cardio, one render check per state. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class LiveCardioStatesRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val gate = ActivityReadGate(shouldFail = false)
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<LiveCardioViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            activityDaoDecorator = { FailingGetGraphDao(it, gate) },
        )
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
    fun underAMinuteFinishWaitsBehindTheGate(): Unit = runBlocking {
        val (id, clock) = live()
        val vm = viewModel(id, clock)
        vm.uiState.awaitFirst { it.session != null }
        show(vm)
        compose.onNodeWithTag(CardioTags.FINISH).assertIsNotEnabled()
        // A scripted call under the minute is refused by the ViewModel too.
        vm.finish()
        assertEquals(false, vm.uiState.value.finishing)
        assertEquals(null, vm.finishedId.value)
    }

    @Test
    fun aReadFaultShowsTheRetry(): Unit = runBlocking {
        val (id, clock) = live()
        gate.shouldFail = true
        val vm = viewModel(id, clock)
        vm.uiState.awaitFirst { it.failed }
        show(vm)
        compose.onNodeWithTag(CardioTags.RETRY).assertIsDisplayed()
        compose.onNodeWithTag(CardioTags.FINISH).assertDoesNotExist()
    }

    @Test
    fun firstTimeTypePickerChangesTheType(): Unit = runBlocking {
        val (id, clock) = live()
        val vm = viewModel(id, clock)
        vm.uiState.awaitFirst { it.session != null }
        show(vm)
        compose.onNodeWithText(CardioCopy.name(CardioType.WALK)).performClick()
        compose.waitForIdle()
        assertEquals(CardioType.WALK, vm.uiState.awaitFirst { it.type == CardioType.WALK }.type)
    }

    @Test
    fun resumedPastTheMinuteFinishIsOpen(): Unit = runBlocking {
        val (id, clock) = live()
        // The first open writes the timer row; reopening past the minute resumes from it.
        val first = viewModel(id, clock)
        first.uiState.awaitFirst { it.session != null }
        first.clearAndJoinForTest()
        clock.advance(CardioFinishGate.MIN_SECONDS * 1_000L + 5_000L)
        val vm = viewModel(id, clock)
        vm.uiState.awaitFirst { CardioFinishGate.isOpen(elapsedSeconds = it.elapsedSeconds) }
        show(vm)
        compose.onNodeWithTag(CardioTags.FINISH).assertIsEnabled()
    }

    @Test
    fun processDeathKeepsTheClockAndTheType(): Unit = runBlocking {
        val (id, clock) = live()
        val handle = SavedStateHandle(mapOf("sessionId" to id))
        val first = viewModel(id, clock, handle)
        first.uiState.awaitFirst { it.session != null }
        first.setType(CardioType.ROW)
        clock.advance(90_000L)
        first.clearAndJoinForTest()
        val restored = viewModel(id, clock, SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        val state = restored.uiState.awaitFirst { it.session != null && it.elapsedSeconds >= 90L }
        assertEquals(CardioType.ROW, state.type)
        assertTrue(state.elapsedSeconds >= 90L)
    }

    @Test
    fun aGoneSessionSaysSo(): Unit = runBlocking {
        val vm = viewModel("no-such-session", TestCardioClock(JvmTime.captureNow().instantMillis))
        vm.uiState.awaitFirst { it.missing }
        show(vm)
        compose.onNodeWithText(CardioCopy.NOT_FOUND_TITLE).assertIsDisplayed()
    }

    private suspend fun live(): Pair<String, TestCardioClock> {
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock()), now)
        return (started as ActivityWrite.Accepted).session.id to TestCardioClock(now.instantMillis)
    }

    private fun show(vm: LiveCardioViewModel) {
        compose.setContent { PersonalTrainerTheme { LiveCardioScreen(onExit = {}, onFinished = {}, viewModel = vm) } }
        compose.waitForIdle()
    }

    private fun viewModel(
        sessionId: String,
        clock: TestCardioClock,
        handle: SavedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
    ): LiveCardioViewModel = LiveCardioViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = handle,
        container = deps,
        elapsedRealtime = { clock.elapsedMs },
        wallClock = { clock.wallMs },
    ).also { viewModels += it }

    private fun runBlock() = CardioBlock(
        id = "blk-live", sortOrder = 0, type = CardioType.RUN, indoor = false, elapsedSeconds = 0L,
        movingSeconds = 0L, distanceMeters = null, elevationMeters = null, heartRateBpm = null,
        energyKj = null, rpe = null, routeRef = null,
    )
}
