package com.sinura.personaltrainer.ui.workout

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.timer.RestTimerService
import com.sinura.personaltrainer.ui.history.SessionDetailTestTags
import com.sinura.personaltrainer.ui.history.SetEditTestTags
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarTestTags
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Critical durable journey: real AppContainer + Room + NavHost + Compose.
 *
 * It deep-links only after seeding, so this test does not confuse Plan/Home
 * setup with the logging loop it is intended to prove.
 */
@RunWith(AndroidJUnit4::class)
class ActiveWorkoutJourneyInstrumentedTest {
    private val launchIntent = Intent(
        ApplicationProvider.getApplicationContext(),
        MainActivity::class.java,
    )
    private lateinit var container: AppContainer
    private lateinit var fixture: JourneyFixture

    private val seedRule = object : ExternalResource() {
        override fun before() {
            // Soft keyboard animations never go idle on this SwiftShader emulator.
            runShell("settings put secure show_ime_with_hard_keyboard 1")
            runShell("settings put global window_animation_scale 0")
            runShell("settings put global transition_animation_scale 0")
            runShell("settings put global animator_duration_scale 0")
            seedBeforeActivityLaunch()
            launchIntent.putExtra(RestTimerService.EXTRA_SESSION_ID, fixture.sessionId)
        }

        override fun after() {
            cleanAfterActivityClose()
        }
    }

    private val scenarioRule = ActivityScenarioRule<MainActivity>(launchIntent)
    private val composeRule = AndroidComposeTestRule(scenarioRule) { rule ->
        lateinit var activity: MainActivity
        rule.scenario.onActivity { activity = it }
        activity
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(seedRule).around(composeRule)

    private val compose: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
        get() = composeRule

    @Test
    fun seedSession_typeWeight_log_rest_finish_reachesSummary() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(fixture.routineName)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.SET_ENTRY))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.LOG_SET) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitCondition("logged working set") {
            runBlocking(Dispatchers.IO) {
                container.workoutRepository.getSession(fixture.sessionId)?.sets?.size == 1
            }
        }
        val live = runBlocking(Dispatchers.IO) {
            checkNotNull(container.workoutRepository.getSession(fixture.sessionId))
        }
        assertNull(live.finishedAt)
        assertEquals(1, live.sets.size)
        with(live.sets.single()) {
            assertTrue(weightKg > 0.0)
            assertEquals(5, reps)
            assertFalse(isWarmup)
        }
        val logged = live.sets.single()
        val setLine = SetCopy.setLine(logged.weightKg, logged.reps, LoadClass.LOADED, WeightUnit.KG)

        awaitCondition("rest running") {
            val timer = container.restTimerStore.current()
            timer.running && timer.sessionId == fixture.sessionId
        }
        val timer = container.restTimerStore.current()
        assertEquals(120, timer.totalSeconds)
        val remaining = timer.remainingSeconds(SystemClock.elapsedRealtime())
        assertTrue("remaining=$remaining", remaining in 1..120)
        // The rest track tweens every second and this emulator never catches
        // up, so Compose never goes idle while the clock runs. Skip through
        // the same controller the Skip button uses, then resume Espresso.
        container.restTimerController.stop()
        awaitCondition("rest skipped") { !container.restTimerStore.current().running }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.MICRO_REC))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).assertIsDisplayed()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.FINISH) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(container.restTimerStore.current().running)

        compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()
        compose.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) {
                container.workoutRepository.getSession(fixture.sessionId)?.finishedAt != null
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("WORKOUT COMPLETE")
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("WORKOUT COMPLETE").assertIsDisplayed()
        compose.onNodeWithText(fixture.routineName).assertIsDisplayed()
        val volumeLabel = WeightConverter.formatVolumeLabel(
            live.work().volumeKg,
            WeightUnit.KG,
        )
        compose.onNodeWithContentDescription("Total volume $volumeLabel").assertIsDisplayed()
        compose.onNodeWithText("Top set $setLine").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()

        val finished = runBlocking(Dispatchers.IO) {
            checkNotNull(container.workoutRepository.getSession(fixture.sessionId))
        }
        assertNotNull(finished.finishedAt)
        assertEquals(1, finished.sets.count { !it.isWarmup })
        assertNull(runBlocking(Dispatchers.IO) { container.workoutRepository.getInProgress() })
        assertFalse(container.restTimerStore.current().running)
        assertTrue(
            runBlocking(Dispatchers.IO) {
                container.workoutRepository.observeHistory().first()
                    .any { it.id == fixture.sessionId }
            },
        )
    }

    @Test
    fun leaveResume_finishFromBar_rotateSummary_andRepairUndo() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(fixture.routineName)
                .fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking(Dispatchers.IO) {
            container.workoutRepository.logSet(
                sessionId = fixture.sessionId,
                exerciseId = fixture.exercise.id,
                weightKg = 100.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
            )
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.FINISH) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("Exit workout").performClick()
        compose.onNodeWithText("Keep and exit").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(LiveSessionBarTestTags.ROOT))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val leftOpen = runBlocking(Dispatchers.IO) {
            checkNotNull(container.workoutRepository.getSession(fixture.sessionId))
        }
        assertNull(leftOpen.finishedAt)
        assertEquals(fixture.sessionId, runBlocking(Dispatchers.IO) {
            container.workoutRepository.getInProgress()?.id
        })

        compose.onNodeWithTag(LiveSessionBarTestTags.ROOT).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.FINISH))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Exit workout").performClick()
        compose.onNodeWithText("Keep and exit").performClick()
        compose.onNodeWithContentDescription("Workout actions").performClick()
        compose.onNodeWithText("Finish workout").performClick()

        compose.waitUntil(15_000) {
            runBlocking(Dispatchers.IO) {
                container.workoutRepository.getSession(fixture.sessionId)?.finishedAt != null
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("WORKOUT COMPLETE")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("WORKOUT COMPLETE").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("WORKOUT COMPLETE")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Total volume 500 kg").assertIsDisplayed()
        assertNull(runBlocking(Dispatchers.IO) { container.workoutRepository.getInProgress() })

        val original = runBlocking(Dispatchers.IO) {
            checkNotNull(container.workoutRepository.getSession(fixture.sessionId)).sets.single()
        }
        compose.onNodeWithText("See full session").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(SessionDetailTestTags.EDIT_SET))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(SessionDetailTestTags.CONTENT)
            .performScrollToNode(hasTestTag(SessionDetailTestTags.EDIT_SET))
        compose.onNodeWithTag(SessionDetailTestTags.EDIT_SET).performClick()
        compose.onNodeWithTag(SetEditTestTags.DELETE).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) {
                container.workoutRepository.getSession(fixture.sessionId)?.sets.isNullOrEmpty()
            }
        }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) {
                container.workoutRepository.getSession(fixture.sessionId)?.sets?.singleOrNull()?.id ==
                    original.id
            }
        }
        val restored = runBlocking(Dispatchers.IO) {
            checkNotNull(container.workoutRepository.getSession(fixture.sessionId)).sets.single()
        }
        assertEquals(original.id, restored.id)
        assertEquals(original.completedAt, restored.completedAt)
        assertEquals(100.0, restored.weightKg, 0.0001)
        compose.onNodeWithText("Set 1").assertIsDisplayed()
    }

    private fun seedBeforeActivityLaunch() {
        val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
        container = app.container
        fixture = runBlocking {
            withContext(Dispatchers.IO) {
                resetStaleJourneyData()
                container.preferencesRepository.setOnboardingComplete(true)
                container.preferencesRepository.setWeightUnit(WeightUnit.KG)

                val suffix = UUID.randomUUID().toString().take(8)
                val exercise = createExercise("Journey squat $suffix")
                val routine = container.routineRepository.create("Journey lower $suffix")
                container.routineRepository.addExercise(
                    routineId = routine.id,
                    exercise = exercise,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = 140.0,
                    restSeconds = 120,
                )
                val planned = checkNotNull(container.routineRepository.getById(routine.id))

                // Suppress the first-ever PR overlay without changing the live
                // session's expected 500 kg summary.
                val prior = container.workoutRepository.startRoutine(planned)
                container.workoutRepository.logSet(
                    sessionId = prior.id,
                    exerciseId = exercise.id,
                    weightKg = 200.0,
                    reps = 5,
                    rpe = null,
                    isWarmup = false,
                )
                container.workoutRepository.finishSession(prior.id, notes = "")

                val live = container.workoutRepository.startRoutine(planned)
                JourneyFixture(
                    sessionId = live.id,
                    priorSessionId = prior.id,
                    routineId = routine.id,
                    routineName = routine.name,
                    exercise = exercise,
                )
            }
        }
    }

    private fun cleanAfterActivityClose() {
        if (!::container.isInitialized) return
        runBlocking {
            withContext(Dispatchers.IO) {
                container.restTimerController.stop()
                container.workoutRepository.getInProgress()?.let { running ->
                    container.discardWorkout(running.id)
                }
                if (::fixture.isInitialized) {
                    listOf(fixture.sessionId, fixture.priorSessionId).forEach { id ->
                        runCatching { container.workoutRepository.deleteFinishedSession(id) }
                    }
                    runCatching { container.routineRepository.delete(fixture.routineId) }
                    runCatching { container.exerciseRepository.deleteCustom(fixture.exercise.id) }
                }
            }
        }
    }

    private suspend fun createExercise(name: String): Exercise =
        when (val result = container.exerciseRepository.createCustom(name, "Quads")) {
            is SaveExerciseResult.Saved -> result.exercise
            is SaveExerciseResult.DuplicateName -> result.existing
            SaveExerciseResult.MissingMuscle -> error("journey fixture muscle rejected")
        }

    private suspend fun resetStaleJourneyData() {
        container.restTimerController.stop()
        container.restTimerStatePersistence.clear()
        container.workoutDraftCache.clearAll()
        container.workoutRepository.getInProgress()?.let { container.discardWorkout(it.id) }

        container.workoutRepository.observeHistory().first()
            .filter { it.routineName?.startsWith(JOURNEY_PREFIX) == true }
            .forEach { runCatching { container.workoutRepository.deleteFinishedSession(it.id) } }

        container.routineRepository.observeAll().first()
            .filter { it.name.startsWith(JOURNEY_PREFIX) }
            .forEach { runCatching { container.routineRepository.delete(it.id) } }

        container.exerciseRepository.observeAll().first()
            .filter { it.isCustom && it.name.startsWith("Journey squat") }
            .forEach { runCatching { container.exerciseRepository.deleteCustom(it.id) } }
    }

    private fun runShell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
            .use { pipe ->
                FileInputStream(pipe.fileDescriptor).use { it.readBytes() }
            }
    }

    private fun awaitCondition(label: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        throw AssertionError("$label not met after ${timeoutMs}ms")
    }

    private data class JourneyFixture(
        val sessionId: String,
        val priorSessionId: String,
        val routineId: String,
        val routineName: String,
        val exercise: Exercise,
    )

    private companion object {
        const val JOURNEY_PREFIX = "Journey lower"
    }
}
