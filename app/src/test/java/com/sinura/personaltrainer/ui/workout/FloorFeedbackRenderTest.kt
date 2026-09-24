package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.Motion
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
 * What the floor says and does to the hand when a set lands, through the real screen and
 * ViewModel with a [FeltView] as the screen's view: a set that breaks a record is felt as the
 * commit and then, a beat later, one gold accent (never a celebration's triple); a set that
 * breaks nothing is the commit alone; the saved line is announced once, from the screen; and
 * taking the coach's call from its Why sheet is a detent, not a commit, and logs nothing.
 *
 * These were lines of ActiveWorkoutScreen.kt, Haptics.kt and NextSetRecommendation.kt read as
 * text (`Haptics.recordAccent(view)`, `fun recordAccent(`, `PR_ACCENT_DELAY_MS`,
 * `view.announceForAccessibility(receipt.line)`, `Haptics.tick(view)`), which a renamed
 * helper failed while a silent record passed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class FloorFeedbackRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private lateinit var felt: FeltView

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
    fun aRecordIsFeltAsTheCommitAndThenOneGoldAccentABeatLater() {
        // Last time's best was 50 lb; 70 lb today is a record.
        val vm = openLegExtensionAfter(priorBestLbs = 50.0)
        showFelt(vm)
        logSeventy(vm)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.personalRecord.value != null }
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { felt.confirmations().size >= 2 }
        compose.mainClock.advanceTimeBy(ONE_SECOND_MS)
        compose.waitForIdle()
        val confirmations = felt.confirmations()
        assertEquals("the commit and one accent, was ${felt.haptics}", 2, confirmations.size)
        val (commit, accent) = confirmations
        assertTrue("the accent follows the commit by a beat, was ${accent - commit} ms", accent - commit >= Motion.PR_ACCENT_DELAY_MS)
    }

    @Test
    fun aSetThatBreaksNothingIsFeltAsTheCommitAlone() {
        // Last time's best was 100 lb; 70 lb today breaks nothing.
        val vm = openLegExtensionAfter(priorBestLbs = 100.0)
        showFelt(vm)
        logSeventy(vm)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { felt.confirmations().isNotEmpty() }
        compose.mainClock.advanceTimeBy(ONE_SECOND_MS)
        compose.waitForIdle()
        assertEquals("one commit, was ${felt.haptics}", 1, felt.confirmations().size)
        assertTrue("nothing was broken", vm.personalRecord.value == null)
    }

    @Test
    fun aSavedSetIsAnnouncedOnceFromTheScreen() {
        val vm = openLegExtensionAfter(priorBestLbs = null)
        showFelt(vm)
        logSeventy(vm)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.logReceipt.value != null }
        val line = checkNotNull(vm.logReceipt.value).line
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { felt.announcements.isNotEmpty() }
        // Past the receipt's dwell: said once, not again as it clears.
        compose.mainClock.advanceTimeBy(Motion.STATUS_DWELL_MS + ONE_SECOND_MS)
        compose.waitForIdle()
        assertEquals(listOf(line), felt.announcements.toList())
    }

    @Test
    fun usingTheCoachsCallFromWhyIsADetentNotACommitAndLogsNothing() {
        // Nine of ten: the coach calls one more rep at the same weight.
        val vm = openLegExtensionAfter(priorBestLbs = null, loggedToday = listOf(TestSetInput(weightKg = FLOOR_KG70, reps = 9, rpe = 8)))
        showFelt(vm)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.microRec.value != null }
        vm.setReps(8)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.draft.reps == 8 }
        compose.waitForIdle()
        val rec = checkNotNull(vm.microRec.value)
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.MICRO_REC_WHY))
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).performClick()
        compose.waitForIdle()
        val before = felt.felt().size
        compose.onNodeWithText(SetMicroRecCopy.USE_SUGGESTION).performClick()
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.draft.reps == rec.nextReps }
        compose.waitForIdle()
        val sinceTheTap = felt.felt().drop(before)
        assertTrue("Use suggestion is a detent, was $sinceTheTap", HapticFeedbackConstants.CLOCK_TICK in sinceTheTap)
        assertTrue("nothing was saved, so nothing may feel like a commit, was $sinceTheTap", HapticFeedbackConstants.CONFIRM !in sinceTheTap)
        assertEquals("the call fills the entry; it never logs", 1, checkNotNull(vm.uiState.value.session).sets.size)
    }

    private fun logSeventy(vm: ActiveWorkoutViewModel) {
        vm.setWeight(FLOOR_KG70)
        vm.setReps(10)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.draft.weightKg == FLOOR_KG70 && vm.uiState.value.draft.reps == 10 }
        compose.waitForIdle()
        felt.haptics.clear()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
    }

    /** The real screen, with [felt] as its view, once the entry would log a set. */
    private fun showFelt(vm: ActiveWorkoutViewModel) {
        felt = compose.attachedFeltView()
        val canLog: (ActiveWorkoutUiState) -> Boolean = { state -> FLOOR_LIFT_READY(state) && state.canLog }
        compose.showWorkoutScreen(vm, view = felt, ready = canLog)
    }

    /**
     * A leg extension today, after a finished session whose best set was [priorBestLbs] × 10
     * (none when null), with [loggedToday] already saved.
     */
    private fun openLegExtensionAfter(priorBestLbs: Double?, loggedToday: List<TestSetInput> = emptyList()): ActiveWorkoutViewModel {
        val sessionId = runBlocking {
            val prior = priorBestLbs?.let { lbs ->
                seedTestWorkout(
                    deps = deps,
                    exerciseId = FLOOR_LIFT_ID,
                    exerciseName = "Leg Extension",
                    routineName = "Lower B",
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = FLOOR_KG70,
                    restSeconds = 120,
                    loggedSets = listOf(TestSetInput(weightKg = WeightConverter.lbsToKg(lbs), reps = 10)),
                    finish = true,
                )
            }
            if (prior == null) {
                seedTestWorkout(
                    deps = deps,
                    exerciseId = FLOOR_LIFT_ID,
                    exerciseName = "Leg Extension",
                    routineName = "Lower B",
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = FLOOR_KG70,
                    restSeconds = 120,
                    loggedSets = loggedToday,
                ).session.id
            } else {
                val today = deps.workoutRepository.startRoutine(prior.routine)
                loggedToday.forEach { set ->
                    deps.workoutRepository.logSet(
                        sessionId = today.id,
                        exerciseId = FLOOR_LIFT_ID,
                        weightKg = set.weightKg,
                        reps = set.reps,
                        rpe = set.rpe,
                        isWarmup = set.isWarmup,
                    )
                }
                today.id
            }
        }
        return floorViewModel(deps = deps, sessionId = sessionId).also(viewModels::add)
    }

    private companion object {
        const val ONE_SECOND_MS = 1_000L
    }
}
