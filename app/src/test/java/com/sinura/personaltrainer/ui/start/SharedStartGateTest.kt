package com.sinura.personaltrainer.ui.start

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.util.JvmTime
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
import org.junit.Assert.assertNull
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
    private val composers = mutableListOf<String>()

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

    @Test
    fun aMixedTodayStartFromANonHomeTabOpensTheComposerOnceAndAdmitsALaterStart() {
        runBlocking {
            val planner = deps.plannerRepository
            val today = JvmTime.captureNow().localEpochDay
            planner.addTimedRule(
                weekday = Weekday.fromEpochDay(today), hour = 0, minute = 0, modality = ScheduleModality.MIXED,
            )
            planner.ensureWeek(CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY))
        }
        showOffHome()
        compose.awaitThat("Today offers the planned mixed start", { gate.uiState.value }) {
            compose.onAllNodesWithTag(StartTags.START_TODAY).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(StartTags.START_TODAY).performScrollTo().performClick()
        compose.awaitThat("the composer opens", { composers }) { composers.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(listOf("mixed"), composers)
        assertNull("nothing stays queued", gate.navigateToComposer.value)
        assertEquals("the chooser closes once for the composer", 1, dismisses)
        val later = compose.runOnUiThread { gate.startFreeWorkout() }
        assertTrue("a later start is admitted", later)
        compose.awaitThat("the later start opens its workout", { started }) { started.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(listOf("mixed"), composers)
        assertEquals(1, started.size)
    }

    private var dismisses = 0

    private fun showOffHome() {
        gate = startGateFor(deps)
        compose.awaitThat("the gate's board is current", { gate.uiState.value.readState }) {
            gate.uiState.value.readState == HomeReadState.CURRENT && !gate.uiState.value.isLoading
        }
        compose.setContent {
            PersonalTrainerTheme {
                StartChooserHost(gate = gate, deps = deps, onDismiss = { dismisses++ }, onWorkoutStarted = { started += it }, onOpenComposer = { composers += it })
            }
        }
        compose.waitForIdle()
    }
}
