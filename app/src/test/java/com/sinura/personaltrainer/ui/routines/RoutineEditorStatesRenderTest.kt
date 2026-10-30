package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.ui.components.ScreenSkeletonTags
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint 4.3 (A29): one render check per Plan day (routine editor) state. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class RoutineEditorStatesRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var deps: FakeAppDependencies? = null
    private val viewModels = mutableListOf<RoutineEditorViewModel>()

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        try { runBlocking { viewModels.forEach { it.clearAndJoinForTest() } } }
        finally { deps?.close(); Dispatchers.resetMain() }
    }

    private fun editorFor(routineId: String): RoutineEditorViewModel {
        val container = checkNotNull(deps)
        val vm = RoutineEditorViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            SavedStateHandle(mapOf("routineId" to routineId)), container,
        ).also { viewModels += it }
        val shown = mutableStateOf(vm)
        compose.setContent { PersonalTrainerTheme { RoutineEditorScreen(onBack = {}, viewModel = shown.value) } }
        return vm
    }

    @Test
    fun emptyAndFirstTimeOfferAddYourFirstLiftWithAddExercise() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val routine = runBlocking { checkNotNull(deps).routineRepository.create("Push day") }
        val vm = editorFor(routine.id)
        val loaded = { vm.uiState.value }
        compose.awaitThat("the empty routine is ready", loaded) { !loaded().isLoading && loaded().routine != null }
        compose.onNodeWithText("Add your first lift").assertIsDisplayed()
        compose.onNodeWithTag(RoutineEditorTags.ADD_LIFTS).assertIsDisplayed()
        compose.onNodeWithText("Add exercise").assertIsDisplayed()
    }

    @Test
    fun missingRoutineSaysRoutineMissing() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val vm = editorFor("no-such-routine")
        val loaded = { vm.uiState.value }
        compose.awaitThat("the missing read settles", loaded) { loaded().missing }
        compose.onNodeWithText("Routine missing").assertIsDisplayed()
    }

    @Test
    fun loadingDrawsSkeletonCards() {
        compose.setContent { PersonalTrainerTheme { RoutineEditorLoading() } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(RoutineEditorStateTags.LOADING).assertExists()
    }

    @Test
    fun saveErrorShowsInlineWithTryAgain() {
        var saves = 0
        compose.setContent {
            PersonalTrainerTheme {
                RoutineSaveDock(enabled = true, saving = false, error = "Couldn't save the name.", onSave = { saves++ })
            }
        }
        compose.onNodeWithTag(RoutineEditorTags.SAVE_ERROR).assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithTag(RoutineEditorTags.SAVE).performClick()
        assertEquals(1, saves)
    }

    @Test
    fun cleanDockSaysSaveDay() {
        compose.setContent {
            PersonalTrainerTheme { RoutineSaveDock(enabled = true, saving = false, error = null, onSave = {}) }
        }
        compose.onNodeWithText("Save day").assertIsDisplayed()
    }

    @Test
    fun resumeAndProcessDeathShowTheResumedBanner() {
        compose.setContent { PersonalTrainerTheme { RoutineRecoveryBanner(resumed = true) } }
        compose.onNodeWithText("Resumed unfinished edits").assertIsDisplayed()
    }

    @Test
    fun unsavedBackDialogOffersKeepEditingAndDiscardChanges() {
        var kept = 0
        var discarded = 0
        compose.setContent {
            PersonalTrainerTheme {
                RoutineUnsavedDialog(
                    body = "The name and notes did not save.",
                    keepsFields = false,
                    onDiscard = { discarded++ },
                    onKeepEditing = { kept++ },
                )
            }
        }
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText("Unfinished edits").assertIsDisplayed()
        compose.onNodeWithText("Discard changes").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        assertEquals(1, kept)
        assertEquals(0, discarded)
    }
}
