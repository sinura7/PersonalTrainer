package com.sinura.personaltrainer.ui.workout

import android.content.Intent
import android.graphics.Bitmap
import android.accessibilityservice.AccessibilityService
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarTestTags
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Stage/resume run in different app processes, around the runner's external force-stop. */
@RunWith(AndroidJUnit4::class)
class WorkoutPrivateProcessInstrumentedTest {
    private lateinit var container: AppContainer
    private lateinit var marker: File
    private lateinit var sessionId: String
    private lateinit var routineId: String
    private var kept = false
    private val phase = InstrumentationRegistry.getArguments().getString("workoutRecoveryPhase") ?: "connected"
    private val environment = NativeWorkoutFixtureEnvironment(PREFIX)
    private val seed = object : ExternalResource() {
        override fun before() {
            check(phase in setOf("connected", "stage", "resume", "stage-pending", "resume-pending"))
            val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
            container = app.container
            environment.prepare(container)
            marker = File(app.noBackupFilesDir, "synthetic-workout-private-owner.txt")
            try { read {
                container.preferencesRepository.setOnboardingComplete(true)
                container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
                container.preferencesRepository.setLaunchPermissionsAsked(true)
                container.preferencesRepository.markRestAlertsAsked()
                container.preferencesRepository.markRestBatteryHintShown()
                container.preferencesRepository.setWeightUnit(if (phase.startsWith("resume")) WeightUnit.LBS else WeightUnit.KG)
                if (phase.startsWith("resume")) {
                    val ids = marker.readLines()
                    check(ids.size == 2)
                    sessionId = ids[0]; routineId = ids[1]
                    val live = checkNotNull(container.workoutRepository.getInProgress())
                    check(live.id == sessionId && live.routineId == routineId && live.routineName == PREFIX)
                } else {
                    check(!marker.exists() && container.workoutRepository.getInProgress() == null)
                    val exercise = container.exerciseRepository.observeAll().first { rows -> rows.any { it.id == EXERCISE } }.single { it.id == EXERCISE }
                    val routine = container.routineRepository.create(PREFIX)
                    routineId = routine.id
                    container.routineRepository.addExercise(routine.id, exercise, 3, 5, 100.0, 90)
                    sessionId = container.workoutRepository.startRoutine(checkNotNull(container.routineRepository.getById(routine.id))).id
                    container.workoutRepository.updateSessionNotes(sessionId, "Synthetic notes retained")
                }
            } } catch (failure: Throwable) { try { cleanup() } catch (second: Throwable) { failure.addSuppressed(second) }; throw failure }
        }
        override fun after() = cleanup()
    }
    private val scenario = ActivityScenarioRule<MainActivity>(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
    private val compose = AndroidComposeTestRule(scenario) { rule ->
        lateinit var activity: MainActivity
        rule.scenario.onActivity { activity = it }
        activity
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)

    @Test fun privateEntryAndFrozenSaveReturnToTheSameWorkout() {
        awaitTag(LiveSessionBarTestTags.ROOT)
        tap(LiveSessionBarTestTags.ROOT)
        awaitTag(WorkoutTestTags.CONTENT)
        if (phase == "resume-pending") {
            compose.waitUntil(30000) { compose.onAllNodes(hasTestTag(WorkoutTestTags.LOG_SET) and hasText("Retry save")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsEnabled().performClick()
            compose.waitUntil(30000) { read { container.workoutRepository.getSession(sessionId)!!.sets.size == 1 } }
            val saved = read { container.workoutRepository.getSession(sessionId)!!.sets.single() }
            assertEquals("synthetic-workout-frozen-set", saved.id)
            assertEquals(47.5, saved.weightKg, 0.0); assertEquals(11, saved.reps); assertEquals(8, saved.rpe)
            assertEquals(23, saved.durationSeconds)
            capture("resume-pending-accepted")
            return
        }
        if (phase.startsWith("resume")) {
            compose.onNodeWithTag(WorkoutTestTags.HOLD_CLOCK).assertIsDisplayed()
            val elapsedClock = hasAnyAncestor(hasTestTag(WorkoutTestTags.HOLD_CLOCK)) and hasContentDescription("elapsed", substring = true)
            compose.waitUntil(30000) { compose.onAllNodes(elapsedClock).fetchSemanticsNodes().isNotEmpty() }
            val clock = compose.onNode(elapsedClock).assertIsDisplayed().fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].joinToString()
            assertTrue(clock, clock.endsWith(" elapsed"))
            compose.revealFloorControlAboveTempo(WorkoutTestTags.WEIGHT_STEPPER)
            tap(WorkoutTestTags.WEIGHT_STEPPER)
            awaitTag(NumberEntryTags.FIELD)
            assertEquals("82.", compose.onNodeWithTag(NumberEntryTags.FIELD).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            compose.onNodeWithText("kg").assertIsDisplayed()
            capture("resume-partial-original-unit")
            compose.onNodeWithTag(NumberEntryTags.CONFIRM).assertIsNotEnabled()
            compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("82.0")
            compose.onNodeWithTag(NumberEntryTags.CONFIRM).assertIsEnabled()
            tap(NumberEntryTags.CONFIRM)
            compose.waitUntil(30000) { compose.onAllNodes(hasTestTag(NumberEntryTags.FIELD)).fetchSemanticsNodes().isEmpty() }
            compose.revealFloorControlAboveTempo(WorkoutTestTags.WEIGHT_STEPPER)
            tap(WorkoutTestTags.WEIGHT_STEPPER)
            awaitTag(NumberEntryTags.FIELD)
            // Existing lb presentation rounds to half-pound increments; 82 kg shows 181.
            assertEquals("181", compose.onNodeWithTag(NumberEntryTags.FIELD).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            compose.onNodeWithText("Cancel").performClick()
            compose.waitUntil(30000) { compose.onAllNodes(hasTestTag(NumberEntryTags.FIELD)).fetchSemanticsNodes().isEmpty() }
            assertTrue(read { container.workoutRepository.getSession(sessionId)!!.sets.isEmpty() })
            assertEquals("Synthetic notes retained", read { container.workoutRepository.getSession(sessionId)!!.notes })
            capture("resume-clock-and-entry")
            compose.onNodeWithContentDescription("Close").performClick()
            awaitTag(LiveSessionBarTestTags.ROOT)
            val entry = read { (container.workoutDraftCache.persistence!!.open(sessionId) as com.sinura.personaltrainer.workout.WorkoutDraftRestore.ReadyEntry).entry!! }
            assertEquals(82.0, entry.lifts.getValue(EXERCISE).weightKg, 0.0)
            assertTrue(entry.numbers.isEmpty())
            return
        }
        awaitTag(WorkoutTestTags.START_SET_CLOCK)
        compose.onNodeWithTag(WorkoutTestTags.START_SET_CLOCK).assertIsDisplayed().assertIsEnabled().performClick()
        compose.revealFloorControlAboveTempo(WorkoutTestTags.WEIGHT_STEPPER)
        tap(WorkoutTestTags.WEIGHT_STEPPER)
        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("82.")
        // Android Back first hides the IME, then closes without applying the unfinished field.
        repeat(3) {
            if (compose.onAllNodes(hasTestTag(NumberEntryTags.FIELD)).fetchSemanticsNodes().isNotEmpty()) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                compose.waitForIdle()
            }
        }
        compose.waitUntil(30000) { compose.onAllNodes(hasTestTag(NumberEntryTags.FIELD)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Close").performClick()
        awaitTag(LiveSessionBarTestTags.ROOT)
        assertTrue(read { container.workoutRepository.getSession(sessionId)!!.sets.isEmpty() })
        read {
            val restored = container.workoutDraftCache.persistence!!.open(sessionId) as com.sinura.personaltrainer.workout.WorkoutDraftRestore.ReadyEntry
            assertEquals("82.", restored.entry!!.numbers.single().text)
            assertTrue(restored.entry.clocks!!.stopwatches.getValue(EXERCISE).running)
        }
        if (phase == "stage-pending") read {
            val port = checkNotNull(container.workoutDraftCache.persistence)
            check(port.open(sessionId) is com.sinura.personaltrainer.workout.WorkoutDraftRestore.ReadyEntry)
            val command = WorkoutSetSave(sessionId, EXERCISE, "synthetic-workout-frozen-set", container.workoutRepository.getSession(sessionId)!!.startedAt + 1234,
                WorkoutSetValues(47.5, 11, 8, false, 23))
            try { port.writeSet(command, { throw IOException("Synthetic pre-commit interruption") }, { error("No committed row") }) }
            catch (_: IOException) { }
        }
        marker.writeText("$sessionId\n$routineId\n")
        capture("$phase-kept")
        if (phase.startsWith("stage")) { kept = true; return }
        tap(LiveSessionBarTestTags.ROOT)
        compose.revealFloorControlAboveTempo(WorkoutTestTags.WEIGHT_STEPPER)
        tap(WorkoutTestTags.WEIGHT_STEPPER)
        assertEquals("82.", compose.onNodeWithTag(NumberEntryTags.FIELD).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }
    private fun tap(tag: String) = compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
    private fun awaitTag(tag: String) = compose.waitUntil(30000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(30000) { block() } }
    private fun capture(name: String) {
        val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
        val file = File(app.getExternalFilesDir(null), "ux-context/workout-private/$name.png")
        check(file.parentFile.isDirectory || file.parentFile.mkdirs())
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } } finally { bitmap.recycle() }
    }
    private fun cleanup() {
        try { if (::container.isInitialized && !kept && ::sessionId.isInitialized) read {
            val live = container.workoutRepository.getSession(sessionId)
            check(live == null || live.routineName == PREFIX)
            if (live != null) container.discardWorkout(sessionId)
            if (::routineId.isInitialized) container.routineRepository.delete(routineId)
            if (::marker.isInitialized && marker.exists()) check(marker.delete())
        } } finally { environment.restore() }
    }
    private companion object { const val PREFIX = "Workout private recovery"; const val EXERCISE = "ex-barbell-back-squat" }
}
