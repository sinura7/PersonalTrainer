package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.insertTestExercise
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A free workout with no lift yet, as the real screen shows it: the dock holds one thing, Add
 * exercise, standing where the commit will stand, with no rest card and no clock; the header
 * offers Discard in place of Finish, and it asks before deleting; there is no lift, so no ⋮
 * options. The list holds only the "Add a lift" placeholder, and the first lift takes its
 * place: the identity becomes the list's first row, and the dock becomes that lift's.
 *
 * These were lines of ActiveWorkoutScreen.kt and WorkoutHeader.kt read as text (`text = "Add
 * exercise"`, `WorkoutTestTags.DOCK_ADD_LIFT`, `emptySession`, `showDiscard = state.showDiscard`,
 * `PinnedDock(` inside `if (emptySession) {`, `WorkoutTestTags.DISCARD` in the header's trailing
 * slot, `if (!session.hasLifts()) {` as the one item that may precede the identity) and the
 * one FloorCompactChrome decision production reads here, `emptySessionHidesTimerDock()`. The
 * ViewModel's side (no rest, Discard, no Finish) is ActiveWorkoutViewModelTest's
 * emptySessionHidesRestAndOffersDiscard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class EmptySessionFloorRenderTest {
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
    fun anEmptyFreeWorkoutDocksAddExerciseAloneWithNoClock() {
        val vm = openEmptyFreeWorkout()
        val add = compose.onNodeWithTag(WorkoutTestTags.DOCK_ADD_LIFT).assertIsDisplayed().assertHeightIsAtLeast(Metrics.commit)
        assertEquals(listOf("Add exercise"), add.mergedTexts())
        // Pinned under the floor, never part of it.
        compose.onAllNodes(hasTestTag(WorkoutTestTags.DOCK_ADD_LIFT) and hasAnyAncestor(hasTestTag(WorkoutTestTags.CONTENT)))
            .assertCountEquals(0)
        val floor = compose.onNodeWithTag(WorkoutTestTags.CONTENT).getBoundsInRoot()
        assertTrue("Add exercise sits under the floor", add.getBoundsInRoot().top >= floor.bottom)
        // Nothing to rest after and nothing to log: no clock, no rest card, no commit.
        NO_CLOCK_OR_COMMIT.forEach { tag -> compose.onNodeWithTag(tag).assertDoesNotExist() }
        add.performClick()
        compose.awaitThat(what = "Add exercise opened the picker", now = vm.uiState::value) { vm.uiState.value.showExercisePicker }
    }

    @Test
    fun anEmptySessionOffersDiscardNotFinishAndAsksBeforeDeleting() {
        val vm = openEmptyFreeWorkout()
        val discard = compose.onNodeWithTag(WorkoutTestTags.DISCARD).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin)
        assertEquals(listOf(EndWorkoutCopy.HEADER_DISCARD), discard.mergedTexts())
        compose.onNodeWithTag(WorkoutTestTags.FINISH).assertDoesNotExist()
        // No lift, so nothing for the lift's options to act on.
        compose.onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS).assertDoesNotExist()
        val floor = compose.onNodeWithTag(WorkoutTestTags.CONTENT).getBoundsInRoot()
        assertTrue("Discard sits on the header, above the floor", discard.getBoundsInRoot().bottom <= floor.top)
        discard.performClick()
        compose.onNodeWithText(DISCARD_TITLE).assertIsDisplayed()
        val sessionId = checkNotNull(vm.uiState.value.session).id
        assertNotNull("asking is not deleting", runBlocking { deps.workoutRepository.getSession(sessionId) })
    }

    @Test
    fun theFirstLiftReplacesThePlaceholderAsTheListsTopRow() {
        val vm = openEmptyFreeWorkout()
        compose.onNodeWithText(PLACEHOLDER).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_LIFT).assertDoesNotExist()
        val add = compose.onNodeWithTag(WorkoutTestTags.DOCK_ADD_LIFT).getBoundsInRoot()
        val lift = runBlocking { insertTestExercise(deps = deps, id = "leg-curl", name = "Leg Curl", muscleGroup = "Hamstrings") }
        vm.addExercise(lift)
        compose.awaitThat(what = "the added lift is ready", now = vm.uiState::value) { FLOOR_LIFT_READY(vm.uiState.value) }
        compose.waitForIdle()
        compose.onNodeWithText(PLACEHOLDER).assertDoesNotExist()
        // Nothing precedes the identity: it is the list's first row, right under its top padding.
        val list = compose.onNodeWithTag(WorkoutTestTags.CONTENT).getBoundsInRoot()
        val identity = compose.onNodeWithTag(WorkoutTestTags.CURRENT_LIFT).assertIsDisplayed().getBoundsInRoot()
        assertEquals("the identity is the list's first row", (list.top + Metrics.space3).value, identity.top.value, 1f)
        // The dock is the lift's now, and its commit stands exactly where Add exercise stood.
        compose.onNodeWithTag(WorkoutTestTags.DOCK_ADD_LIFT).assertDoesNotExist()
        val commit = compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed().getBoundsInRoot()
        assertEquals(add.left, commit.left)
        assertEquals(add.right, commit.right)
        assertEquals(add.bottom, commit.bottom)
    }

    /** A free workout with no lift yet, on screen once its entry takes taps (there is no lift to wait for). */
    private fun openEmptyFreeWorkout(): ActiveWorkoutViewModel {
        val sessionId = runBlocking { deps.workoutRepository.startFreeWorkout(focusTitle = "Arms") }.id
        val vm = floorViewModel(deps = deps, sessionId = sessionId).also(viewModels::add)
        val unlocked: (ActiveWorkoutUiState) -> Boolean = { state -> !state.entryLocked }
        compose.showWorkoutScreen(vm, ready = unlocked)
        assertTrue("no lift yet", checkNotNull(vm.uiState.value.session).exercises.isEmpty())
        return vm
    }

    private companion object {
        const val PLACEHOLDER = "Add a lift"
        const val DISCARD_TITLE = "Discard this workout?"
        val NO_CLOCK_OR_COMMIT = listOf(
            WorkoutTestTags.TIMER_ROW,
            WorkoutTestTags.REST_IDLE,
            WorkoutTestTags.REST_BAR,
            WorkoutTestTags.COMPANION_CLOCK,
            WorkoutTestTags.LOG_SET,
        )
    }
}
