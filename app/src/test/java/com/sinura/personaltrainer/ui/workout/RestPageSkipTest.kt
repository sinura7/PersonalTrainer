package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rest page's Skip names the rest the page drew, as the notification's does (W2b-3, owner
 * decision of 24 September 2026). It used to stop whatever ran when the tap was read: a rest
 * that ran out as Skip was tapped lost its "Rest complete", and a newer rest (a set logged on
 * the Log as the page opened) was ended.
 *
 * Composed for real: [RestTimerScreen] through [RestTimerViewModel] and the shared rest store.
 * A tap is the Skip as drawn: its click action is taken while the page shows the rest, the
 * store then moves on (a finish, the next set's rest, a ±15), and the action runs before the
 * page redraws.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestPageSkipTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<RestTimerViewModel>()

    private val rest get() = deps.restTimerController

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler()))
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext())
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
    fun aSkipTappedAsTheRestRanOutKeepsItComplete() {
        val shown = openThePageOnARunningRest()
        val skip = drawnSkip()

        assertTrue("the rest runs out first", rest.completeIfCurrent(shown.timerId))
        skip()
        compose.waitForIdle()

        assertEquals("still done, not skipped", shown.timerId, rest.lastCompletedTimerId.value)
        compose.onNodeWithTag(RestFloorTags.BACK_TO_BAR).assertIsDisplayed()
    }

    @Test
    fun aSkipForTheRestItDrewLeavesANewerRestRunning() {
        val shown = openThePageOnARunningRest()
        val skip = drawnSkip()

        rest.start(120, shown.sessionId)
        val next = deps.restTimerStore.current().timerId
        skip()
        compose.waitForIdle()

        assertTrue("the newer rest keeps running", deps.restTimerStore.current().running)
        assertEquals(next, deps.restTimerStore.current().timerId)
        compose.onNodeWithTag(RestFloorTags.SKIP).assertIsDisplayed()
    }

    @Test
    fun aSkipOnTheRestItShowsEndsIt() {
        openThePageOnARunningRest()

        compose.onNodeWithTag(RestFloorTags.SKIP).performClick()

        assertFalse("the rest the page shows ends", deps.restTimerStore.current().running)
        assertNull("a skip is not a finish", rest.lastCompletedTimerId.value)
        compose.onNodeWithTag(RestFloorTags.START).assertIsDisplayed()
    }

    @Test
    fun aSkipDrawnJustBeforeAPlusFifteenStillEndsTheRest() {
        // To the owner a ±15 is the same rest, under a new id.
        openThePageOnARunningRest()
        val skip = drawnSkip()

        rest.adjust(15)
        skip()

        assertFalse("the +15 of the rest shown ends", deps.restTimerStore.current().running)
    }

    /** A live session's rest page, up and showing its running rest. Returns that rest. */
    private fun openThePageOnARunningRest(): RestTimerSnapshot {
        val sessionId = runBlocking { seedTestWorkout(deps).session.id }
        val vm = RestTimerViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(viewModels::add)
        rest.start(90, sessionId)
        compose.showFloor { RestTimerScreen(onClose = {}, viewModel = vm) }
        compose.awaitThat(what = "the page shows the running rest's Skip", now = vm.uiState::value) {
            compose.onAllNodesWithTag(RestFloorTags.SKIP).fetchSemanticsNodes().isNotEmpty()
        }
        return deps.restTimerStore.current()
    }

    /** The Skip as the page drew it; running it later is a tap the page has not redrawn for. */
    private fun drawnSkip(): () -> Unit {
        val click = compose.onNodeWithTag(RestFloorTags.SKIP).fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action
        return { checkNotNull(click) { "the drawn Skip has no click" }.invoke() }
    }
}
