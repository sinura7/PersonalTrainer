package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadCalculator
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.history.HistoryScreen
import com.sinura.personaltrainer.ui.history.HistoryTags
import com.sinura.personaltrainer.ui.history.HistoryViewModel
import com.sinura.personaltrainer.ui.home.HomeScreen
import com.sinura.personaltrainer.ui.home.HomeTags
import com.sinura.personaltrainer.ui.home.HomeViewModel
import com.sinura.personaltrainer.ui.plan.PlanScreen
import com.sinura.personaltrainer.ui.plan.PlanViewModel
import com.sinura.personaltrainer.ui.progress.BodyTags
import com.sinura.personaltrainer.ui.progress.ProgressScreen
import com.sinura.personaltrainer.ui.progress.ProgressViewModel
import com.sinura.personaltrainer.ui.settings.SettingsScreen
import com.sinura.personaltrainer.ui.settings.SettingsTags
import com.sinura.personaltrainer.ui.settings.SettingsViewModel
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.testutil.SteppingTime
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * W3/TS-3: each shipping tab's actual screen and real ViewModel draws loaded content
 * in the permanent JVM gate. Fresh synthetic Room/preferences per test, no owner data.
 * Pixels inside an essential content region must contain ink, not just semantics.
 * This smoke fixture is not AppNav routing, a full adaptive matrix or phone acceptance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class ShippingTabsRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val models = mutableListOf<ViewModel>()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val clock = SteppingTime(nowMs = 1_791_446_400_000L, zoneId = "UTC")
        val emptyHistory = MuscleLoadCalculator.snapshot(
            sessions = emptyList(), window = HeatWindow.CURRENT_WEEK,
            nowMs = clock.nowMillis(), time = clock,
        )
        deps = FakeAppDependencies(
            context = app, time = clock,
            insights = MutableStateFlow(TrainingInsights(snapshot = emptyHistory)),
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After fun tearDown() {
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        models.clear()
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test fun homeDrawsItsLoadedDayBoard() {
        val vm = HomeViewModel(app, deps).also(models::add)
        compose.showFloor {
            Surface(modifier = Modifier.fillMaxSize(), color = Pit) {
                HomeScreen(onResumeWorkout = {}, onOpenPlan = {}, viewModel = vm)
            }
        }
        compose.awaitThat("Home's loaded synthetic day", vm.uiState::value) { !vm.uiState.value.isLoading }
        assertNull(vm.uiState.value.error)
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(PlanDayCopy.EMPTY))
        capture("home", compose.onNodeWithText(PlanDayCopy.EMPTY))
        compose.onNodeWithTag(HomeTags.START).assertExists()
    }

    @Test fun bodyDrawsItsLoadedAnatomy() {
        val vm = ProgressViewModel(app, deps).also(models::add)
        compose.showFloor {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                ProgressScreen(onOpenLibrary = {}, onOpenExercise = {}, onOpenRoutines = {}, viewModel = vm)
            }
        }
        compose.awaitThat("Body's computed empty-history map", vm.uiState::value) {
            !vm.uiState.value.isLoading && vm.uiState.value.snapshot != null
        }
        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.snapshot)
        capture("body", compose.onNodeWithTag(BodyTags.MAP))
    }

    @Test fun planDrawsItsLoadedSelectedDay() {
        val vm = PlanViewModel(app, deps).also(models::add)
        compose.showFloor {
            PlanScreen(onOpenRoutine = {}, onOpenLibrary = {}, onOpenDay = { _, _ -> }, viewModel = vm)
        }
        compose.awaitThat("Plan's loaded empty selected day", vm.uiState::value) { !vm.uiState.value.isLoading }
        assertNull(vm.uiState.value.error)
        capture("plan", compose.onNodeWithText(PlanDayCopy.EMPTY))
    }

    @Test fun historyDrawsItsLoadedEmptyLog() {
        val vm = HistoryViewModel(app, deps).also(models::add)
        compose.showFloor {
            HistoryScreen(onOpenSession = {}, onOpenExercise = {}, onOpenActiveSession = {}, viewModel = vm)
        }
        compose.awaitThat("History's loaded empty log", vm.uiState::value) { !vm.uiState.value.isLoading }
        assertTrue(!vm.uiState.value.unavailable && !vm.uiState.value.stale)
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(HistoryTags.EMPTY))
        capture("history", compose.onNodeWithTag(HistoryTags.EMPTY))
    }

    @Test fun settingsDrawsItsLoadedDisplayPreference() {
        val vm = SettingsViewModel(app, deps).also(models::add)
        // Settings inherits its background from AppNav's Scaffold on the phone.
        compose.showFloor {
            Surface(modifier = Modifier.fillMaxSize(), color = Pit) { SettingsScreen(viewModel = vm) }
        }
        compose.awaitThat("Settings' stored kilograms preference", vm.uiState::value) {
            vm.uiState.value.weightUnit == WeightUnit.KG
        }
        compose.onNodeWithTag(SettingsTags.HOME).assertIsDisplayed()
        capture("settings", compose.onNodeWithTag(SettingsTags.ROW_DISPLAY))
    }

    private fun capture(tab: String, content: SemanticsNodeInteraction) {
        compose.waitForIdle()
        val region = content.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val frame = compose.drawWindow()
        try {
            assertTrue("the $tab content region has positive area", region.width > 0f && region.height > 0f)
            assertNotNull("$tab draws ink inside its loaded content region", frame.inkBox(region, Pit))
            val out = File("build/screen-renders/shipping-tabs/${UUID.randomUUID()}")
            check(out.mkdirs())
            out.resolve("$tab-360x640-font10.png").outputStream().use {
                check(frame.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { frame.recycle() }
    }
}
