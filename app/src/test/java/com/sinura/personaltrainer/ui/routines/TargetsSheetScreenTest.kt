package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.workout.awaitThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Blueprint 4.5 (A31), screen level: the real Set targets sheet (the modal, not just its
 * content) wired to the real editor ViewModel and Room, as RoutineEditorScreen wires it.
 * Save targets is the only write; dismissing writes nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class TargetsSheetScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private var vm: RoutineEditorViewModel? = null
    private val open = mutableStateOf(true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() {
        try { runBlocking { vm?.clearAndJoinForTest() } } finally { deps.close(); Dispatchers.resetMain() }
    }

    private fun mount(): String {
        val routineId = runBlocking {
            val routine = deps.routineRepository.create("Push day")
            val bench = insertTestExercise(deps, "bench", "Bench press", muscleGroup = "Chest")
            deps.routineRepository.addExercise(routine.id, bench, 3, 8, null, 120)
            routine.id
        }
        val model = RoutineEditorViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            SavedStateHandle(mapOf("routineId" to routineId)), deps,
        ).also { vm = it }
        return routineId
    }

    private fun show(model: RoutineEditorViewModel) {
        compose.setContent {
            PersonalTrainerTheme {
                val save by model.targetsSheetSave.collectAsState()
                val row = model.uiState.collectAsState().value.routine?.exercises?.single()
                if (open.value && row != null) {
                    TargetsSheet(
                        liftName = row.exercise.name,
                        initial = TargetsValues(row.targetSets, row.targetReps, row.restSeconds),
                        hold = false,
                        saving = save.saving,
                        error = save.error,
                        onSave = { values ->
                            model.saveTargetsFromSheet(
                                itemId = row.id, targetSets = values.sets, targetReps = values.reps,
                                targetWeightKg = row.targetWeightKg, restSeconds = values.restSeconds,
                                targetSeconds = row.targetSeconds, targetSecondsMax = row.targetSecondsMax,
                            )
                        },
                        onDismiss = { open.value = false },
                    )
                }
            }
        }
        compose.awaitThat("the routine is loaded", model.uiState::value) {
            model.uiState.value.routine?.exercises?.size == 1
        }
    }

    @Test fun savingTargetsWritesTheRowThroughTheEditor() {
        val routineId = mount()
        val model = vm!!
        show(model)
        compose.onNodeWithTag(TargetsSheetTags.CONTENT).assertIsDisplayed()
        compose.onNodeWithTag(TargetsSheetTags.LIFT).assertTextEquals("Bench press")
        compose.onNodeWithContentDescription("Sets up").performClick()
        compose.onNodeWithContentDescription("Reps down").performClick()
        compose.onNodeWithTag(TargetsSheetTags.SAVE).performScrollTo().performClick()
        compose.awaitThat("the sheet's save lands", model.targetsSheetSave::value) {
            model.targetsSheetSave.value.savedItemId != null
        }
        val stored = runBlocking { deps.routineRepository.getById(routineId) }!!.exercises.single()
        assertEquals(4, stored.targetSets)
        assertEquals(7, stored.targetReps)
        assertEquals(120, stored.restSeconds)
    }

    /**
     * v4 V1b (A-V1-12): dismissal is a real swipe down on the sheet content, not the test
     * flipping its own flag. Under Robolectric the swipe reaches the ModalBottomSheet's
     * onDismissRequest (back press does not), so no fallback to the editor's dismissal is used.
     */
    @Test fun dismissingTheSheetWritesNothing() {
        val routineId = mount()
        val model = vm!!
        val before = runBlocking { deps.database.routineDao().getAllRoutineExercises() }
        show(model)
        compose.onNodeWithContentDescription("Sets up").performClick()
        compose.onNodeWithTag(TargetsSheetTags.value(TargetsSheetTags.SETS)).assertTextEquals("4")
        compose.onNodeWithTag(TargetsSheetTags.CONTENT).performTouchInput { swipeDown(durationMillis = 300) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse("a swipe down dismisses the sheet through onDismiss", open.value)
        compose.onNodeWithTag(TargetsSheetTags.CONTENT).assertDoesNotExist()
        assertEquals(before, runBlocking { deps.database.routineDao().getAllRoutineExercises() })
        assertFalse(model.targetsSheetSave.value.saving)
        assertEquals(3, runBlocking { deps.routineRepository.getById(routineId) }!!.exercises.single().targetSets)
    }
}
