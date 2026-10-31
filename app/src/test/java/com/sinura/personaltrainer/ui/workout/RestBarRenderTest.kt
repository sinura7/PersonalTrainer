package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.Warn
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

/**
 * v4 blueprint 3.4 and V4: rest is an inline bar directly above the next set, not a page and
 * not an immersion that hides the entry. The next set stays editable while rest counts down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestBarRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    private val remaining = mutableStateOf(92)
    private val running = mutableStateOf(true)
    private val nudges = mutableListOf<Int>()
    private var skips = 0
    private var edits = 0

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

    private fun showBar(fontScale: Float = 1f) {
        compose.showFloor(fontScale = fontScale) {
            val left = remaining.value
            RestBar(
                remainingSeconds = left,
                totalSeconds = 120,
                running = running.value,
                overtime = RestBarCopy.overtime(running.value, left),
                onNudge = { nudges += it },
                onSkip = { skips += 1 },
                onEditDuration = { edits += 1 },
            )
        }
    }

    private fun startRestOnTheFloor(): ActiveWorkoutViewModel {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1))
        compose.showWorkoutScreen(vm)
        vm.startSelectedRest()
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.restTimerState.value.running }
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.REST_BAR)).fetchSemanticsNodes().isNotEmpty()
        }
        return vm
    }

    @Test
    fun countsDownAboveNextSet() {
        startRestOnTheFloor()
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.REST_BAR))
        val bar = compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed()
        bar.assertHeightIsAtLeast(Metrics.restBar)
        compose.onNodeWithTag(WorkoutTestTags.REST_PROGRESS).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsDisplayed()
        val barBottom = bar.getBoundsInRoot().bottom
        val entryTop = compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).getBoundsInRoot().top
        assertTrue("the bar sits above the next set: bar $barBottom, entry $entryTop", barBottom <= entryTop)
        // One rest surface: the dock's card steps aside, and no Start set now replaces Log.
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
    }

    @Test
    fun nextSetEditableDuringRest() {
        val vm = startRestOnTheFloor()
        val before = vm.uiState.value.draft.reps
        compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsDisplayed()
        vm.setReps((before ?: 10) + 1)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.draft.reps == (before ?: 10) + 1 }
        compose.waitForIdle()
        assertTrue("editing the next set keeps rest running", vm.restTimerState.value.running)
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed()
        assertEquals(WorkoutPrimaryKind.LOG_SET, vm.primaryAction.value.kind)
    }

    @Test
    fun overtimeShowsWarn() {
        remaining.value = -12
        showBar()
        val clock = compose.onNode(hasText("+0:12"), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(Warn, clock.textLayout().layoutInput.style.color)
        remaining.value = 30
        compose.waitForIdle()
        val counting = compose.onNode(hasText("0:30"), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(TextPrimary, counting.textLayout().layoutInput.style.color)
        // Spoken politely, and only once per 15 s bucket.
        compose.onNodeWithTag(WorkoutTestTags.REST_CLOCK)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertEquals(listOf("Rest, 0 minutes 30 seconds left"), compose.onNodeWithTag(WorkoutTestTags.REST_CLOCK).spokenDescriptions())
        assertEquals(45, RestBarCopy.spokenBucketSeconds(31))
        assertEquals(30, RestBarCopy.spokenBucketSeconds(30))
        assertEquals(0, RestBarCopy.spokenBucketSeconds(-5))
    }

    @Test
    fun nudgeAndSkip() {
        showBar()
        compose.onNodeWithTag(WorkoutTestTags.REST_MINUS).assertHeightIsAtLeast(Metrics.touchMin).performClick()
        compose.onNodeWithTag(WorkoutTestTags.REST_PLUS).assertHeightIsAtLeast(Metrics.touchMin).performClick()
        assertEquals(listOf(-15, 15), nudges)
        compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).performClick()
        assertEquals(1, skips)
        compose.onNodeWithTag(WorkoutTestTags.REST_CLOCK).performClick()
        assertEquals("the clock opens the length sheet", 1, edits)
    }

    @Test
    fun fontScaleTwoWraps() {
        showBar(fontScale = 2f)
        val width = 360.dp
        listOf(WorkoutTestTags.REST_CLOCK, WorkoutTestTags.REST_MINUS, WorkoutTestTags.REST_PLUS, WorkoutTestTags.REST_SKIP)
            .forEach { tag ->
                val node = compose.onNodeWithTag(tag).assertIsDisplayed()
                assertTrue("$tag stays inside the window at 2x", node.getBoundsInRoot().right <= width)
            }
        // Wrapped or not, no control lands on another: the row flows rather than clips.
        val boxes = listOf(WorkoutTestTags.REST_CLOCK, WorkoutTestTags.REST_MINUS, WorkoutTestTags.REST_PLUS, WorkoutTestTags.REST_SKIP)
            .map { compose.onNodeWithTag(it).getBoundsInRoot() }
        boxes.forEachIndexed { i, a ->
            boxes.drop(i + 1).forEach { b ->
                val apart = a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top
                assertTrue("controls overlap at 2x: $a and $b", apart)
            }
        }
    }
}
