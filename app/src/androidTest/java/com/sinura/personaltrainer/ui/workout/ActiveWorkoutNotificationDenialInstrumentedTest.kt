package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.RestNotificationCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FND-015: after the one explanation, recovery is a compact row. At 360 dp
 * and font 2.0 the current lift, wells, and Log stay on screen, and Log
 * names the payload it will write.
 */
@RunWith(AndroidJUnit4::class)
class ActiveWorkoutNotificationDenialInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
    private val container = app.container
    private var sessionId: String? = null
    private var routineId: String? = null
    private var exerciseId: String? = null

    @After
    fun tearDown() {
        runBlocking {
            withContext(Dispatchers.IO) {
                container.restTimerController.stop()
                sessionId?.let { id ->
                    container.workoutRepository.getInProgress()?.let { live ->
                        if (live.id == id) container.discardWorkout(id)
                    }
                    runCatching { container.workoutRepository.deleteFinishedSession(id) }
                }
                routineId?.let { runCatching { container.routineRepository.delete(it) } }
                exerciseId?.let { runCatching { container.exerciseRepository.deleteCustom(it) } }
            }
        }
    }

    @Test
    fun compactRecoveryLeavesLiftWellsAndNamedLogAt360Font2() {
        val session = seedLiveSession()
        val viewModel = ActiveWorkoutViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to session)),
            container = container,
        )
        compose.setContent {
            val density = LocalDensity.current
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = 2f),
                ) {
                    Box(Modifier.size(360.dp, 800.dp)) {
                        ActiveWorkoutScreen(
                            onExit = {},
                            onFinished = {},
                            viewModel = viewModel,
                            restNotificationsEnabledOverride = false,
                        )
                    }
                }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.LOG_SET))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WorkoutTestTags.NOTIF_RECOVERY).assertIsDisplayed()
        compose.onNodeWithText(RestNotificationCopy.RECOVERY_TITLE).assertIsDisplayed()
        compose.onNodeWithText(RestNotificationCopy.RECOVERY_ACTION).assertIsDisplayed()
        compose.onNodeWithText("Notifications are blocked", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET)
            .assertTextContains("Log", substring = true)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET)
            .assertTextContains("×", substring = true)
        compose.onNodeWithTag(WorkoutTestTags.CONTENT)
            .performScrollToNode(hasTestTag(WorkoutTestTags.CURRENT_LIFT))
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_LIFT).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.CONTENT)
            .performScrollToNode(hasTestTag(WorkoutTestTags.SET_ENTRY))
        compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsDisplayed()
    }

    private fun seedLiveSession(): String = runBlocking {
        withContext(Dispatchers.IO) {
            container.restTimerController.stop()
            container.workoutRepository.getInProgress()?.let { container.discardWorkout(it.id) }
            container.preferencesRepository.setOnboardingComplete(true)
            container.preferencesRepository.setWeightUnit(WeightUnit.KG)
            val suffix = UUID.randomUUID().toString().take(8)
            val exercise = when (
                val result = container.exerciseRepository.createCustom("Denial squat $suffix", "Quads")
            ) {
                is SaveExerciseResult.Saved -> result.exercise
                is SaveExerciseResult.DuplicateName -> result.existing
                SaveExerciseResult.MissingMuscle -> error("denial fixture muscle rejected")
            }
            exerciseId = exercise.id
            val routine = container.routineRepository.create("Denial lower $suffix")
            routineId = routine.id
            container.routineRepository.addExercise(
                routineId = routine.id,
                exercise = exercise,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 90,
            )
            val planned = checkNotNull(container.routineRepository.getById(routine.id))
            val live = container.workoutRepository.startRoutine(planned)
            sessionId = live.id
            live.id
        }
    }
}
