package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.ComposerCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class ActivityComposerFormRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: ActivityComposerViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun pastWorkoutStartsWithChooseLiftOnlyAndSaveDisabled() = runBlocking {
        val vm = ActivityComposerViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("mode" to "strength")),
            container = deps,
        )
        viewModel = vm
        val exercise = deps.exerciseRepository.createCustom("Past lift", "Back") as SaveExerciseResult.Saved
        vm.uiState.awaitFirst { it.catalog.any { lift -> lift.id == exercise.exercise.id } }
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
                    ActivityComposerScreen(onBack = {}, onSaved = {}, viewModel = vm)
                }
            }
        }
        compose.onNodeWithTag(ComposerTags.CHOOSE_LIFT).assertIsDisplayed()
        compose.onNodeWithText(ComposerCopy.weightFieldLabel(WeightUnit.KG)).assertDoesNotExist()
        compose.onNodeWithTag(ComposerTags.SAVE).assertIsNotEnabled()
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun addedLinesShowDraftHeadingAndEnableSave() = runBlocking {
        val vm = ActivityComposerViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("mode" to "mixed")),
            container = deps,
        )
        viewModel = vm
        val exercise = deps.exerciseRepository.createCustom("Mixed lift", "Legs") as SaveExerciseResult.Saved
        vm.uiState.awaitFirst { it.catalog.isNotEmpty() }
        vm.addStrength(exercise.exercise, 60.0, 8)
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
                    ActivityComposerScreen(onBack = {}, onSaved = {}, viewModel = vm)
                }
            }
        }
        compose.onNodeWithText(ComposerCopy.DRAFT_NOT_SAVED.uppercase()).assertIsDisplayed()
        compose.onNodeWithTag(ComposerTags.SAVE).assertIsDisplayed()
        assertTrue(vm.uiState.value.canSave)
    }
}
