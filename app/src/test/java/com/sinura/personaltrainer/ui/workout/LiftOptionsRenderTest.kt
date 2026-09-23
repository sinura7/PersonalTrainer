package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.SetStopwatchCopy
import com.sinura.personaltrainer.domain.UndoKind
import com.sinura.personaltrainer.domain.WeightUnit
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
 * The lift's options behind the header's ⋮, tapped through the real screen and ViewModel:
 * Skip for now parks the lift and moves on; a lift with saved sets keeps Swap and Remove in
 * view, disabled, with the reason ("Delete its sets first"); an unlogged lift can be
 * removed; Session summary opens the session's own numbers (how long in minutes, how many
 * working sets, how much was lifted, in the lifter's unit). And a lift switch while the set
 * clock runs asks first, since switching stops timing.
 *
 * These were lines of WorkoutOverflowMenu.kt, ActiveWorkoutScreen.kt and
 * WorkoutSessionSummary.kt read as text (`onSkip = viewModel::skipForNow`, `canEdit =
 * logged.isEmpty()`, `enabled = canEdit`, `CurrentLiftCopy.EDIT_BLOCKED_REASON`, `onSummary =
 * { sessionSummaryOpen = true }`, `"Elapsed:"`, `"Working sets:"`, `"External volume:"`,
 * `SetStopwatchCopy.SWITCH_TITLE`,
 * `confirmStopTimingAndSwitch`). The words are held as the lifter reads them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class LiftOptionsRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler()))
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.LBS) }
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
    fun skipForNowParksTheLiftAndMovesOn() {
        val vm = openLegExtension(deps, viewModels, loggedSets = emptyList(), withNextLift = true)
        compose.showWorkoutScreen(vm)
        openOptions()
        compose.onNodeWithText("Skip for now").assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.selectedExerciseId == FLOOR_NEXT_LIFT_ID }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.liftCard(FLOOR_NEXT_LIFT_ID)).assertIsDisplayed()
        // Parked, not gone: both lifts are still the session's, and nothing is offered back.
        assertEquals(2, checkNotNull(vm.uiState.value.session).exercises.size)
        assertTrue(vm.undoEntries.value.isEmpty())
    }

    @Test
    fun aLiftWithSavedSetsKeepsSwapAndRemoveInViewAndSaysWhyTheyWait() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1))
        compose.showWorkoutScreen(vm)
        openOptions()
        compose.onNode(hasText(SWAP, substring = true) and hasClickAction()).assertIsDisplayed().assertIsNotEnabled()
        compose.onNode(hasText(REMOVE, substring = true) and hasClickAction()).assertIsDisplayed().assertIsNotEnabled()
        // Said under each, in words, rather than a control that silently vanished.
        compose.onAllNodesWithText(BLOCKED, useUnmergedTree = true).assertCountEquals(2)
        compose.onNode(hasText(REMOVE, substring = true) and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals("a disabled Remove removes nothing", 1, checkNotNull(vm.uiState.value.session).exercises.size)
    }

    @Test
    fun anUnloggedLiftCanBeRemovedAndSaysNothingAboutSets() {
        val vm = openLegExtension(deps, viewModels, loggedSets = emptyList(), withNextLift = true)
        compose.showWorkoutScreen(vm)
        openOptions()
        compose.onNode(hasText(SWAP, substring = true) and hasClickAction()).assertIsEnabled()
        compose.onAllNodesWithText(BLOCKED, useUnmergedTree = true).assertCountEquals(0)
        compose.onNode(hasText(REMOVE, substring = true) and hasClickAction()).assertIsEnabled().performClick()
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.undoEntries.value.lastOrNull()?.offer?.kind == UndoKind.REMOVED_LIFT }
        // The offer is pushed as the write returns; the session follows when Room's observer
        // re-reads it, a moment later on its own thread. Wait for that, then hold the count.
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.session?.exercises?.size == 1 }
        assertEquals(1, checkNotNull(vm.uiState.value.session).exercises.size)
    }

    @Test
    fun sessionSummaryOpensFromTheOptionsWithTheSessionsNumbers() {
        val sessionId = runBlocking {
            val id = seedLegExtension(deps = deps, loggedSets = floorSets(2))
            // Started twenty-five minutes before now, by the clock that stamped it, so the
            // elapsed line has a number worth reading.
            val dao = deps.database.workoutDao()
            val row = checkNotNull(dao.getSessionRow(id))
            dao.updateSession(row.copy(startedAt = row.startedAt - 25 * ONE_MINUTE_MS))
            id
        }
        val vm = floorViewModel(deps = deps, sessionId = sessionId).also(viewModels::add)
        compose.showWorkoutScreen(vm)
        openOptions()
        compose.onNodeWithText("Session summary").assertIsDisplayed().performClick()
        compose.onNodeWithText("Session summary").assertIsDisplayed()
        // The session's facts, which the header leaves out: how long, how many, how much.
        val facts = compose.onNodeWithText("Working sets: ", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
            .mergedTexts()
            .single()
            .split("\n\n")
        val elapsed = facts.firstNotNullOfOrNull { ELAPSED.matchEntire(it) }
        assertTrue("elapsed is said in whole minutes, was $facts", elapsed != null)
        assertTrue("twenty-five minutes in, was $facts", checkNotNull(elapsed).groupValues[1].toInt() in 25..26)
        assertTrue("was $facts", "Working sets: 2" in facts)
        // Two sets of 70 lb × 10, in pounds as the lifter reads them.
        assertTrue("was $facts", "External volume: 1,400 lb" in facts)
        compose.onNodeWithText("Done").performClick()
        compose.onAllNodesWithText("Working sets: 2", substring = true).assertCountEquals(0)
    }

    @Test
    fun switchingLiftWhileTheSetClockRunsAsksFirstAndStopsIt() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1), withNextLift = true)
        compose.showWorkoutScreen(vm)
        compose.onNodeWithTag(WorkoutTestTags.START_SET_CLOCK).performClick()
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.setStopwatch.value.running }
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).performClick()
        compose.onNodeWithTag(WorkoutTestTags.liftSwitcherRow(FLOOR_NEXT_LIFT_ID)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(SetStopwatchCopy.SWITCH_TITLE).assertIsDisplayed()
        assertEquals("asked, not yet switched", FLOOR_LIFT_ID, vm.uiState.value.selectedExerciseId)
        assertTrue(vm.setStopwatch.value.running)
        compose.onNodeWithText(SetStopwatchCopy.SWITCH_CONFIRM).performClick()
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.selectedExerciseId == FLOOR_NEXT_LIFT_ID }
        assertTrue("switching stopped the clock", !vm.setStopwatch.value.running)
        compose.onAllNodesWithText(SetStopwatchCopy.SWITCH_TITLE).assertCountEquals(0)
    }

    private fun openOptions() {
        compose.onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS).performClick()
        compose.waitForIdle()
    }

    private companion object {
        const val ONE_MINUTE_MS = 60_000L
        const val SWAP = "Swap lift"
        const val REMOVE = "Remove lift"
        const val BLOCKED = "Delete its sets first"
        val ELAPSED = Regex("Elapsed: (\\d+) min")
    }
}
