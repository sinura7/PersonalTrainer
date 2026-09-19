package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWorkoutFixture
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the redesigned floor through the real ViewModel, Room and timer graph on the
 * JVM, and writes the frames to `app/build/floor-renders/` for visual review.
 *
 * No emulator exists in the cloud lane (docs/CLOUD-ENVIRONMENT.md), so this is how the
 * layout is looked at rather than reasoned about. The frames are review artifacts, not
 * pinned goldens: Robolectric's renderer is not the API 29 reference profile. Each test
 * also asserts the floor's load-bearing regions exist, so the lane fails loudly if a
 * state stops rendering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class WorkoutFloorRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        // The ViewModel's scope runs inline on the test thread, as in ActiveWorkoutViewModelTest;
        // joining it under the real main looper from that same thread would never return.
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
    fun rendersTheWorkingFloorAt360By800() {
        val vm = openLegExtension(loggedSets = twoSetsLogged())
        render(name = "working-360x800", vm = vm)
    }

    @Test
    fun rendersTheWarmupFloor() {
        val vm = openLegExtension(loggedSets = emptyList())
        render(name = "warmup-360x800", vm = vm) { vm.setWarmup(true) }
    }

    @Test
    fun rendersTheRestingFloor() {
        val vm = openLegExtension(loggedSets = twoSetsLogged())
        render(name = "resting-360x800", vm = vm, expectRest = true) {
            deps.restTimerController.start(totalSeconds = 120, sessionId = vm.uiState.value.session?.id)
            deps.restTimerController.adjust(deltaSeconds = -28)
            // The first rest of a fresh install shows the battery hint in the companion slot;
            // this frame is about the card underneath it.
            vm.acknowledgeRestBatteryHint()
        }
    }

    @Test
    fun rendersTheLiftCompleteFloor() {
        val vm = openLegExtension(loggedSets = threeSetsLogged())
        render(name = "complete-360x800", vm = vm)
    }

    @Test
    fun rendersTheEditingFloor() {
        val vm = openLegExtension(loggedSets = twoSetsLogged())
        render(name = "editing-360x800", vm = vm) {
            val setId = vm.uiState.value.session?.sets?.firstOrNull()?.id
            if (setId != null) vm.editSet(setId)
        }
    }

    @Test
    fun rendersTheFirstSetFloor() {
        val vm = openLegExtension(loggedSets = emptyList())
        render(name = "first-set-360x800", vm = vm)
    }

    @Test
    fun rendersTheFloorAtLargeSystemText() {
        val vm = openLegExtension(loggedSets = twoSetsLogged())
        render(name = "working-360x800-font20", vm = vm, fontScale = 2f)
    }

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun rendersTheFloorAt412By840() {
        val vm = openLegExtension(loggedSets = twoSetsLogged())
        render(name = "working-412x840", vm = vm, widthDp = 412, heightDp = 840)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun rendersTheFloorInLandscape() {
        val vm = openLegExtension(loggedSets = twoSetsLogged())
        render(name = "working-800x360-land", vm = vm, widthDp = 800, heightDp = 360)
    }

    private fun twoSetsLogged() = listOf(
        TestSetInput(weightKg = WeightConverter.lbsToKg(70.0), reps = 10, rpe = 8),
        TestSetInput(weightKg = WeightConverter.lbsToKg(70.0), reps = 10, rpe = 9),
    )

    private fun threeSetsLogged() = twoSetsLogged() +
        TestSetInput(weightKg = WeightConverter.lbsToKg(70.0), reps = 11, rpe = 9)

    private fun openLegExtension(loggedSets: List<TestSetInput>): ActiveWorkoutViewModel {
        val fixture: TestWorkoutFixture = runBlocking {
            seedTestWorkout(
                deps = deps,
                exerciseId = "leg-extension",
                exerciseName = "Leg Extension",
                routineName = "Lower B",
                targetSets = 3,
                targetReps = 10,
                targetWeightKg = WeightConverter.lbsToKg(70.0),
                restSeconds = 120,
                loggedSets = loggedSets,
            )
        }
        return ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to fixture.session.id)),
            container = deps,
            undoTimeout = { it.toLong() },
        ).also(viewModels::add)
    }

    /**
     * Composes the screen in a fixed frame, waits for the session to land, runs [drive]
     * to put the ViewModel in the state under review, then captures the frame.
     */
    private fun render(
        name: String,
        vm: ActiveWorkoutViewModel,
        widthDp: Int = 360,
        heightDp: Int = 800,
        fontScale: Float = 1f,
        expectRest: Boolean = false,
        drive: () -> Unit = {},
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalWeightUnit provides WeightUnit.LBS,
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                PersonalTrainerTheme {
                    Box(
                        Modifier
                            .width(widthDp.dp)
                            .height(heightDp.dp)
                            .background(Pit),
                    ) {
                        ActiveWorkoutScreen(
                            onExit = {},
                            onFinished = {},
                            viewModel = vm,
                            restNotificationsEnabledOverride = true,
                        )
                    }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) { vm.uiState.value.loadState == SessionLoadState.FOUND }
        compose.waitUntil(timeoutMillis = 20_000) { vm.uiState.value.session?.exercises?.isNotEmpty() == true }
        compose.waitForIdle()
        drive()
        compose.waitForIdle()
        // Draw the window's view tree into a bitmap ourselves: Robolectric never delivers
        // the draw callback that captureToImage waits on.
        val bitmap = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val out = Bitmap.createBitmap(decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(out))
            out
        }
        val out = File("build/floor-renders").apply { mkdirs() }
        FileOutputStream(File(out, "$name.png")).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        // Asserted after the frame is on disk, so a failing state still leaves its picture.
        compose.onNodeWithTag(WorkoutTestTags.PROGRESS_LINE).assertExists()
        compose.onNodeWithTag(WorkoutTestTags.TIMER_ROW).assertExists()
        // The list is lazy: landscape and font 2.0 can start the stats and entry below the fold.
        if (heightDp >= 640) compose.onNodeWithTag(WorkoutTestTags.STATS_ROW).assertExists()
        if (heightDp >= 640 && fontScale < 1.6f) compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertExists()
        if (expectRest) compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertExists()
    }
}
