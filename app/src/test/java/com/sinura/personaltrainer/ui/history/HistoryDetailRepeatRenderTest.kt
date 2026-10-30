package com.sinura.personaltrainer.ui.history

import androidx.compose.ui.test.assertContentDescriptionEquals
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.WorkoutPreviewTags
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.settle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Blueprint 4.15 (A25, A41): warm-ups carry a W mark, the effort hint sits above the lifts,
 * and Repeat this workout opens the preview first; only Begin starts the copy (deferral from R4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class HistoryDetailRepeatRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var vm: SessionDetailViewModel? = null
    private lateinit var session: WorkoutSession

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        session = runBlocking {
            seedTestWorkout(
                deps = deps,
                loggedSets = listOf(
                    TestSetInput(weightKg = 60.0, reps = 5, isWarmup = true),
                    TestSetInput(weightKg = 100.0, reps = 5, rpe = 8),
                ),
                finish = true,
            ).session
        }
    }

    @After fun tearDown() {
        try {
            runBlocking { vm?.clearAndJoinForTest() }
        } finally {
            deps.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun warmupIsMarkedWAndTheEffortHintShows() {
        mount()
        val warmup = session.sets.single { it.isWarmup }
        compose.onNodeWithTag(FilledLiftCardTags.warmupMark(warmup.id), useUnmergedTree = true).assertContentDescriptionEquals("Warm-up")
        compose.onNodeWithTag(FilledLiftCardTags.warmupMark(session.sets.single { !it.isWarmup }.id), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(SessionDetailTestTags.EFFORT_HINT)
            .assertTextContains("Right side number is effort. Tap a set to edit.")
    }

    @Test
    fun repeatOpensThePreviewAndBackStartsNothing() {
        val opened = mutableListOf<String>()
        mount(opened)
        compose.onNodeWithTag(SessionDetailTestTags.REPEAT).assertIsDisplayed()
            .assertTextContains("Repeat this workout").performClick()
        compose.settle()
        compose.onNodeWithTag(WorkoutPreviewTags.ROOT).assertExists()
        compose.onNodeWithTag(WorkoutPreviewTags.REORDER).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutPreviewTags.BEGIN).assertTextContains("Begin with Test squat", substring = true)
        assertNull(runBlocking { deps.workoutRepository.getInProgress() })
        compose.onNodeWithTag(WorkoutPreviewTags.BACK).performClick()
        compose.settle()
        compose.onNodeWithTag(WorkoutPreviewTags.ROOT).assertDoesNotExist()
        assertNull(runBlocking { deps.workoutRepository.getInProgress() })
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun beginFromTheRepeatPreviewStartsOneCopy() {
        val opened = mutableListOf<String>()
        mount(opened)
        compose.onNodeWithTag(SessionDetailTestTags.REPEAT).performClick()
        compose.settle()
        compose.onNodeWithTag(WorkoutPreviewTags.BEGIN).performClick()
        val seen: () -> Any? = { opened.toList() }
        compose.awaitThat(what = "the copy opens", now = seen) { opened.size == 1 }
        val live = checkNotNull(runBlocking { deps.workoutRepository.getInProgress() })
        assertEquals(opened.single(), live.id)
        val copy = checkNotNull(runBlocking { deps.workoutRepository.getSession(live.id) })
        assertEquals(listOf("Test squat"), copy.exercises.map { it.exercise.name })
    }

    private fun mount(opened: MutableList<String> = mutableListOf()) {
        val model = SessionDetailViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to session.id)),
            container = deps,
        ).also { vm = it }
        compose.setContent {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
                PersonalTrainerTheme {
                    SessionDetailScreen(onBack = {}, onOpenExercise = {}, onOpenActiveSession = { opened += it }, viewModel = model)
                }
            }
        }
        val state: () -> Any? = { model.uiState.value }
        compose.awaitThat(what = "detail loads", now = state) { !model.uiState.value.isLoading }
        compose.settle()
    }
}
