package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutProgressCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import org.robolectric.annotation.GraphicsMode

/**
 * The floor on a phone turned on its side, and where its chrome sits in any orientation.
 *
 * At 640 × 360 the header is one row whose title is the plan's words (the routine name rides
 * its spoken form), or the routine's name when there is no plan yet to say, never a blank
 * title; there is no progress bar, the idle rest card steps back behind "Timer controls ›",
 * and the commit stays on screen, with the floor above it keeping room to log in, whether
 * rest is idle or running. Upright, the header names the routine over the plan and its bar,
 * and the idle card stands in the dock.
 * The dock is pinned under the scrolling floor, never part of it, and the header sits above.
 *
 * These were lines of ActiveWorkoutScreen.kt, WorkoutHeader.kt and LandscapeChrome.kt read as
 * text (`LandscapeChrome.compactHeader`, `LandscapeChrome.hideIdleRest`, `val planAsTitle =
 * compact && headline.isNotBlank()`, `title = if (planAsTitle) headline else routineName`,
 * the index of `bottomBar = {` before `WorkoutDock(` before `LazyColumn(`, `WorkoutHeader(`
 * before the bottom bar) and direct calls on LandscapeChrome's budget arithmetic, most of
 * which nothing on screen ever read. Its orientation calls on live code (`isLandscape(640,
 * 360)`, `compactHeader`, `hideIdleRest` each way) are held here through the screen, in both
 * orientations: onItsSide… for landscape and upright… for portrait.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w640dp-h360dp-land-xhdpi")
class LandscapeChromeRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler()))
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking {
            deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
            // The first rest's battery sentence takes the card's place; these tests are not about it.
            deps.preferencesRepository.markRestBatteryHintShown()
        }
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun onItsSideTheHeaderIsOneRowWithThePlanAsItsTitle() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1), withNextLift = true)
        compose.showWorkoutScreen(vm, width = 640.dp, height = 360.dp)
        val progress = WorkoutProgressCalculator.of(session = vm.uiState.value.session, selectedExerciseId = vm.uiState.value.selectedExerciseId)
        val headline = WorkoutProgressCalculator.headline(progress)
        assertTrue(headline.isNotBlank())
        // One line of plan, and it is the title: not repeated under it, no bar.
        compose.onAllNodesWithTag(WorkoutTestTags.PROGRESS_LINE, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag(WorkoutTestTags.PROGRESS_LINE, useUnmergedTree = true).assertTextEquals(headline)
        assertEquals(
            listOf("Lower B. ${WorkoutProgressCalculator.spoken(progress)}"),
            compose.onNodeWithTag(WorkoutTestTags.PROGRESS_LINE, useUnmergedTree = true).spokenDescriptions(),
        )
        compose.onNodeWithTag(WorkoutTestTags.PROGRESS_BAR, useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithText("Lower B", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun onItsSideASessionWithNoLiftYetKeepsTheRoutinesNameAsItsTitle() {
        val sessionId = runBlocking { deps.workoutRepository.startFreeWorkout(focusTitle = "Arms") }.id
        val vm = floorViewModel(deps = deps, sessionId = sessionId).also(viewModels::add)
        // No lift to wait for: the screen is ready once the entry takes taps.
        val unlocked: (ActiveWorkoutUiState) -> Boolean = { state -> !state.entryLocked }
        compose.showWorkoutScreen(vm, width = 640.dp, height = 360.dp, ready = unlocked)
        val session = checkNotNull(vm.uiState.value.session)
        assertTrue("no lift yet", session.exercises.isEmpty())
        val progress = WorkoutProgressCalculator.of(session = session, selectedExerciseId = vm.uiState.value.selectedExerciseId)
        assertEquals("with no plan there are no plan words to take the title", "", WorkoutProgressCalculator.headline(progress))
        // So the title stays the routine's name: the one row is never blank.
        val title = checkNotNull(session.routineName)
        assertTrue(title.isNotBlank())
        compose.onAllNodesWithText(title, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.PROGRESS_LINE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun uprightTheHeaderNamesTheRoutineOverThePlanAndItsBar() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1), withNextLift = true)
        compose.showWorkoutScreen(vm, width = 360.dp, height = 800.dp)
        val progress = WorkoutProgressCalculator.of(session = vm.uiState.value.session, selectedExerciseId = vm.uiState.value.selectedExerciseId)
        compose.onAllNodesWithText("Lower B", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag(WorkoutTestTags.PROGRESS_LINE, useUnmergedTree = true)
            .assertTextEquals(WorkoutProgressCalculator.headline(progress).uppercase())
        compose.onNodeWithTag(WorkoutTestTags.PROGRESS_BAR, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed()
        compose.onAllNodesWithText("Timer controls ›").assertCountEquals(0)
    }

    @Test
    fun onItsSideIdleRestStepsBackAndTheCommitStaysOnScreenRestingOrNot() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1))
        compose.showWorkoutScreen(vm, width = 640.dp, height = 360.dp)
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertIsDisplayed().assertTextEquals("Timer controls ›")
        assertWhollyOnScreen(WorkoutTestTags.LOG_SET, windowHeight = 360.dp)
        assertTheFloorKeepsRoom()
        vm.startSelectedRest()
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.restTimerState.value.running }
        compose.waitForIdle()
        // A running rest is shown whatever the orientation, and the commit keeps its place.
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed()
        assertWhollyOnScreen(WorkoutTestTags.LOG_SET, windowHeight = 360.dp)
        assertTheFloorKeepsRoom()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun theDockIsPinnedUnderTheFloorAndTheHeaderSitsAboveIt() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(2))
        compose.showWorkoutScreen(vm, width = 360.dp, height = 800.dp)
        // Not part of the list: nothing of the dock scrolls with the floor.
        listOf(WorkoutTestTags.LOG_SET, WorkoutTestTags.TIMER_ROW).forEach { tag ->
            compose.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(WorkoutTestTags.CONTENT))).assertCountEquals(0)
        }
        val commitBefore = compose.onNodeWithTag(WorkoutTestTags.LOG_SET).getBoundsInRoot()
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.SET_HISTORY))
        compose.waitForIdle()
        assertEquals("the commit does not move when the floor scrolls", commitBefore, compose.onNodeWithTag(WorkoutTestTags.LOG_SET).getBoundsInRoot())
        // Top to bottom: the header's Finish, the floor, then the dock's row and its commit.
        val finish = compose.onNodeWithTag(WorkoutTestTags.FINISH).getBoundsInRoot()
        val floor = compose.onNodeWithTag(WorkoutTestTags.CONTENT).getBoundsInRoot()
        val row = compose.onNodeWithTag(WorkoutTestTags.TIMER_ROW).getBoundsInRoot()
        assertTrue("the header sits above the floor: $finish over $floor", finish.bottom <= floor.top)
        assertTrue("the dock sits under the floor: $row under $floor", row.top >= floor.bottom)
        assertTrue("the commit is the last thing on screen", commitBefore.bottom >= floor.bottom && commitBefore.bottom <= 800.dp)
    }

    /**
     * Header and dock together leave the scrolling floor room to log in: the 96 dp LandscapeChrome
     * budgeted for it (`LOG_MIN_DP`), which a chrome that grew would eat.
     */
    private fun assertTheFloorKeepsRoom() {
        val floor = compose.onNodeWithTag(WorkoutTestTags.CONTENT).getBoundsInRoot()
        assertTrue("the floor keeps room to log in, was ${floor.bottom - floor.top}", floor.bottom - floor.top >= 96.dp)
    }

    private fun assertWhollyOnScreen(tag: String, windowHeight: Dp) {
        val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getBoundsInRoot()
        assertTrue("$tag must lie wholly on screen, was $bounds", bounds.top >= 0.dp && bounds.bottom <= windowHeight)
        assertTrue("$tag keeps its full height, was $bounds", bounds.bottom - bounds.top >= 72.dp)
    }
}
