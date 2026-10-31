package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.ui.plan.PlanScreen
import com.sinura.personaltrainer.ui.plan.PlanTags
import com.sinura.personaltrainer.ui.plan.PlanViewModel
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.settle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

/** v4 blueprint packet V3 (A-V3): routines live on their own screen, not on Plan. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class RoutinesScreenRenderTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var deps: FakeAppDependencies
    private lateinit var vm: PlanViewModel
    private val opened = mutableListOf<String>()
    private val picked = mutableListOf<String>()
    private var backs = 0
    private val pickMode = mutableStateOf(false)
    private val pickInto: (String) -> Unit = { id -> picked += id }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val insights = flow {
            emitAll(deps.routineRepository.observeAll().map { TrainingInsights(routines = it) })
        }
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), insights = insights)
    }

    @After fun tearDown() {
        try {
            runBlocking { if (::vm.isInitialized) vm.clearAndJoinForTest() }
        } finally {
            deps.close()
            Dispatchers.resetMain()
        }
    }

    private fun show(plan: Boolean = false) {
        vm = PlanViewModel(ApplicationProvider.getApplicationContext(), deps)
        compose.setContent {
            PersonalTrainerTheme {
                if (plan) {
                    PlanScreen(onOpenRoutine = {}, onOpenRoutines = {}, onOpenDay = { _, _ -> }, viewModel = vm)
                } else {
                    RoutinesScreen(
                        onBack = { backs++ },
                        onOpenRoutine = { opened += it },
                        onOpenLibrary = {},
                        onPicked = pickInto.takeIf { pickMode.value },
                        viewModel = vm,
                    )
                }
            }
        }
        compose.awaitThat("routines loaded", vm.uiState::value) { !vm.uiState.value.isLoading }
        compose.settle()
    }

    private fun seed(name: String): String = runBlocking { deps.routineRepository.create(name).id }

    @Test
    fun listsRoutines() {
        val a = seed("Upper A")
        val b = seed("Lower B")
        show()
        compose.awaitThat("both routines shown", vm.uiState::value) { vm.uiState.value.routines.size == 2 }
        compose.settle()
        compose.onNodeWithTag(PlanTags.routine(a)).assertIsDisplayed()
        compose.onNodeWithTag(PlanTags.routine(b)).assertIsDisplayed()
        compose.onNodeWithTag(RoutinesTags.NEW_ROUTINE).assertIsDisplayed()
        compose.onNodeWithTag(RoutinesTags.BACK).performClick()
        assertEquals(1, backs)
        compose.onNodeWithTag(PlanTags.routine(a)).performClick()
        assertEquals(listOf(a), opened)
    }

    @Test
    fun emptyShowsMakeOne() {
        show()
        compose.onNodeWithTag(RoutinesTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithText("No routines yet").assertIsDisplayed()
        compose.onNodeWithText("Make one, or start from the library.").assertIsDisplayed()
        compose.onNodeWithText("New routine").performClick()
        compose.awaitThat("new routine opened", { opened.size }) { opened.size == 1 }
        val created = runBlocking { deps.routineRepository.getById(opened.single()) }
        assertEquals("New routine", created?.name)
        assertTrue("New routine schedules nothing", runBlocking { deps.scheduleRepository.slots() }.isEmpty())
    }

    @Test
    fun optionsEditAndDelete() {
        val a = seed("Upper A")
        show()
        compose.awaitThat("routine shown", vm.uiState::value) { vm.uiState.value.routines.isNotEmpty() }
        compose.settle()
        compose.onNodeWithTag(PlanTags.routineOptions(a)).performClick()
        compose.onNodeWithTag(PlanTags.routineEdit(a)).performClick()
        assertEquals(listOf(a), opened)
        compose.onNodeWithTag(PlanTags.routineOptions(a)).performClick()
        compose.onNodeWithTag(PlanTags.routineDelete(a)).performClick()
        compose.onNodeWithText("Delete Upper A?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.awaitThat("routine deleted", vm.uiState::value) { vm.uiState.value.routines.isEmpty() }
        compose.settle()
        compose.onNodeWithTag(RoutinesTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun pickModeStartsAndPops() {
        val a = seed("Upper A")
        pickMode.value = true
        show()
        compose.awaitThat("routine shown", vm.uiState::value) { vm.uiState.value.routines.isNotEmpty() }
        compose.settle()
        compose.onNodeWithTag(RoutinesTags.NEW_ROUTINE).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.routineOptions(a)).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.routine(a)).performClick()
        assertEquals(listOf(a), picked)
        assertTrue("pick never opens the editor", opened.isEmpty())
    }

    @Test
    fun planShowsNoRoutineRows() {
        val a = seed("Upper A")
        show(plan = true)
        compose.onNodeWithTag(PlanTags.ROUTINES).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.routine(a)).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.LIBRARY).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.ROUTINES_ENTRY).assertIsDisplayed()
    }
}
