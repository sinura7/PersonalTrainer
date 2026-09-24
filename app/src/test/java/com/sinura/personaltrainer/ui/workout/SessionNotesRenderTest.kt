package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The session's notes, tapped through the real screen and ViewModel. They are not in the set
 * loop: the lift's ⋮ options offer Session notes, which opens the session's notes as they stand,
 * and what is typed there becomes the session's notes. End workout carries the same notes,
 * saying on its closed toggle that some are saved, and what is typed there becomes the session's
 * notes too.
 *
 * These were lines of ActiveWorkoutScreen.kt, WorkoutOverflowMenu.kt and EndWorkoutDialog.kt
 * read as text (`onNotes = { notesOpen = true }`, `notes = state.notes`, `modifier =
 * Modifier.testTag(WorkoutTestTags.SESSION_NOTES)`, `onNotesChange = viewModel::setNotes`,
 * `CurrentLiftCopy.SESSION_NOTES` and `onNotes()` in the menu, `NotesBlock(` in End workout).
 *
 * A notes field that appears takes focus, and its blinking cursor keeps an auto-advancing clock
 * busy for good, so the clock is held from the tap that shows the field until the field is
 * closed again (FloorTestKit.holdingTheClock).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class SessionNotesRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler()))
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking {
            deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
            deps.preferencesRepository.markRestBatteryHintShown()
        }
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
    fun sessionNotesFromTheOptionsShowTheSessionsNotesAndWriteThem() {
        val vm = openWithNotes()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS).performClick()
        compose.waitForIdle()
        compose.holdingTheClock {
            compose.onNodeWithText(CurrentLiftCopy.SESSION_NOTES).assertIsDisplayed().performClick()
            compose.settle()
            compose.onNode(isDialog()).assert(hasAnyDescendantText(CurrentLiftCopy.SESSION_NOTES))
            // The session's notes as they stand, ready to edit.
            compose.onNode(inNotes(hasSetTextAction())).assert(hasText(NOTES))
            compose.onNode(inNotes(hasSetTextAction())).performTextReplacement(EDITED)
            compose.settle()
            compose.onNodeWithText(DONE).performClick()
            compose.settle()
        }
        compose.onNodeWithTag(WorkoutTestTags.SESSION_NOTES).assertDoesNotExist()
        compose.awaitThat(what = "the typed notes became the session's", now = vm.uiState::value) { vm.uiState.value.notes == EDITED }
    }

    @Test
    fun endWorkoutCarriesTheSessionNotesAndWritesThem() {
        val vm = openWithNotes()
        compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()
        compose.onNodeWithText(EndWorkoutCopy.TITLE).assertIsDisplayed()
        // Closed, the toggle says notes are already written; open, they are the session's.
        compose.holdingTheClock {
            compose.onNodeWithText(SAVED_TOGGLE).assertIsDisplayed().performClick()
            compose.settle()
            compose.onNode(hasSetTextAction() and hasText(NOTES)).performTextReplacement(EDITED)
            compose.settle()
            compose.onNodeWithText(HIDE).performClick()
            compose.settle()
        }
        compose.awaitThat(what = "End workout's notes became the session's", now = vm.uiState::value) { vm.uiState.value.notes == EDITED }
        assertTrue("writing notes is not finishing", vm.uiState.value.session?.finishedAt == null)
        compose.onNodeWithText(EndWorkoutCopy.TITLE).assertIsDisplayed()
    }

    /** A leg extension with one set saved and the session's notes already written. */
    private fun openWithNotes(): ActiveWorkoutViewModel {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1))
        compose.showWorkoutScreen(vm)
        vm.setNotes(NOTES)
        compose.awaitThat(what = "the session's notes are written", now = vm.uiState::value) { vm.uiState.value.notes == NOTES }
        compose.waitForIdle()
        return vm
    }

    private fun inNotes(matcher: SemanticsMatcher) =
        matcher and hasAnyAncestor(hasTestTag(WorkoutTestTags.SESSION_NOTES))

    private fun hasAnyDescendantText(text: String) = hasAnyDescendant(hasText(text))

    private companion object {
        const val NOTES = "Seat on 4"
        const val EDITED = "Seat on 5, knee fine"
        const val DONE = "Done"
        const val HIDE = "Hide notes"
        const val SAVED_TOGGLE = "Session notes · saved"
    }
}
