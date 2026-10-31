package com.sinura.personaltrainer.ui.home

import com.sinura.personaltrainer.ui.start.HomeWithStartChooser
import com.sinura.personaltrainer.ui.start.StartTags
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.WorkoutPreviewTags
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.settle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

/**
 * A21 / D6: the planned start opens the pre-workout preview; Begin is the only thing that
 * creates a session, through Home's single start path, and creates exactly one even when two
 * taps land from the same frame. Back creates nothing. Real Home, ViewModel and isolated Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class PreviewBeginRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val today = todayEpochDay()
    private val nowMs = LocalDate.ofEpochDay(today).atTime(12, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val opened = mutableListOf<String>()
    private val insights = MutableStateFlow(TrainingInsights())
    private lateinit var deps: FakeAppDependencies
    private lateinit var vm: HomeViewModel
    private lateinit var occurrenceId: String

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher,
            insights = insights, time = FrozenTime(nowMs),
        )
        runBlocking {
            deps.preferencesRepository.setOnboardingComplete(true)
            deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
            val routine = deps.routineRepository.create(ROUTINE)
            deps.routineRepository.addExercise(
                routine.id, insertTestExercise(deps, "preview-bench", "Bench Press"), 3, 8, 60.0, 120,
            )
            deps.routineRepository.addExercise(
                routine.id, insertTestExercise(deps, "preview-row", "Seated Cable Row"), 3, 10, null, 90,
            )
            deps.scheduleRepository.pin(routine.id, null, Weekday.fromEpochDay(today))
            deps.plannerRepository.importSlotsIfNeeded(nowMs)
            deps.plannerRepository.ensureWeek(CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY))
            occurrenceId = deps.plannerRepository.occurrencesBetween(today, today).single().id
            insights.value = TrainingInsights(routines = deps.routineRepository.observeAll().first())
        }
        vm = HomeViewModel(ApplicationProvider.getApplicationContext(), deps)
    }

    @After fun tearDown() {
        try {
            runBlocking { vm.clearAndJoinForTest() }
        } finally {
            deps.restTimerController.stop()
            dispatcher.scheduler.advanceUntilIdle()
            deps.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun beginTwiceFromOneFrameCreatesExactlyOneSession() {
        openPreview()
        val begin = compose.onNodeWithTag(WorkoutPreviewTags.BEGIN)
            .assertIsDisplayed().assertIsEnabled()
            .assertTextContains("Begin with Bench Press", substring = true)
        assertEquals(0, sessions())
        val action = begin.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnUiThread { action(); action() }
        await("Begin navigates once") {
            opened.size == 1 && !vm.startState.value.pending
        }
        compose.settle()
        assertEquals(1, sessions())
        assertEquals(1, opened.size)
        val saved = runBlocking { checkNotNull(deps.workoutRepository.getSession(opened.single())) }
        assertEquals(listOf("Bench Press", "Seated Cable Row"), saved.exercises.map { it.exercise.name })
    }

    @Test
    fun backLeavesNothingBehind() {
        openPreview()
        compose.onNodeWithTag(WorkoutPreviewTags.BACK).performClick()
        compose.settle()
        compose.onNodeWithTag(WorkoutPreviewTags.ROOT).assertDoesNotExist()
        assertEquals(0, sessions())
        assertTrue(opened.isEmpty())
    }

    @Test
    fun firstTimeLiftsSayFirstTime() {
        openPreview()
        await("both lifts read First time") {
            compose.onAllNodesWithText("First time").fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun todayPrimaryIsStartWorkoutWithDayAndLiftCountAndOpensThePreview() {
        openPreview(viaAgendaRow = false)
        compose.onNodeWithTag(WorkoutPreviewTags.BEGIN).assertTextContains("Begin with Bench Press", substring = true)
        assertEquals(0, sessions())
    }

    private fun await(what: String, condition: () -> Boolean) {
        val now: () -> Any? = { vm.uiState.value.readState to vm.startState.value }
        compose.awaitThat(what = what, now = now, condition = condition)
    }

    private fun sessions(): Int = runBlocking { deps.database.workoutDao().getAllSessions().size }

    private fun openPreview(viaAgendaRow: Boolean = true) {
        compose.setContent {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG, LocalTodayEpochDay provides today) {
                PersonalTrainerTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Pit) {
                        HomeWithStartChooser(vm = vm, deps = deps, onResumeWorkout = { opened += it })
                    }
                }
            }
        }
        await("Home is current") {
            vm.uiState.value.readState == HomeReadState.CURRENT && !vm.uiState.value.isLoading
        }
        compose.settle()
        if (viaAgendaRow) {
            compose.onNodeWithTag(HomeTags.BOARD).performScrollToNode(hasTestTag(HomeTags.agendaRow(occurrenceId)))
            compose.onNodeWithTag(HomeTags.agendaRow(occurrenceId)).performClick()
        } else {
            compose.onNodeWithTag(HomeTags.TODAY_START).assertIsDisplayed()
                .assertTextContains("Start workout", substring = true)
                .assertTextContains("$ROUTINE · 2 lifts", substring = true)
                .performClick()
            // V2: Today's Start opens the shared chooser; its planned Today row is the start.
            compose.settle()
            compose.onNodeWithTag(StartTags.START_TODAY).assertIsDisplayed().performClick()
        }
        compose.settle()
        compose.onNodeWithTag(WorkoutPreviewTags.ROOT).assertExists()
    }

    private companion object {
        const val ROUTINE = "Upper A"
    }
}
