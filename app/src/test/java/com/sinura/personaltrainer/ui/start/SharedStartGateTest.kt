package com.sinura.personaltrainer.ui.start

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.ui.home.HomeReadState
import com.sinura.personaltrainer.ui.home.HomeViewModel
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.workout.awaitThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one shared gate (V2): a chooser hosted off Home, as on Plan, History or Body, gets the
 * same admission guards Home has.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1600dp-xhdpi")
class SharedStartGateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var deps: FakeAppDependencies
    private lateinit var gate: HomeViewModel
    private val started = mutableListOf<String>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() {
        runBlocking { if (::gate.isInitialized) gate.clearAndJoinForTest() }
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aDoubleStartFromANonHomeTabAdmitsOneWorkout() {
        showOffHome()
        // Two taps from one frame: the second is refused by the gate, not by recomposition.
        val first = compose.runOnUiThread { gate.startFreeWorkout() }
        val second = compose.runOnUiThread { gate.startFreeWorkout() }
        assertTrue(first)
        assertFalse("the second start in flight is refused", second)
        compose.awaitThat("the one admitted start opens its workout", { started }) { started.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(1, started.size)
        val live = runBlocking { deps.workoutRepository.getInProgress() }
        assertEquals(started.single(), live?.id)
    }

    @Test
    fun aStartDuringALiveWorkoutFromANonHomeTabIsBlocked() {
        val live = runBlocking { deps.workoutRepository.startFreeWorkout() }
        showOffHome()
        compose.runOnUiThread { gate.startFreeWorkout() }
        compose.awaitThat("the live workout blocks the start", { gate.blockedByInProgress.value }) {
            gate.blockedByInProgress.value != null || !gate.startState.value.pending
        }
        compose.waitForIdle()
        assertNotNull("the gate holds the refused start", gate.blockedByInProgress.value)
        assertTrue("no workout opened", started.isEmpty())
        assertEquals(live.id, runBlocking { deps.workoutRepository.getInProgress() }?.id)
        compose.onNodeWithTag(StartTags.START_BLOCKED).assertIsDisplayed()
        compose.onNodeWithTag(StartTags.START_EMPTY).assertIsNotEnabled()
    }

    private fun showOffHome() {
        gate = startGateFor(deps)
        compose.awaitThat("the gate's board is current", { gate.uiState.value.readState }) {
            gate.uiState.value.readState == HomeReadState.CURRENT && !gate.uiState.value.isLoading
        }
        compose.setContent {
            PersonalTrainerTheme {
                StartChooserHost(gate = gate, deps = deps, onDismiss = {}, onWorkoutStarted = { started += it })
            }
        }
        compose.waitForIdle()
    }
}
