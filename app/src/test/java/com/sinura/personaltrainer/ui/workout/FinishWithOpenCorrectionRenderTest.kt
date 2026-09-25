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
import com.sinura.personaltrainer.domain.WeightConverter
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
import org.junit.Assert.assertFalse
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
        val (vm, session) = openACorrection()

        compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()
        compose.onNodeWithTag(EndWorkoutTags.EDIT_OPEN).assertIsDisplayed()
        compose.onNodeWithTag(EndWorkoutTags.BACK_TO_EDIT).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(EndWorkoutTags.SAVE).assertDoesNotExist()
        val back = vm.uiState.value
        assertFalse("going back must not start a finish", back.mutating || back.finished)
        assertEquals("the change is still open", session.sets.single().id, back.editingSetId)
        assertEquals("the change itself is kept", CORRECTED, back.draft.weightKg, 1e-6)
        assertEquals(WorkoutPrimaryKind.SAVE_CHANGES, vm.primaryAction.value.kind)
        assertNull(
            "going back must not end the workout",
            runBlocking { deps.workoutRepository.getSession(session.id) }?.finishedAt,
        )
    }

    /** What the warning says Save as is does: the workout ends, the set as it was saved. */
    @Test
    fun saveAsIsEndsTheWorkoutWithTheSetAsItWasSaved() {
        val (vm, session) = openACorrection()

        compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()
        compose.onNodeWithTag(EndWorkoutTags.SAVE).performClick()

        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.finished }
        val saved = checkNotNull(runBlocking { deps.workoutRepository.getSession(session.id) })
        assertTrue(saved.isFinished)
        assertEquals(FLOOR_KG70, saved.sets.single().weightKg, 1e-6)
        assertNull("the open change is gone with the workout", deps.workoutDraftCache.editingOriginal(session.id))
    }

    /** A logged set open for correction, its weight changed and the change landed in the entry. */
    private fun openACorrection(): Pair<ActiveWorkoutViewModel, com.sinura.personaltrainer.domain.WorkoutSession> {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1))
        compose.showWorkoutScreen(vm)
        val session = checkNotNull(vm.uiState.value.session)
        val setId = session.sets.single().id
        vm.editSet(setId)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) {
            vm.uiState.value.editingSetId == setId && !vm.uiState.value.entryLocked
        }
        vm.setWeight(CORRECTED)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) {
            kotlin.math.abs(vm.uiState.value.draft.weightKg - CORRECTED) < 1e-6
        }
        compose.waitForIdle()
        return vm to session
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

    private companion object {
        /** 75 lb, one clean step from the logged 70. */
        val CORRECTED: Double = WeightConverter.lbsToKg(75.0)
    }
}
