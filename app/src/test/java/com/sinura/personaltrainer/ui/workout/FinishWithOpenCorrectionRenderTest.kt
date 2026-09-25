package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.EndWorkoutTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import org.robolectric.annotation.GraphicsMode

/**
 * Audit UI-2: the header's Finish stays enabled while a logged set is open for correction, and
 * ending the workout then drops the change without a word (the set keeps its saved values). The
 * dialog now says so first and offers the way back to the change; with no correction open it
 * says nothing about one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class FinishWithOpenCorrectionRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler()))
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
    fun finishWhileASetIsBeingCorrectedSaysTheChangeIsNotSavedAndLeadsBackToIt() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1))
        compose.showWorkoutScreen(vm)
        val session = checkNotNull(vm.uiState.value.session)
        val setId = session.sets.single().id
        vm.editSet(setId)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) {
            vm.uiState.value.editingSetId == setId && !vm.uiState.value.entryLocked
        }
        vm.setWeight(FLOOR_KG70 + 5.0)
        compose.waitForIdle()

        compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()
        compose.onNodeWithTag(EndWorkoutTags.EDIT_OPEN).assertIsDisplayed()
        compose.onNodeWithTag(EndWorkoutTags.BACK_TO_EDIT).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(EndWorkoutTags.SAVE).assertDoesNotExist()
        assertEquals("the change is still open", setId, vm.uiState.value.editingSetId)
        assertNull(
            "going back must not end the workout",
            runBlocking { deps.workoutRepository.getSession(session.id) }?.finishedAt,
        )
    }

    @Test
    fun finishWithNoCorrectionOpenSaysNothingAboutOne() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1))
        compose.showWorkoutScreen(vm)

        compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()

        compose.onNodeWithTag(EndWorkoutTags.SAVE).assertIsDisplayed()
        compose.onNodeWithTag(EndWorkoutTags.EDIT_OPEN).assertDoesNotExist()
        compose.onNodeWithTag(EndWorkoutTags.BACK_TO_EDIT).assertDoesNotExist()
    }
}
