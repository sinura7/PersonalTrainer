package com.sinura.personaltrainer.ui.workout

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
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
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.timer.RestTimerService
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.history.SessionDetailTestTags
import com.sinura.personaltrainer.ui.history.SetEditTestTags
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarTestTags
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    private val environment = NativeWorkoutFixtureEnvironment(JOURNEY_PREFIX)

    private val seedRule = object : ExternalResource() {
        override fun before() {
            container = ApplicationProvider.getApplicationContext<PersonalTrainerApp>().container
            environment.prepare(container)
            try {
                seedBeforeActivityLaunch()
                launchIntent.putExtra(RestTimerService.EXTRA_SESSION_ID, fixture.sessionId)
            } catch (failure: Throwable) {
                try { cleanAndRestore() } catch (cleanupFailure: Throwable) { failure.addSuppressed(cleanupFailure) }
                throw failure
            }
        }

        override fun after() {
            cleanAndRestore()
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
            compose.onAllNodes(
                hasTestTag(WorkoutTestTags.LOG_SET) and SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    LogCommitCopy.EFFORT_MISSING,
                ),
            )
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsNotEnabled()
        compose.onAllNodes(isDialog()).assertCountEquals(0)

        // Enter the actual result through the same numeric dialogs a lifter uses. The
        // synthetic result differs from both prefilled numbers, and Set only fills the draft.
        compose.onNodeWithTag(WorkoutTestTags.CONTENT)
            .performScrollToNode(hasTestTag(WorkoutTestTags.WEIGHT_STEPPER))
        captureWeightEntryState("before-tap")
        compose.revealFloorControlAboveTempo(WorkoutTestTags.WEIGHT_STEPPER)
        captureWeightEntryState("after-scroll")
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).performClick()
        captureWeightEntryState("after-tap")
        val weightField = compose.onNodeWithTag(NumberEntryTags.FIELD)
        val suggestedWeight = checkNotNull(NumericEntry.parseWeightKg(
            weightField.fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
            WeightUnit.KG,
        ))
        assertTrue("the actual load must differ from the prefill", suggestedWeight != ACTUAL_WEIGHT_KG)
        weightField.performTextReplacement(ACTUAL_WEIGHT_KG.toString())
        compose.onNodeWithText("Set").performClick()

        compose.revealFloorControlAboveTempo(WorkoutTestTags.REPS_STEPPER)
        compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).performClick()
        val repsField = compose.onNodeWithTag(NumberEntryTags.FIELD)
        val suggestedReps = checkNotNull(NumericEntry.parseReps(
            repsField.fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
        ))
        assertTrue("the actual reps must differ from the prefill", suggestedReps != ACTUAL_REPS)
        repsField.performTextReplacement(ACTUAL_REPS.toString())
        compose.onNodeWithText("Set").performClick()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsNotEnabled()
        assertTrue(runBlocking(Dispatchers.IO) {
            checkNotNull(container.workoutRepository.getSession(fixture.sessionId)).sets.isEmpty()
        })

        // A working set logs only with its effort (P2a): the 8 chip, scrolled into view and tapped.
        compose.revealFloorControlAboveTempo(WorkoutTestTags.rpeChoice(8))
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(8)).performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.LOG_SET) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        captureWindow("before-log")
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
            assertEquals(fixture.sessionId, sessionId)
            assertEquals(fixture.exercise.id, exerciseId)
            assertEquals(ACTUAL_WEIGHT_KG, weightKg, 0.0001)
            assertEquals(ACTUAL_REPS, reps)
            assertEquals(8, rpe)
            assertFalse(isWarmup)
        }
        val logged = live.sets.single()
        val setLine = SetCopy.setLine(logged.weightKg, logged.reps, LoadClass.LOADED, WeightUnit.KG)

        awaitCondition("rest running") {
            val timer = container.restTimerStore.current()
            timer.running && timer.sessionId == fixture.sessionId
        }
        val timer = container.restTimerStore.current()
        // The saved four-rep working set uses the heavy-set rest prescription.
        assertEquals(150, timer.totalSeconds)
        val remaining = timer.remainingSeconds(SystemClock.elapsedRealtime())
        assertTrue("remaining=$remaining", remaining in 1..150)
        val autoAdvance = compose.mainClock.autoAdvance
        compose.mainClock.autoAdvance = false
        try {
            // Backend polling alone does not advance Compose's controlled frame clock.
            try {
                compose.waitUntil(5_000) {
                    compose.mainClock.advanceTimeBy(100)
                    runCatching { compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).assertIsDisplayed() }.isSuccess
                }
            } catch (failure: Throwable) {
                captureWindow("rest-visibility-failure")
                throw AssertionError("Running rest did not become visible.\n${environment.describeWindow()}", failure)
            }
            captureWindow("saved-rest-running")
            // This older smoke retains its direct controller seam. The connected AppNav
            // journey separately exercises the real Android Skip action.
            container.restTimerController.stop()
            awaitCondition("rest skipped") { !container.restTimerStore.current().running }
            compose.mainClock.advanceTimeBy(1_000)
        } finally {
            compose.mainClock.autoAdvance = autoAdvance
        }
        // Debug124 pins Tempo above the dock, outside the scrolling content list.
        // Wait for the derived suggestion and its real Apply control to be visible.
        awaitCondition("pinned Tempo suggestion displayed") {
            compose.waitForIdle()
            runCatching {
                compose.onNodeWithTag(WorkoutTestTags.TEMPO_COACH_CARD).assertIsDisplayed()
                compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).assertIsDisplayed()
            }.isSuccess
        }
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).assertIsDisplayed()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.FINISH) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(container.restTimerStore.current().running)

        compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()
        compose.onNodeWithText("End workout?").assertIsDisplayed()
        compose.onNodeWithText("Save as is").performClick()
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
        captureWindow("summary")
        // The lift breakdown is the fourth item of the summary's list, below the fold of a
        // 731 dp screen. Scroll the LIST to it rather than the node: a node's own
        // performScrollTo needs it already composed, and a taller receipt (two record kinds,
        // or stacked tiles at a large font scale) pushes it outside the composed range.
        compose.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(text = "Top set $setLine", substring = true))
        compose.onNodeWithText("Top set $setLine").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()

        val finished = runBlocking(Dispatchers.IO) {
            checkNotNull(container.workoutRepository.getSession(fixture.sessionId))
        }
        assertNotNull(finished.finishedAt)
        assertEquals(1, finished.sets.count { !it.isWarmup })
        assertEquals("finishing retains the exact saved result", logged, finished.sets.single())
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
        compose.onAllNodes(isDialog()).assertCountEquals(0)
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
        compose.onAllNodes(isDialog()).assertCountEquals(0)
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
        // The set list is what Undo puts back, and the three waits above are on the
        // database, not on the screen. Wait for the row to exist, then scroll the list to
        // it: on the 731 dp profile it lands below the fold whenever the detail's header
        // and metrics push it there, which is why this assertion failed on one run of a
        // commit it passed on twice.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Set 1"))
        compose.onNodeWithText("Set 1").assertIsDisplayed()
    }

    private fun seedBeforeActivityLaunch() {
        val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
        container = app.container
        fixture = runBlocking(Dispatchers.IO) {
            withTimeout(60_000) {
                resetStaleJourneyData()
                container.preferencesRepository.setOnboardingComplete(true)
                container.preferencesRepository.setWeightUnit(WeightUnit.KG)
                container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
                container.preferencesRepository.setLaunchPermissionsAsked(true)
                container.preferencesRepository.markRestBatteryHintShown()
                container.preferencesRepository.markRestAlertsAsked()

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

                // Suppress the first-ever PR overlay; each journey checks its own live result.
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
        runBlocking(Dispatchers.IO) {
            withTimeout(30_000) {
                container.restTimerController.stop()
                container.workoutRepository.getInProgress()?.let { running ->
                    container.discardWorkout(running.id)
                }
                if (::fixture.isInitialized) {
                    listOf(fixture.sessionId, fixture.priorSessionId).forEach { id ->
                        runCatchingCancellable { container.workoutRepository.deleteFinishedSession(id) }
                    }
                    runCatchingCancellable { container.routineRepository.delete(fixture.routineId) }
                    runCatchingCancellable { container.exerciseRepository.deleteCustom(fixture.exercise.id) }
                }
            }
        }
    }

    private fun cleanAndRestore() {
        var cleanupFailure: Throwable? = null
        try { cleanAfterActivityClose() } catch (failure: Throwable) { cleanupFailure = failure }
        try { environment.restore() } catch (failure: Throwable) {
            val earlier = cleanupFailure
            if (earlier == null) cleanupFailure = failure else earlier.addSuppressed(failure)
        }
        cleanupFailure?.let { throw it }
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
            .forEach { runCatchingCancellable { container.workoutRepository.deleteFinishedSession(it.id) } }

        container.routineRepository.observeAll().first()
            .filter { it.name.startsWith(JOURNEY_PREFIX) }
            .forEach { runCatchingCancellable { container.routineRepository.delete(it.id) } }

        container.exerciseRepository.observeAll().first()
            .filter { it.isCustom && it.name.startsWith("Journey squat") }
            .forEach { runCatchingCancellable { container.exerciseRepository.deleteCustom(it.id) } }
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

    private fun captureWeightEntryState(state: String) {
        for (tag in listOf(WorkoutTestTags.WEIGHT_STEPPER, WorkoutTestTags.TEMPO_COACH_CARD,
            WorkoutTestTags.CONTENT, WorkoutTestTags.LOG_SET, NumberEntryTags.FIELD)) {
            val bounds = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().map { it.boundsInRoot }
            println("NATIVE_ENTRY_DIAGNOSTIC journey=active state=$state tag=$tag bounds=$bounds")
        }
        captureWindow("weight-$state")
    }

    /** Capture real Android windows without waiting for the continuously animated rest track. */
    private fun captureWindow(state: String) {
        // Let the OS compositor catch up after a UI transition; this does not change app state.
        SystemClock.sleep(750)
        environment.assertNoBlockingPrompts()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = automation.takeScreenshot() ?: automation.executeShellCommand("screencap -p").use { pipe ->
            FileInputStream(pipe.fileDescriptor).use(BitmapFactory::decodeStream)
        }
        checkNotNull(bitmap) { "Both native-window screenshot paths failed for $state" }
        NativeArtifacts.write("active-workout-journey-$state-api${Build.VERSION.SDK_INT}", bitmap)
        bitmap.recycle()
    }

    private companion object {
        const val JOURNEY_PREFIX = "Journey lower"
        const val ACTUAL_WEIGHT_KG = 87.5
        const val ACTUAL_REPS = 4
    }
}
