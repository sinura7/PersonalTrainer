package com.sinura.personaltrainer.ui.plan

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.components.ConfirmActionTags
import com.sinura.personaltrainer.ui.routines.RoutineEditorTags
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Real MainActivity/NavHost, actual system font 2, Room and native text/touch actions. */
@RunWith(AndroidJUnit4::class)
class PlanRoutineActionsInstrumentedTest {
    private lateinit var container: AppContainer
    private lateinit var routine: Routine
    private lateinit var other: Routine
    private var savedId: String? = null
    private var originalFont: String? = null
    private val environment = NativeWorkoutFixtureEnvironment(PREFIX)
    private val seedRule = object : ExternalResource() {
        override fun before() {
            container = ApplicationProvider.getApplicationContext<PersonalTrainerApp>().container
            environment.prepare(container)
            try {
                originalFont = shell("settings get system font_scale").trim().also {
                    check(it == "null" || it.toFloatOrNull() != null)
                }
                shell("settings put system font_scale 2.0")
                runBlocking(Dispatchers.IO) { withTimeout(30_000) { seed() } }
            } catch (failure: Throwable) {
                try { cleanup() } catch (secondary: Throwable) { failure.addSuppressed(secondary) }
                throw failure
            }
        }
        override fun after() { cleanup() }
    }
    private val scenarioRule = ActivityScenarioRule<MainActivity>(
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
    )
    private val compose = AndroidComposeTestRule(scenarioRule) { rule ->
        lateinit var activity: MainActivity
        rule.scenario.onActivity { activity = it }
        activity
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seedRule).around(compose)

    @Test
    fun optionsEditRenamesOnlyChosenTemplateAndKeepsCapturedWorkout() {
        openRoutines()
        val historyBefore = history()
        val otherBefore = read { container.routineRepository.getById(other.id) }
        options(routine.id)
        capture("plan-routine-options-edit-font20")
        tap(PlanTags.routineEdit(routine.id))
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag(RoutineEditorTags.SAVE).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(RoutineEditorTags.SCOPE).assertIsDisplayed()
        compose.onNodeWithText(com.sinura.personaltrainer.domain.RoutineSaveCopy.SCOPE).assertIsDisplayed()
        capture("plan-routine-editor-scope-font20")
        val renamed = "${routine.name} revised"
        compose.onNode(hasSetTextAction() and hasText(routine.name)).performTextReplacement(renamed)
        tap(RoutineEditorTags.SAVE)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag(PlanTags.CONTENT).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(renamed, read { container.routineRepository.getById(routine.id) }?.name)
        assertEquals(otherBefore, read { container.routineRepository.getById(other.id) })
        assertEquals(historyBefore, history())
        assertNull(read { container.workoutRepository.getInProgress() })
        capture("plan-routine-edit-return-font20")
    }

    @Test
    fun optionsDeleteCancelAndConfirmKeepHistoryAndOtherPlannedBlock() {
        openRoutines()
        val before = inventory()
        options(routine.id)
        tap(PlanTags.routineDelete(routine.id))
        compose.onNodeWithText("Delete ${routine.name}?").assertIsDisplayed()
        capture("plan-routine-delete-confirm-font20")
        compose.onNodeWithText("Cancel").performTouchInput { click(center) }
        assertEquals(before, inventory())
        val expectedHistory = read {
            container.workoutRepository.observeHistory().first().map {
                if (it.routineId == routine.id) it.copy(routineId = null) else it
            }
        }
        val otherBefore = read { container.routineRepository.getById(other.id) }
        val otherPins = read { container.scheduleRepository.slots().filter { it.routineId == other.id } }
        options(routine.id)
        tap(PlanTags.routineDelete(routine.id))
        tap(ConfirmActionTags.CONFIRM)
        compose.waitUntil(15_000) { read { container.routineRepository.getById(routine.id) } == null }
        assertEquals(expectedHistory, history())
        assertEquals(otherBefore, read { container.routineRepository.getById(other.id) })
        assertEquals(otherPins, read { container.scheduleRepository.slots().filter { it.routineId == other.id } })
        assertTrue(read { container.scheduleRepository.slots() }.none { it.routineId == routine.id })
        assertNull(read { container.workoutRepository.getInProgress() })
        capture("plan-routine-delete-return-font20")
    }

    @Test
    fun weeklyRemoveExplainsScopeAndKeepsRoutineAndCapturedWorkout() {
        openRoutines()
        compose.onNodeWithTag(PlanTags.CONTENT).performScrollToNode(hasTestTag(PlanTags.ADD_SESSION))
        tap(PlanTags.ADD_SESSION)
        val weekday = Weekday.fromEpochDay(java.time.LocalDate.now().toEpochDay())
        compose.onNodeWithText(com.sinura.personaltrainer.domain.PlanDayCopy.addScope(weekday)).assertIsDisplayed()
        capture("plan-weekly-add-scope-font20")
        compose.onNodeWithTag(PickerHeaderTags.CANCEL).performScrollTo()
        tap(PickerHeaderTags.CANCEL)
        val before = inventory()
        val rule = read { container.plannerRepository.rules().single { it.routineId == routine.id } }
        val removeTag = PlanDayTags.remove(rule.id)
        compose.onNodeWithTag(removeTag).performScrollTo()
        tap(removeTag)
        compose.onNodeWithText(com.sinura.personaltrainer.domain.PlanDayCopy.removeBody(weekday, true)).assertIsDisplayed()
        capture("plan-weekly-remove-scope-font20")
        compose.onNodeWithText("Cancel").performTouchInput { click(center) }
        assertEquals(before, inventory())
        val historyBefore = history()
        val routinesBefore = read { container.routineRepository.observeAll().first() }
        val otherPins = read { container.scheduleRepository.slots().filter { it.routineId == other.id } }
        compose.onNodeWithTag(removeTag).performScrollTo()
        tap(removeTag)
        tap(ConfirmActionTags.CONFIRM)
        compose.waitUntil(15_000) {
            read { container.scheduleRepository.slots().none { it.routineId == routine.id } }
        }
        assertEquals(historyBefore, history())
        assertEquals(routinesBefore, read { container.routineRepository.observeAll().first() })
        assertEquals(otherPins, read { container.scheduleRepository.slots().filter { it.routineId == other.id } })
        assertTrue(read { container.plannerRepository.rules() }.none { it.id == rule.id && it.enabled })
        assertTrue(read { container.plannerRepository.observeOccurrences().first() }.none {
            it.ruleId == rule.id && it.status == com.sinura.personaltrainer.domain.OccurrenceStatus.PLANNED
        })
        capture("plan-weekly-remove-return-font20")
    }

    private fun openRoutines() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("navigation-routines").fetchSemanticsNodes().isNotEmpty()
        }
        tap("navigation-routines")
        compose.onNodeWithTag(PlanTags.CONTENT).performScrollToNode(hasTestTag(PlanTags.ROUTINES))
        tap(PlanTags.ROUTINES)
        compose.waitForIdle()
    }

    private fun options(id: String) {
        compose.onNodeWithTag(PlanTags.CONTENT).performScrollToNode(hasTestTag(PlanTags.routineOptions(id)))
        val button = compose.onNodeWithTag(PlanTags.routineOptions(id)).performScrollTo().assertIsDisplayed()
        val node = button.fetchSemanticsNode()
        assertEquals(2f, node.layoutInfo.density.fontScale, .001f)
        assertEquals(listOf("Options for ${routine.name}"), node.config[SemanticsProperties.ContentDescription])
        assertTrue(node.boundsInWindow.width >= 48f * node.layoutInfo.density.density - 1f)
        assertTrue(node.boundsInWindow.height >= 48f * node.layoutInfo.density.density - 1f)
        button.performTouchInput { click(center) }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed().performTouchInput { click(center) }
        compose.waitForIdle()
    }

    private suspend fun seed() {
        container.preferencesRepository.setOnboardingComplete(true)
        container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
        container.preferencesRepository.setLaunchPermissionsAsked(true)
        container.preferencesRepository.markRestBatteryHintShown()
        container.preferencesRepository.markRestAlertsAsked()
        val exercise = container.exerciseRepository.observeAll().first { rows ->
            rows.any { it.id == "ex-barbell-back-squat" }
        }.single { it.id == "ex-barbell-back-squat" }
        val name = "$PREFIX upper ${UUID.randomUUID().toString().take(8)}"
        val created = container.routineRepository.create(name)
        container.routineRepository.addExercise(created.id, exercise, 3, 8, 72.5, 90)
        routine = checkNotNull(container.routineRepository.getById(created.id))
        other = container.routineRepository.create(name)
        val session = container.workoutRepository.startRoutine(routine)
        savedId = session.id
        container.workoutRepository.logSet(session.id, exercise.id, 72.5, 9, 8, false)
        container.workoutRepository.finishSession(session.id, "Synthetic preserved result")
        val weekday = Weekday.fromEpochDay(java.time.LocalDate.now().toEpochDay())
        container.scheduleRepository.pin(routine.id, null, weekday)
        container.scheduleRepository.pin(other.id, null, weekday)
        container.plannerRepository.publishPinnedWeek(
            container.preferencesRepository.schedulePreferences.first().weekStart,
            java.time.LocalDate.now().toEpochDay(),
        )
    }

    private fun history() = read {
        container.workoutRepository.observeHistory().first()
    }
    private fun inventory(): List<Any?> = read {
        listOf(history(), container.routineRepository.observeAll().first(), container.scheduleRepository.slots(),
            container.plannerRepository.rules(), container.plannerRepository.observeOccurrences().first())
    }
    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(15_000) { block() } }

    private fun cleanup() {
        try {
            if (::container.isInitialized) read {
                savedId?.let { container.workoutRepository.deleteFinishedSession(it) }
                if (::routine.isInitialized) container.routineRepository.delete(routine.id)
                if (::other.isInitialized) container.routineRepository.delete(other.id)
            }
        } finally {
            try {
                originalFont?.let {
                    shell(if (it == "null") "settings delete system font_scale" else "settings put system font_scale $it")
                    check(shell("settings get system font_scale").trim() == it)
                }
            } finally { environment.restore() }
        }
    }
    private fun capture(name: String) {
        val frame = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { NativeArtifacts.write(name, frame) } finally { frame.recycle() }
    }
    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).use { pipe -> FileInputStream(pipe.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) } }
    private companion object { const val PREFIX = "Plan actions" }
}
