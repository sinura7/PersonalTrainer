package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every block of the floor sits in its own framed section (owner ask of 29 September 2026,
 * packet P1 of docs/owner-eight-plan-2026-09-29.md): stats, the entry, effort, the coach's
 * Next card and the set history each have a `FloorSection` around them, at the default
 * text size and at the largest, on the narrowest phone.
 *
 * The frame is what the owner sees; the tag on it is what this test can reach. A block that
 * loses its frame loses its tag, and the test fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h1600dp-xhdpi")
class FloorSectionEdgesRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
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
    fun everyFloorBlockSitsInItsOwnFrameAtDefaultText() {
        val vm = openLegExtension(deps, viewModels, loggedSets = twoSetsLogged())
        show(vm, fontScale = 1f)
        assertFramed()
    }

    @Test
    fun everyFloorBlockSitsInItsOwnFrameAtTheLargestText() {
        val vm = openLegExtension(deps, viewModels, loggedSets = twoSetsLogged())
        show(vm, fontScale = 2f)
        assertFramed()
    }

    private fun assertFramed() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag(WorkoutTestTags.SET_HISTORY).fetchSemanticsNodes().isNotEmpty()
        }
        listOf(
            WorkoutTestTags.SECTION_STATS,
            WorkoutTestTags.SECTION_ENTRY,
            WorkoutTestTags.SECTION_RPE,
            WorkoutTestTags.SECTION_NEXT_SET,
            WorkoutTestTags.SECTION_SET_HISTORY,
        ).forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        // The block is inside its frame, not beside it.
        compose.onNodeWithTag(WorkoutTestTags.SET_HISTORY)
            .assert(hasAnyAncestor(hasTestTag(WorkoutTestTags.SECTION_SET_HISTORY)))
        compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK)
            .assert(hasAnyAncestor(hasTestTag(WorkoutTestTags.SECTION_RPE)))
    }

    private fun twoSetsLogged() = listOf(
        TestSetInput(weightKg = FLOOR_KG70, reps = 10, rpe = 8),
        TestSetInput(weightKg = FLOOR_KG70, reps = 10, rpe = 9),
    )

    private fun show(vm: ActiveWorkoutViewModel, fontScale: Float) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalWeightUnit provides WeightUnit.LBS,
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                PersonalTrainerTheme {
                    Box(Modifier.width(360.dp).height(1600.dp).background(Pit)) {
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
    }
}
