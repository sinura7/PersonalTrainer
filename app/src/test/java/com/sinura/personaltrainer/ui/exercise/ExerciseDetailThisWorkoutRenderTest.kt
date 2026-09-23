package com.sinura.personaltrainer.ui.exercise

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.ExerciseFloorStats
import com.sinura.personaltrainer.domain.ExerciseFloorStatsCalculator
import com.sinura.personaltrainer.domain.FloorStatCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags
import com.sinura.personaltrainer.ui.workout.showFloor
import com.sinura.personaltrainer.util.QuantityFormat
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Packet W1d: Best set and Volume are one tap away in the lift's Details.
 *
 * At large text (font 1.6 and above) the floor's stats row keeps Last alone, before and after
 * the first working set, so that set never moves the entry (owner decision of 23 September 2026,
 * ADR-030). The other two cells have to be somewhere, and Details — the lift's picture on the
 * floor — only read finished sessions: it could not show today's Best set or Volume at all.
 * Now, while the workout in progress holds the lift, Details leads with them, in the floor's own
 * words from the floor's own calculator, without the Last cell the floor keeps.
 *
 * ADR-032's matrix, each frame written to `app/build/screen-renders/w1d/`: the two cells must be
 * reachable in every one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class ExerciseDetailThisWorkoutRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ExerciseDetailViewModel>()

    /**
     * The real main looper, as on the phone, not an unconfined Main: Details computes its history
     * on the compute dispatcher, and an unconfined Main would carry that work's results — and the
     * screen's state writes with them — onto a background thread the composition then lays out on.
     */
    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.LBS) }
    }

    /** Cancelled on the main looper and drained there: joining from it with runBlocking would wait on itself. */
    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        viewModels.clear()
        deps.close()
    }

    @Test
    fun detailsShowsTodaysBestAndVolumeAt360By640() = showsThisWorkout(360, 640, 1f)

    @Test
    fun detailsShowsTodaysBestAndVolumeAt360By640Font16() = showsThisWorkout(360, 640, 1.6f)

    @Test
    fun detailsShowsTodaysBestAndVolumeAt360By640Font20() = showsThisWorkout(360, 640, 2f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun detailsShowsTodaysBestAndVolumeAt412By840() = showsThisWorkout(412, 840, 1f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun detailsShowsTodaysBestAndVolumeAt412By840Font16() = showsThisWorkout(412, 840, 1.6f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun detailsShowsTodaysBestAndVolumeAt412By840Font20() = showsThisWorkout(412, 840, 2f)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun detailsShowsTodaysBestAndVolumeInLandscape() = showsThisWorkout(800, 360, 1f)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun detailsShowsTodaysBestAndVolumeInLandscapeFont16() = showsThisWorkout(800, 360, 1.6f)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun detailsShowsTodaysBestAndVolumeInLandscapeFont20() = showsThisWorkout(800, 360, 2f)

    /** A lift's first session: nothing finished yet, but today's sets are the floor's and show. */
    @Test
    fun aFirstSessionShowsTodaysBestAndVolumeAboveNothingLoggedYet() {
        val (sessionId, vm) = seed(lastTimeLb = null)
        show(vm, 360, 640, 2f)
        compose.waitUntil(WAIT_MS) { vm.uiState.value.exercise != null }
        // Nothing finished to beat, so the record names its kind rather than "Today".
        assertThisWorkout(sessionId, "first-session-360x640-font2.0", best = listOf("Best set · Est. 1RM", "135 × 10"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Nothing logged yet"))
        compose.onNode(hasText("Nothing logged yet")).assertIsDisplayed()
    }

    /** Opened from History with no workout running, Details is exactly as before. */
    @Test
    fun withNoWorkoutInProgressDetailsShowsNoTodayCells() {
        runBlocking {
            seedTestWorkout(
                deps = deps, exerciseId = LIFT, exerciseName = "Leg Extension", routineName = "Lower B",
                targetReps = 10, targetWeightKg = WeightConverter.lbsToKg(90.0),
                loggedSets = listOf(TestSetInput(weightKg = WeightConverter.lbsToKg(90.0), reps = 5)), finish = true,
            )
        }
        val vm = viewModel()
        show(vm, 360, 640, 1f)
        compose.waitUntil(WAIT_MS) { vm.uiState.value.history.sessions.isNotEmpty() }
        compose.waitForIdle()
        capture("no-workout-360x640-font1.0")
        listOf(WorkoutTestTags.STAT_LAST, WorkoutTestTags.STAT_BEST, WorkoutTestTags.STAT_VOLUME).forEach { tag ->
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().let { assertTrue("$tag must not show without a workout", it.isEmpty()) }
        }
    }

    private fun showsThisWorkout(widthDp: Int, heightDp: Int, fontScale: Float) {
        val (sessionId, vm) = seed(lastTimeLb = 90.0)
        show(vm, widthDp, heightDp, fontScale)
        compose.waitUntil(WAIT_MS) { vm.uiState.value.history.sessions.isNotEmpty() }
        assertThisWorkout(sessionId, "details-${widthDp}x$heightDp-font$fontScale", best = listOf("Best set · Today", "135 × 10"))
    }

    /**
     * Best and Volume are shown, reachable, in the floor's words and TalkBack's, and the Last cell
     * the floor keeps is not repeated.
     */
    private fun assertThisWorkout(sessionId: String, frame: String, best: List<String>) {
        val found = runCatching {
            compose.waitUntil(STATS_WAIT_MS) { compose.onAllNodesWithTag(WorkoutTestTags.STAT_BEST).fetchSemanticsNodes().isNotEmpty() }
        }.isSuccess
        compose.waitForIdle()
        capture(frame)
        assertTrue("Details must show today's Best set and Volume for a lift in the workout in progress", found)
        val floor = floorStats(sessionId)
        val volume = floor.volumeColumn(WeightUnit.LBS)
        val volumeValue = "${QuantityFormat.formatVolumeNumber(floor.work.volumeKg, WeightUnit.LBS)} ${volume.label}"
        val expected = mapOf(
            WorkoutTestTags.STAT_BEST to listOf(floor.bestSet.label + FloorStatCopy.DETAIL_JOIN + floor.bestSet.detail, floor.bestSet.value),
            WorkoutTestTags.STAT_VOLUME to listOf(FloorStatCopy.VOLUME + FloorStatCopy.DETAIL_JOIN + FloorStatCopy.VOLUME_DETAIL, volumeValue),
        )
        val spoken = mapOf(
            WorkoutTestTags.STAT_BEST to floor.bestSet.spoken,
            WorkoutTestTags.STAT_VOLUME to "Volume this exercise, $volumeValue",
        )
        // The fixture's own numbers, so a calculator change cannot pass unnoticed either.
        assertEquals(best, expected.getValue(WorkoutTestTags.STAT_BEST))
        assertEquals(listOf("Volume · this exercise", "1,350 lb"), expected.getValue(WorkoutTestTags.STAT_VOLUME))
        expected.forEach { (tag, words) ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
            val cell = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
            assertEquals("$tag reads as the floor's", words, cell.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
            assertEquals("$tag speaks as the floor's", listOf(spoken.getValue(tag)), cell.config.getOrNull(SemanticsProperties.ContentDescription))
        }
        assertTrue("the Last cell stays on the floor", compose.onAllNodesWithTag(WorkoutTestTags.STAT_LAST).fetchSemanticsNodes().isEmpty())
    }

    /** What the floor's stats row computes for the same lift and session. */
    private fun floorStats(sessionId: String): ExerciseFloorStats = runBlocking {
        val session = checkNotNull(deps.workoutRepository.getSession(sessionId))
        ExerciseFloorStatsCalculator.of(
            session = session,
            exerciseId = LIFT,
            lastPerformance = null,
            priorHistory = deps.workoutRepository.historyBefore(sessionId, listOf(LIFT)).getValue(LIFT),
            unit = WeightUnit.LBS,
        )
    }

    /**
     * Leg extension in a workout in progress with 135 lb × 10 logged today, after a finished
     * session of [lastTimeLb] × 5 when one is named.
     */
    private fun seed(lastTimeLb: Double?): Pair<String, ExerciseDetailViewModel> {
        val sessionId = runBlocking {
            val seed: suspend (List<TestSetInput>, Boolean) -> String = { sets, finish ->
                seedTestWorkout(
                    deps = deps, exerciseId = LIFT, exerciseName = "Leg Extension", routineName = "Lower B",
                    targetReps = 10, targetWeightKg = WeightConverter.lbsToKg(135.0), loggedSets = sets, finish = finish,
                ).session.id
            }
            if (lastTimeLb != null) seed(listOf(TestSetInput(weightKg = WeightConverter.lbsToKg(lastTimeLb), reps = 5)), true)
            seed(listOf(TestSetInput(weightKg = WeightConverter.lbsToKg(135.0), reps = 10)), false)
        }
        return sessionId to viewModel()
    }

    private fun viewModel() = ExerciseDetailViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = SavedStateHandle(mapOf("exerciseId" to LIFT)),
        container = deps,
    ).also(viewModels::add)

    private fun show(vm: ExerciseDetailViewModel, widthDp: Int, heightDp: Int, fontScale: Float) {
        compose.showFloor(fontScale = fontScale) {
            Box(modifier = Modifier.width(widthDp.dp).height(heightDp.dp).background(Pit)) {
                ExerciseDetailScreen(onBack = {}, onOpenSession = {}, onOpenActivity = {}, viewModel = vm)
            }
        }
        compose.waitUntil(WAIT_MS) { !vm.uiState.value.isLoading }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        // Robolectric never delivers the draw callback captureToImage waits on; draw the
        // window ourselves, as WorkoutFloorRenderTest does.
        val bitmap = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val out = Bitmap.createBitmap(decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(out))
            out
        }
        val out = File("build/screen-renders/w1d").apply { mkdirs() }
        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    private companion object {
        const val LIFT = "leg-extension"
        const val WAIT_MS = 20_000L

        /** How long a Details that will never show the cells is given to show them. */
        const val STATS_WAIT_MS = 5_000L
    }
}
