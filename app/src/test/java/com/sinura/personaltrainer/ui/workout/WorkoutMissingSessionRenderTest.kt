package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.runBlocking
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ADR-032: real missing/read-failed workout and rest screens, actions, Room and shared timer. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class WorkoutMissingSessionRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val failReads = MutableStateFlow(false)
    private lateinit var deps: FakeAppDependencies
    private val models = mutableListOf<ViewModel>()
    private val RecoveryScene = mutableStateOf<RecoveryScene?>(null)
    private val runId = UUID.randomUUID().toString()
    private lateinit var profile: String
    private var systemFont = 1f

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher,
            workoutDaoDecorator = { real ->
                object : WorkoutDao by real {
                    override fun observeSession(id: String): Flow<SessionWithDetails?> =
                        real.observeSession(id).combine(failReads) { row, fail ->
                            check(!fail) { "Injected session read failure" }
                            row
                        }
                }
            },
        )
        runBlocking {
            deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
            deps.preferencesRepository.markRestBatteryHintShown()
        }
    }

    @After
    fun tearDown() {
        try {
            runBlocking { models.forEach { it.clearAndJoinForTest() } }
            models.clear()
        } finally {
            if (::deps.isInitialized) {
                deps.restTimerController.stop()
                dispatcher.scheduler.advanceUntilIdle()
                deps.close()
            }
            Dispatchers.resetMain()
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun smallFont10() = verifyProfile(name = "360x640-font10", expectedFont = 1f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun smallFont16() = verifyProfile(name = "360x640-font16", expectedFont = 1.6f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun smallFont20() = verifyProfile(name = "360x640-font20", expectedFont = 2f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun largerFont10() = verifyProfile(name = "412x840-font10", expectedFont = 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun largerFont16() = verifyProfile(name = "412x840-font16", expectedFont = 1.6f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun largerFont20() = verifyProfile(name = "412x840-font20", expectedFont = 2f)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscapeFont10() = verifyProfile(name = "640x360-land-font10", expectedFont = 1f)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscapeFont16() = verifyProfile(name = "640x360-land-font16", expectedFont = 1.6f)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscapeFont20() = verifyProfile(name = "640x360-land-font20", expectedFont = 2f)

    private fun verifyProfile(name: String, expectedFont: Float) {
        profile = name
        systemFont = RuntimeEnvironment.getApplication().resources.configuration.fontScale
        assertEquals(expectedFont, systemFont, .001f)
        val fixture = runBlocking { seedTestWorkout(deps = deps) }
        val original = fixture.session
        deps.restTimerController.start(90, original.id)
        val timer = deps.restTimerStore.current()
        assertTrue(timer.running)
        assertEquals(original.id, timer.sessionId)
        assertTrue(timer.timerId.isNotBlank())
        // One root composition per profile; each real screen is disposed before the next.
        // Density comes from Android's OS configuration, without a LocalDensity override.
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
                    Box(Modifier.fillMaxSize()) {
                        val shown = RecoveryScene.value
                        if (shown != null) {
                            when (val vm = shown.model) {
                                is ActiveWorkoutViewModel -> ActiveWorkoutScreen(
                                    onExit = shown.onBack, onFinished = {}, viewModel = vm,
                                    restNotificationsEnabledOverride = true,
                                )
                                is RestTimerViewModel -> RestTimerScreen(onClose = shown.onBack, viewModel = vm)
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
        missingWorkout(original = original, timer = timer)
        missingRest(original = original, timer = timer)
        failedWorkout(original = original, timer = timer)
        failedRest(original = original, timer = timer)
    }

    private fun missingWorkout(original: WorkoutSession, timer: RestTimerSnapshot) {
        var exits = 0
        val vm = active(id = "missing")
        mount(model = vm, onBack = { exits += 1 })
        compose.awaitThat("missing workout", vm.uiState::value) { vm.uiState.value.loadState == SessionLoadState.MISSING }
        assertNull(vm.uiState.value.session)
        assertMissingCopy()
        checkScene(stage = "missing-workout", title = MISSING_TITLE, body = MISSING_BODY, actionLabel = "Back to home")
        action(label = "Back to home").performClick()
        assertEquals(1, exits)
        assertUnchanged(original = original, timer = timer)
        unmount(model = vm)
    }

    private fun missingRest(original: WorkoutSession, timer: RestTimerSnapshot) {
        var exits = 0
        val vm = rest(id = "missing")
        mount(model = vm, onBack = { exits += 1 })
        compose.awaitThat("missing rest workout", vm.uiState::value) { vm.uiState.value.loadState == SessionLoadState.MISSING }
        assertMissingCopy()
        checkScene(stage = "missing-rest", title = MISSING_TITLE, body = MISSING_BODY, actionLabel = "Back")
        action(label = "Back").performClick()
        assertEquals(1, exits)
        assertUnchanged(original = original, timer = timer)
        unmount(model = vm)
    }

    private fun failedWorkout(original: WorkoutSession, timer: RestTimerSnapshot) {
        failReads.value = true
        var exits = 0
        val vm = active(id = original.id)
        mount(model = vm, onBack = { exits += 1 })
        compose.awaitThat("failed workout read", vm.uiState::value) { vm.uiState.value.loadState == SessionLoadState.FAILED }
        compose.onNodeWithText(MISSING_TITLE).assertDoesNotExist()
        checkScene(stage = "failed-workout", title = FAILED_TITLE, body = WORKOUT_FAILED_BODY, actionLabel = "Retry")
        assertUnchanged(original = original, timer = timer)
        failReads.value = false
        action(label = "Retry").performClick()
        compose.awaitThat("Retry loaded the actual workout", vm.uiState::value) { vm.uiState.value.canLog }
        assertEquals(original.id, vm.uiState.value.session!!.id)
        compose.onNodeWithText(FAILED_TITLE).assertDoesNotExist()
        assertEquals(0, exits)
        assertUnchanged(original = original, timer = timer)
        unmount(model = vm)
    }

    private fun failedRest(original: WorkoutSession, timer: RestTimerSnapshot) {
        failReads.value = true
        var exits = 0
        val vm = rest(id = original.id)
        mount(model = vm, onBack = { exits += 1 })
        compose.awaitThat("failed rest read", vm.uiState::value) { vm.uiState.value.loadState == SessionLoadState.FAILED }
        compose.onNodeWithText(MISSING_TITLE).assertDoesNotExist()
        checkScene(stage = "failed-rest", title = FAILED_TITLE, body = REST_FAILED_BODY, actionLabel = "Retry")
        assertUnchanged(original = original, timer = timer)
        failReads.value = false
        action(label = "Retry").performClick()
        compose.awaitThat("Retry loaded rest for its session", vm.uiState::value) { vm.uiState.value.loadState == SessionLoadState.FOUND }
        compose.onNodeWithTag(RestFloorTags.CLOCK).assertIsDisplayed()
        assertEquals(0, exits)
        assertUnchanged(original = original, timer = timer)
        unmount(model = vm)
    }

    private fun assertMissingCopy() {
        compose.onNodeWithText(MISSING_TITLE).assertIsDisplayed()
        compose.onNodeWithText(MISSING_BODY).assertIsDisplayed()
        compose.onNodeWithText("Nothing was lost from your history.").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    private fun checkScene(stage: String, title: String, body: String, actionLabel: String) {
        captureScene(stage = stage, title = title, body = body, actionLabel = actionLabel).recycle()
        listOf(
            Triple("title", title, TextPrimary),
            Triple("body", body, TextSecondary),
            Triple("action", actionLabel, Volt),
        ).forEach { (part, words, color) ->
            // A short window may need a real scroll. The entire required text and
            // action still have to fit once reached; a clipped remnant never passes.
            compose.onNodeWithText(text = words, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            val frame = captureScene(stage = "$stage-$part", title = title, body = body, actionLabel = actionLabel)
            try {
                fullText(words = words, frame = frame, color = color)
                if (part == "action") action(label = actionLabel)
            } finally {
                frame.recycle()
            }
        }
    }

    private fun captureScene(stage: String, title: String, body: String, actionLabel: String): Bitmap {
        compose.waitForIdle()
        val frame = compose.drawWindow()
        try {
            val directory = File("build/screen-renders/workout-missing-truth/$runId/$profile").also {
                check(it.isDirectory || it.mkdirs()) { "Cannot create missing-state evidence directory $it" }
            }
            directory.resolve("$stage.png").outputStream().use {
                check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot save $profile/$stage" }
            }
            val geometry = listOf(title, body, actionLabel).map { words ->
                val target = compose.onNodeWithText(words).fetchSemanticsNode()
                "text=$words bounds=${target.boundsInWindow} layout=${target.size} " +
                    "density=${target.layoutInfo.density.density} font=${target.layoutInfo.density.fontScale}"
            }
            directory.resolve("$stage-geometry.txt").writeText(
                "window=${frame.width}x${frame.height}\n" + geometry.joinToString("\n", postfix = "\n"),
            )
            geometry.forEach { println("UX23_MISSING_RENDER run=$runId profile=$profile stage=$stage $it") }
            return frame
        } catch (failure: Throwable) {
            frame.recycle()
            throw failure
        }
    }

    private fun fullText(words: String, frame: Bitmap, color: Color) {
        val node = compose.onNodeWithText(text = words, useUnmergedTree = true).assertIsDisplayed()
        val target = node.fetchSemanticsNode()
        val layout = node.textLayout()
        repeat(layout.lineCount) { line ->
            assertFalse("$words has no ellipsis", layout.isLineEllipsized(line))
            assertTrue("$words fits its text width", layout.getLineRight(line) - layout.getLineLeft(line) <= target.size.width + 1f)
        }
        assertEquals("$words keeps every character", words.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        assertTrue("$words fits its text height", layout.getLineBottom(layout.lineCount - 1) <= target.size.height + 1f)
        val bounds = target.boundsInWindow
        assertTrue("$words is fully unclipped: $bounds layout=${target.size}",
            bounds.width >= target.size.width - 1f && bounds.height >= target.size.height - 1f)
        assertTrue("$words fits the whole window", bounds.left >= -1f && bounds.top >= -1f &&
            bounds.right <= frame.width + 1f && bounds.bottom <= frame.height + 1f)
        assertEquals("$words uses the OS font", systemFont, target.layoutInfo.density.fontScale, .001f)
        assertTrue("$words has actual text ink", frame.count(bounds, color) >= 20)
    }

    private fun action(label: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled()
        val target = node.fetchSemanticsNode()
        val bounds = target.boundsInWindow
        val density = target.layoutInfo.density.density
        val window = compose.runOnIdle { compose.activity.window.decorView.let { it.width to it.height } }
        assertTrue("$label has its full action bounds: $bounds layout=${target.size}",
            bounds.width >= target.size.width - 1f && bounds.height >= target.size.height - 1f)
        assertTrue("$label fits its window", bounds.left >= -1f && bounds.top >= -1f &&
            bounds.right <= window.first + 1f && bounds.bottom <= window.second + 1f)
        assertTrue("$label keeps a full 48 dp action", bounds.width / density + .01f >= 48f &&
            bounds.height / density + .01f >= 48f && target.size.height / density + .01f >= 48f)
        assertEquals("$label uses the OS font", systemFont, target.layoutInfo.density.fontScale, .001f)
        return node
    }

    private fun assertUnchanged(original: WorkoutSession, timer: RestTimerSnapshot) {
        assertEquals(original, runBlocking { deps.workoutRepository.getSession(original.id) })
        // Includes timer generation, session identity, total and monotonic deadline.
        assertEquals(timer, deps.restTimerStore.current())
    }

    private fun mount(model: ViewModel, onBack: () -> Unit) {
        compose.runOnIdle { RecoveryScene.value = RecoveryScene(model = model, onBack = onBack) }
        compose.waitForIdle()
    }

    private fun unmount(model: ViewModel) {
        compose.runOnIdle { RecoveryScene.value = null }
        compose.waitForIdle()
        runBlocking { model.clearAndJoinForTest() }
        models.remove(model)
    }

    private fun active(id: String) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to id)), container = deps,
    ).also(models::add)

    private fun rest(id: String) = RestTimerViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to id)), container = deps,
    ).also(models::add)

    private data class RecoveryScene(val model: ViewModel, val onBack: () -> Unit)

    private companion object {
        const val MISSING_TITLE = "Workout not live"
        const val MISSING_BODY = "This workout is not running. If you finished it, look in History."
        const val FAILED_TITLE = "Workout unavailable"
        const val WORKOUT_FAILED_BODY = "Your workout could not be read. Your draft is kept. Retry, or close this screen and return later."
        const val REST_FAILED_BODY = "The workout could not be read. The timer has not been stopped. Retry, or close this screen."
    }
}
