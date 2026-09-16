package com.sinura.personaltrainer.ui.preview

import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.GoldenPageCatalog
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.GoldenImageAssert
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.ActiveWorkoutScreen
import com.sinura.personaltrainer.ui.workout.ActiveWorkoutViewModel
import com.sinura.personaltrainer.ui.workout.SessionLoadState
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Image-led H1: the nine named 360×800 `active-strength` floor goldens
 * against the 112 dp hero. Record on `temper-tests-api29` with
 * `recordGoldens=true`, then pull `/sdcard/Download/` into
 * `app/src/androidTest/assets/goldens`. One
 * [androidx.compose.ui.test.junit4.ComposeContentTestRule.setContent] per
 * test — the empty Activity rejects a second mount.
 *
 * Without the record flag, a state that is not in [GoldenPageCatalog.committed]
 * is skipped rather than compared against a missing asset.
 */
@RunWith(AndroidJUnit4::class)
class FloorGoldenTest {
    @get:Rule
    val compose = createComposeRule()

    private val container
        get() = app().container
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        assumeTrue(
            "Floor goldens are the API 29 temper-tests-api29 profile; this device is API ${Build.VERSION.SDK_INT}",
            Build.VERSION.SDK_INT == 29,
        )
        runShell("settings put secure show_ime_with_hard_keyboard 1")
        runShell("settings put global window_animation_scale 0")
        runShell("settings put global transition_animation_scale 0")
        runShell("settings put global animator_duration_scale 0")
        runBlocking {
            withContext(Dispatchers.IO) {
                container.preferencesRepository.setOnboardingComplete(true)
                container.preferencesRepository.setWeightUnit(WeightUnit.KG)
                removeLeftovers()
            }
        }
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        viewModels.clear()
        runBlocking {
            withContext(Dispatchers.IO) { removeLeftovers() }
        }
    }

    @Test
    fun recordsWorking() {
        val session = seedStrength(lifts = 2, targetSets = 3)
        val vm = viewModel(session)
        mountFloor(vm)
        awaitHero(vm)
        record("working")
    }

    @Test
    fun recordsWarmup() {
        val session = seedStrength(lifts = 2, targetSets = 3)
        val vm = viewModel(session)
        mountFloor(vm)
        awaitHero(vm)
        vm.applyWarmupRamp(40.0)
        awaitCondition("warmup draft") { vm.uiState.value.draft.isWarmup }
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag(WorkoutTestTags.WARMUP_CHIP)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        record("warmup")
    }

    @Test
    fun recordsRest() {
        val session = seedStrength(lifts = 2, targetSets = 3)
        val vm = viewModel(session)
        // Rest pulse is an infinite Compose transition; reduced motion
        // is what lets Espresso idle on this capture.
        mountFloor(vm, reduceMotion = true)
        awaitHero(vm)
        vm.startSelectedRest()
        awaitCondition("rest running") { vm.restTimerState.value.running }
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag(WorkoutTestTags.REST_BAR)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        record("rest")
    }

    @Test
    fun recordsHold() {
        val session = seedHang()
        val vm = viewModel(session)
        mountFloor(vm)
        awaitHero(vm)
        vm.startHoldSet()
        awaitCondition("hold running") { vm.holdTimer.value.running }
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag(WorkoutTestTags.HOLD_CLOCK)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        record("hold", waitIdle = false)
    }

    @Test
    fun recordsSuccess() {
        val session = seedStrength(lifts = 2, targetSets = 3)
        val vm = viewModel(session)
        mountFloor(vm, reduceMotion = true)
        awaitHero(vm)
        vm.logSet()
        awaitCondition("log receipt") { vm.logReceipt.value != null }
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag(WorkoutTestTags.LOG_RECEIPT)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        if (vm.restTimerState.value.running) vm.skipRest()
        record("success", waitIdle = false)
    }

    @Test
    fun recordsError() {
        val session = seedStrength(lifts = 1, targetSets = 3)
        val vm = viewModel(session)
        mountFloor(vm)
        awaitHero(vm)
        vm.skipForNow()
        awaitCondition("skip-nowhere error") { vm.uiState.value.error != null }
        record("error")
    }

    @Test
    fun recordsCompletion() {
        val session = seedStrength(lifts = 2, targetSets = 1)
        val vm = viewModel(session)
        mountFloor(vm, reduceMotion = true)
        awaitHero(vm)
        vm.logSet()
        awaitCondition("lift complete") { vm.pendingAdvance.value != null }
        if (vm.restTimerState.value.running) vm.skipRest()
        awaitCondition("rest stopped after lift complete") { !vm.restTimerState.value.running }
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag(WorkoutTestTags.NEXT)
                .fetchSemanticsNodes()
                .isNotEmpty() ||
                compose.onAllNodesWithTag(WorkoutTestTags.DOCK_FINISH)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
        record("completion")
    }

    @Test
    fun recordsFont20() {
        val session = seedStrength(lifts = 2, targetSets = 3)
        val vm = viewModel(session)
        mountFloor(vm, fontScale = 2f)
        awaitHero(vm)
        record("font20")
    }

    @Test
    fun recordsReducedMotion() {
        val session = seedStrength(lifts = 2, targetSets = 3)
        val vm = viewModel(session)
        mountFloor(vm, reduceMotion = true)
        awaitHero(vm)
        record("reduced-motion")
    }

    private fun mountFloor(
        viewModel: ActiveWorkoutViewModel,
        fontScale: Float = 1f,
        reduceMotion: Boolean = false,
    ) {
        GoldenCapture.mount(compose, reduceMotion = reduceMotion) {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalWeightUnit provides WeightUnit.KG,
            ) {
                ActiveWorkoutScreen(
                    onExit = {},
                    onFinished = {},
                    viewModel = viewModel,
                    restNotificationsEnabledOverride = true,
                )
            }
        }
    }

    private fun record(state: String, waitIdle: Boolean = true) {
        if (waitIdle) compose.waitForIdle()
        val image = compose.onNodeWithTag(GoldenCapture.DefaultTag).captureToImage()
        val name = GoldenPageCatalog.floorAssetName(state)
        val recording = InstrumentationRegistry.getArguments()
            .getString("recordGoldens").toBoolean()
        if (!recording) {
            assumeTrue(
                "$name is not committed; recapture with recordGoldens=true",
                GoldenPageCatalog.isCommitted(name),
            )
        }
        GoldenImageAssert.assertMatches(name, image)
    }

    private fun awaitHero(viewModel: ActiveWorkoutViewModel) {
        awaitCondition("session found") {
            viewModel.uiState.value.loadState == SessionLoadState.FOUND &&
                viewModel.uiState.value.selectedExerciseId != null
        }
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag(WorkoutTestTags.CURRENT_LIFT)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.waitForIdle()
        // Keyed stills decode off the first frame.
        SystemClock.sleep(750)
        compose.waitForIdle()
    }

    private fun viewModel(sessionId: String): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = app(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = container,
        ).also(viewModels::add)

    private fun seedStrength(lifts: Int, targetSets: Int): String = runBlocking {
        withContext(Dispatchers.IO) {
            val squat = catalogLift(SQUAT_ID)
            val routine = container.routineRepository.create(STRENGTH_ROUTINE)
            container.routineRepository.addExercise(
                routineId = routine.id,
                exercise = squat,
                targetSets = targetSets,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 90,
            )
            if (lifts > 1) {
                val row = catalogLift(ROW_ID)
                container.routineRepository.addExercise(
                    routineId = routine.id,
                    exercise = row,
                    targetSets = targetSets,
                    targetReps = 5,
                    targetWeightKg = 80.0,
                    restSeconds = 90,
                )
            }
            val planned = checkNotNull(container.routineRepository.getById(routine.id))
            container.workoutRepository.startRoutine(planned).id
        }
    }

    private fun seedHang(): String = runBlocking {
        withContext(Dispatchers.IO) {
            val hang = catalogLift(HANG_ID)
            val routine = container.routineRepository.create(HANG_ROUTINE)
            container.routineRepository.addExercise(
                routineId = routine.id,
                exercise = hang,
                targetSets = 2,
                targetReps = 1,
                targetWeightKg = null,
                restSeconds = 75,
                targetSeconds = 30,
            )
            val planned = checkNotNull(container.routineRepository.getById(routine.id))
            container.workoutRepository.startRoutine(planned).id
        }
    }

    private suspend fun catalogLift(id: String): Exercise {
        repeat(200) {
            container.exerciseRepository.getById(id)?.let { return it }
            delay(50)
        }
        error("catalog $id not seeded")
    }

    private suspend fun removeLeftovers() {
        container.restTimerController.stop()
        container.workoutRepository.getInProgress()?.let { container.discardWorkout(it.id) }
        container.workoutRepository.observeHistory().first()
            .filter { it.routineName?.startsWith(PREFIX) == true }
            .forEach { runCatching { container.workoutRepository.deleteFinishedSession(it.id) } }
        container.routineRepository.observeAll().first()
            .filter { it.name.startsWith(PREFIX) }
            .forEach { runCatching { container.routineRepository.delete(it.id) } }
    }

    private fun awaitCondition(label: String, timeoutMs: Long = 60_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        throw AssertionError("$label not met after ${timeoutMs}ms")
    }

    private fun runShell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
            .use { pipe -> FileInputStream(pipe.fileDescriptor).use { it.readBytes() } }
    }

    private fun app(): PersonalTrainerApp = ApplicationProvider.getApplicationContext()

    private companion object {
        const val PREFIX = "H1 floor"
        const val STRENGTH_ROUTINE = "H1 floor strength"
        const val HANG_ROUTINE = "H1 floor hang"
        const val SQUAT_ID = "ex-barbell-back-squat"
        const val ROW_ID = "ex-barbell-row"
        const val HANG_ID = "ex-dead-hang"
    }
}
