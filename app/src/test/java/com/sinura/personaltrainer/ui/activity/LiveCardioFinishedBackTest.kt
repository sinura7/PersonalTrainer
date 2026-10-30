package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.util.JvmTime
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * System Back on a finished cardio session is the same leave as the Back button.
 * A session that is still running still asks before it leaves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class LiveCardioFinishedBackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<LiveCardioViewModel>()
    private var exits = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
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
    fun systemBackOnAFinishedSessionLeavesImmediately() {
        runBlocking {
        val vm = finishedSession()
        show(vm)
        compose.onNodeWithText(CardioCopy.FINISHED_TITLE).assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.waitForIdle()
        assertEquals(1, exits)
        compose.onNodeWithText(CardioCopy.LEAVE_TITLE).assertDoesNotExist()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(2, exits)
        compose.onNodeWithText(CardioCopy.LEAVE_TITLE).assertDoesNotExist()
        compose.onNodeWithText(CardioCopy.DISCARD_TITLE).assertDoesNotExist()
        }
    }

    @Test
    fun systemBackOnALiveSessionStillAsks() {
        runBlocking {
        val vm = liveSession()
        show(vm)
        compose.onNodeWithText("Easy run").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, exits)
        compose.onNodeWithText(CardioCopy.LEAVE_TITLE).assertIsDisplayed()
        }
    }

    private fun show(vm: LiveCardioViewModel) {
        compose.setContent {
            PersonalTrainerTheme {
                LiveCardioScreen(
                    onExit = { exits += 1 },
                    onFinished = {},
                    viewModel = vm,
                )
            }
        }
        compose.waitForIdle()
    }

    private suspend fun finishedSession(): LiveCardioViewModel {
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock()), now)
        val live = (started as ActivityWrite.Accepted).session
        val clock = TestCardioClock(now.instantMillis)
        val finishing = viewModel(live.id, clock)
        finishing.uiState.awaitFirst { it.session != null }
        finishing.runPastTheGate(clock)
        finishing.finish()
        finishing.finishedId.first { it != null }
        return viewModel(live.id)
    }

    private suspend fun liveSession(): LiveCardioViewModel {
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock()), now)
        val live = (started as ActivityWrite.Accepted).session
        return viewModel(live.id)
    }

    private fun viewModel(sessionId: String, clock: TestCardioClock? = null): LiveCardioViewModel =
        if (clock == null) {
            LiveCardioViewModel(
                application = ApplicationProvider.getApplicationContext(),
                savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
                container = deps,
            ).also { viewModels += it }
        } else {
            LiveCardioViewModel(
                application = ApplicationProvider.getApplicationContext(),
                savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
                container = deps,
                elapsedRealtime = { clock.elapsedMs },
                wallClock = { clock.wallMs },
            ).also { viewModels += it }
        }

    private fun runBlock() = CardioBlock(
        id = "blk-live",
        sortOrder = 0,
        type = CardioType.RUN,
        indoor = false,
        elapsedSeconds = 0L,
        movingSeconds = 0L,
        distanceMeters = null,
        elevationMeters = null,
        heartRateBpm = null,
        energyKj = null,
        rpe = null,
        routeRef = null,
    )
}
