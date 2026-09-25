package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rest page draws the Log's Next line, and no Next line where the Log shows none (W2b-4,
 * owner decision of 24 September 2026). After the lift's last planned set the Log hides its
 * Next card; the page went on drawing the last set again as "Next: 100 kg × 5".
 *
 * Composed for real: [RestTimerScreen] through [RestTimerViewModel], beside the Log's
 * [ActiveWorkoutViewModel] on the same store, which the page reads the Log's entry from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestPageNextLineTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val floors = mutableListOf<RestTimerViewModel>()
    private val workouts = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler()))
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        runBlocking {
            floors.forEach { it.clearAndJoinForTest() }
            workouts.forEach { it.clearAndJoinForTest() }
        }
        floors.clear()
        workouts.clear()
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun afterTheLiftsLastPlannedSetThePageDrawsNoNextLine() {
        val sessionId = seedOneLoggedSet(targetSets = 1)
        val call = theLogsCall(sessionId, what = "the Log's call after the lift's planned sets") {
            it?.reasonCode == SetMicroRecCalculator.LIFT_DONE
        }
        assertFalse("the Log hides its Next card", SetMicroRecCopy.visibleOnEntry(checkNotNull(call)))

        openThePage(sessionId)

        compose.onAllNodesWithTag(RestFloorTags.NEXT).assertCountEquals(0)
    }

    @Test
    fun withPlannedSetsStillToDoThePageDrawsTheLogsNextLine() {
        // The other side of the one above, so its "no Next line" cannot be a tag nobody draws.
        val sessionId = seedOneLoggedSet(targetSets = 3)
        val call = theLogsCall(sessionId, what = "the Log's call for the second set") { it != null }
        assertTrue("the Log shows its Next card", SetMicroRecCopy.visibleOnEntry(checkNotNull(call)))

        openThePage(sessionId)

        compose.onNodeWithTag(RestFloorTags.NEXT)
            .assertIsDisplayed()
            .assertTextEquals(SetMicroRecCopy.line(call, LoadClass.LOADED, WeightUnit.KG))
    }

    /** A live session whose one lift has a working 100 kg × 5 logged, of [targetSets] planned. */
    private fun seedOneLoggedSet(targetSets: Int): String = runBlocking {
        seedTestWorkout(
            deps = deps,
            targetSets = targetSets,
            loggedSets = listOf(TestSetInput(weightKg = 100.0, reps = 5)),
        ).session.id
    }

    /** The Log on [sessionId], once its coach call is the one [what] names; the call. */
    private fun theLogsCall(
        sessionId: String,
        what: String,
        predicate: (SetMicroRec?) -> Boolean,
    ): SetMicroRec? {
        val workout = ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(workouts::add)
        return runBlocking {
            withTimeoutOrNull(TestWaits.FLOW_MS) { workout.microRec.first(predicate) }
                ?: throw AssertionError("Never saw $what; the Log's call was ${workout.microRec.value}")
        }
    }

    /** The rest page on [sessionId], composed and showing the lift's last set. */
    private fun openThePage(sessionId: String) {
        val vm = RestTimerViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(floors::add)
        compose.showFloor { RestTimerScreen(onClose = {}, viewModel = vm) }
        compose.awaitThat(what = "the page shows the lift's last set", now = vm.uiState::value) {
            compose.onAllNodesWithText(LAST_SET).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(LAST_SET, vm.uiState.value.floor.lastSetLine)
    }

    private companion object {
        const val LAST_SET = "Last set · 100 kg × 5"
    }
}
