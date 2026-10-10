package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutProgress
import com.sinura.personaltrainer.domain.WorkoutProgressCalculator
import com.sinura.personaltrainer.ui.theme.Metrics
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
 * The floor's top, upright, through the real screen: the lift's options are one full-size ⋮
 * spoken "Workout options", on the header's own row beside Finish, not a row of their own; the
 * plan line under the routine's name is spoken once, as the plan's words, and the segmented bar
 * under it says nothing, since it says the same numbers as shape; and the floor shows one
 * identity, the selected lift's, whichever that is.
 *
 * These were lines of WorkoutOverflowMenu.kt, WorkoutHeader.kt and ActiveWorkoutScreen.kt read
 * as text (`contentDescription = "Workout options"`, `.size(Metrics.touchMin)`,
 * `overflow?.invoke()` inside `trailing = {`, `LiftOverflowMenu(` between `topBar = {` and
 * `bottomBar = {`, `modifier = progressLine(spoken)`, `.semantics { contentDescription =
 * spokenForm }`, `.clearAndSetSemantics { }` after `.testTag(WorkoutTestTags.PROGRESS_BAR)`, the
 * line's tag before the bar's, and one `ExerciseHeader(` in the screen).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class WorkoutHeaderRowRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler()))
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking {
            deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
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
    fun theOptionsButtonIsAFullSizeWorkoutOptionsOnTheHeadersRow() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1))
        compose.showWorkoutScreen(vm)
        compose.onAllNodesWithTag(WorkoutTestTags.LIFT_OPTIONS).assertCountEquals(1)
        val options = compose.onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(Metrics.touchMin)
            .assertWidthIsAtLeast(Metrics.touchMin)
        assertEquals(listOf("Workout options"), options.spokenDescriptions())
        val bounds = options.getBoundsInRoot()
        val finish = compose.onNodeWithTag(WorkoutTestTags.FINISH).getBoundsInRoot()
        val floor = compose.onNodeWithTag(WorkoutTestTags.CONTENT).getBoundsInRoot()
        assertTrue("on Finish's row, was $bounds beside $finish", bounds.top < finish.bottom && bounds.bottom > finish.top)
        assertTrue("after Finish, was $bounds beside $finish", bounds.left >= finish.right)
        assertTrue("in the header, above the floor, was $bounds over $floor", bounds.bottom <= floor.top)
    }

    @Test
    fun uprightThePlanLineIsSpokenOnceAboveABarThatSaysNothing() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1), withNextLift = true)
        compose.showWorkoutScreen(vm)
        val progress = WorkoutProgressCalculator.of(session = vm.uiState.value.session, selectedExerciseId = vm.uiState.value.selectedExerciseId)
        val spoken = WorkoutProgressCalculator.spoken(progress)
        val line = compose.onNodeWithTag(WorkoutTestTags.PROGRESS_LINE, useUnmergedTree = true).assertIsDisplayed()
        assertEquals(listOf(WorkoutProgressCalculator.headline(progress)), line.mergedTexts())
        assertEquals("the line is spoken as the plan's words", listOf(spoken), line.spokenDescriptions())
        compose.onAllNodes(hasContentDescription(spoken), useUnmergedTree = true).assertCountEquals(1)
        // The bar is the same numbers as shape: nothing under it is read, and it says nothing.
        val bar = compose.onNodeWithTag(WorkoutTestTags.PROGRESS_BAR, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("the bar clears whatever is drawn under it", bar.config.isClearingSemantics)
        // Nothing a reader would say: the bar carries its test tag and nothing else (no words,
        // role, heading, live region, state or progress). A shape the bar is clipped to may
        // publish a key of its own; that says nothing and is set aside.
        val keys = bar.config.map { it.key }.filterNot { it == SemanticsProperties.Shape }
        assertEquals("the bar says nothing", listOf(SemanticsProperties.TestTag.name), keys.map { it.name })
        val said = listOf(
            SemanticsProperties.Text,
            SemanticsProperties.ContentDescription,
            SemanticsProperties.StateDescription,
            SemanticsProperties.ProgressBarRangeInfo,
        )
        assertTrue("nothing drawn in the bar says anything", bar.children.none { segment -> said.any { segment.config.contains(it) } })
        val lineBounds = line.fetchSemanticsNode().boundsInRoot
        assertTrue("the words sit above the bar", lineBounds.bottom <= bar.boundsInRoot.top)
    }

    @Test
    fun theFloorShowsOneIdentityTheSelectedLifts() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1), withNextLift = true)
        compose.showWorkoutScreen(vm)
        assertOneIdentity(shown = FLOOR_LIFT_ID, hidden = FLOOR_NEXT_LIFT_ID)
        vm.selectExercise(FLOOR_NEXT_LIFT_ID)
        compose.awaitThat(what = "the next lift is selected and ready", now = vm.uiState::value) {
            val state = vm.uiState.value
            state.selectedExerciseId == FLOOR_NEXT_LIFT_ID && FLOOR_LIFT_READY(state)
        }
        compose.waitForIdle()
        assertOneIdentity(shown = FLOOR_NEXT_LIFT_ID, hidden = FLOOR_LIFT_ID)
    }

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun focusedHeaderKeepsItsHeightAndActionWidthAfterTheFirstSave() {
        val saved = mutableStateOf(false)
        compose.showFloor {
            Column(Modifier.width(412.dp)) {
                WorkoutHeader(
                    routineName = "F2 entry fixture · Lower A",
                    progress = WorkoutProgress(1, 1, 0, if (saved.value) 1 else 0, 12, emptyList()),
                    canFinish = saved.value,
                    compact = false,
                    onExit = {},
                    onFinish = {},
                    showDiscard = !saved.value,
                    focused = true,
                    overflow = { Box(Modifier.size(Metrics.touchMin)) },
                )
                Box(Modifier.height(1.dp).testTag("after-workout-header"))
            }
        }
        val beforeBottom = compose.onNodeWithTag("after-workout-header").fetchSemanticsNode().boundsInRoot.top
        val beforeAction = compose.onNodeWithTag(WorkoutTestTags.DISCARD).fetchSemanticsNode().boundsInRoot
        val beforeProgress = compose.onNodeWithTag(WorkoutTestTags.PROGRESS_LINE).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { saved.value = true }
        compose.waitForIdle()
        val finish = compose.onNodeWithTag(WorkoutTestTags.FINISH).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val afterProgress = compose.onNodeWithTag(WorkoutTestTags.PROGRESS_LINE).fetchSemanticsNode()
        assertEquals("Saving must not resize the end action", beforeAction.width, finish.width, 1f)
        assertEquals("Saving must not move the content below the header", beforeBottom,
            compose.onNodeWithTag("after-workout-header").fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals("Progress retains its vertical anchor", beforeProgress.top, afterProgress.boundsInRoot.top, 1f)
        assertEquals(listOf("1 of 1 exercise · 1 of 12 sets"),
            afterProgress.config[SemanticsProperties.Text].map { it.text })
    }

    private fun assertOneIdentity(shown: String, hidden: String) {
        compose.onAllNodesWithTag(WorkoutTestTags.CURRENT_LIFT).assertCountEquals(1)
        compose.onNodeWithTag(WorkoutTestTags.liftCard(shown)).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.liftCard(hidden)).assertDoesNotExist()
    }
}
