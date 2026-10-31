package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.repository.RoutineRepository
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
    private lateinit var vm: RoutinesViewModel
    private var planVm: PlanViewModel? = null
    private val opened = mutableListOf<String>()
    private var backs = 0

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val insights = flow {
            emitAll(deps.routineRepository.observeAll().map { TrainingInsights(routines = it) })
        }
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), insights = insights)
    }

    @After fun tearDown() {
        try {
            runBlocking {
                if (::vm.isInitialized) vm.clearAndJoinForTest()
                planVm?.clearAndJoinForTest()
            }
        } finally {
            deps.close()
            Dispatchers.resetMain()
        }
    }

    private fun show(container: AppDependencies = deps) {
        vm = RoutinesViewModel(ApplicationProvider.getApplicationContext(), container)
        compose.setContent {
            PersonalTrainerTheme {
                RoutinesScreen(
                    onBack = { backs++ },
                    onOpenRoutine = { opened += it },
                    onOpenLibrary = {},
                    viewModel = vm,
                )
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
        assertEquals("opens the editor on new, which creates on first save", listOf(NEW_ROUTINE_ID), opened)
        assertTrue("nothing is created until the editor saves", runBlocking { deps.routineRepository.count() } == 0)
        assertTrue("New routine schedules nothing", runBlocking { deps.scheduleRepository.slots() }.isEmpty())
    }

    @Test
    fun newRoutineButtonOpensNewEditor() {
        seed("Upper A")
        show()
        compose.awaitThat("routine shown", vm.uiState::value) { vm.uiState.value.routines.isNotEmpty() }
        compose.settle()
        compose.onNodeWithTag(RoutinesTags.NEW_ROUTINE).performClick()
        assertEquals(listOf(NEW_ROUTINE_ID), opened)
        assertEquals("no extra row", 1, runBlocking { deps.routineRepository.count() })
    }

    @Test
    fun failingRepositoryShowsErrorBanner() {
        val a = seed("Upper A")
        val failing = object : RoutineDao by deps.database.routineDao() {
            override suspend fun deleteRoutine(id: String) = throw IllegalStateException("disk full")
        }
        show(container = object : AppDependencies by deps {
            override val routineRepository: RoutineRepository = RoutineRepository(failing)
        })
        compose.awaitThat("routine shown", vm.uiState::value) { vm.uiState.value.routines.isNotEmpty() }
        compose.settle()
        compose.onNodeWithTag(RoutinesTags.ERROR).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.routineOptions(a)).performClick()
        compose.onNodeWithTag(PlanTags.routineDelete(a)).performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.awaitThat("error surfaced", vm.uiState::value) { vm.uiState.value.error != null }
        compose.settle()
        compose.onNodeWithTag(RoutinesTags.ERROR).assertIsDisplayed()
        compose.onNodeWithText(RoutinesViewModel.DELETE_FAILED).assertIsDisplayed()
        compose.onNodeWithTag(PlanTags.routine(a)).assertIsDisplayed()
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
    fun planShowsNoRoutineRows() {
        val a = seed("Upper A")
        val plan = PlanViewModel(ApplicationProvider.getApplicationContext(), deps).also { planVm = it }
        compose.setContent {
            PersonalTrainerTheme {
                PlanScreen(onOpenRoutine = {}, onOpenRoutines = {}, onOpenDay = { _, _ -> }, viewModel = plan)
            }
        }
        compose.awaitThat("plan loaded", plan.uiState::value) { !plan.uiState.value.isLoading }
        compose.settle()
        compose.onNodeWithTag(PlanTags.ROUTINES).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.routine(a)).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.LIBRARY).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.ROUTINES_ENTRY).assertIsDisplayed()
    }
}
