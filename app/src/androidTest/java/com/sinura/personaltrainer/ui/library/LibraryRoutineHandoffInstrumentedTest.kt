package com.sinura.personaltrainer.ui.library

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.ui.components.AddToRoutineTags
import com.sinura.personaltrainer.ui.plan.PlanTags
import com.sinura.personaltrainer.ui.routines.RoutineEditorTags
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
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

/** Actual AppNav and Room: dismiss -> choose -> Create -> recreate -> Save -> same Library query. */
@RunWith(AndroidJUnit4::class)
class LibraryRoutineHandoffInstrumentedTest {
    private lateinit var container: AppContainer
    private lateinit var exercise: Exercise
    private val environment = NativeWorkoutFixtureEnvironment("Library handoff")
    private var admittedEmptyCatalog = false
    private val seedRule = object : ExternalResource() {
        override fun before() {
            container = ApplicationProvider.getApplicationContext<PersonalTrainerApp>().container
            environment.prepare(container)
            try {
                read {
                    // This fixture requires an empty synthetic routine catalog; never clear one.
                    check(container.routineRepository.observeAll().first().isEmpty())
                    admittedEmptyCatalog = true
                    container.preferencesRepository.setOnboardingComplete(true)
                    container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
                    container.preferencesRepository.setLaunchPermissionsAsked(true)
                    container.preferencesRepository.markRestBatteryHintShown()
                    container.preferencesRepository.markRestAlertsAsked()
                    exercise = container.exerciseRepository.observeAll().first { rows ->
                        rows.any { it.id == "ex-barbell-back-squat" }
                    }.single { it.id == "ex-barbell-back-squat" }
                }
            } catch (failure: Throwable) {
                try { cleanup() } catch (secondary: Throwable) { failure.addSuppressed(secondary) }
                throw failure
            }
        }
        override fun after() { cleanup() }
    }
    private val scenario = ActivityScenarioRule<MainActivity>(
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
    )
    private val compose = AndroidComposeTestRule(scenario) { rule ->
        lateinit var activity: MainActivity
        rule.scenario.onActivity { activity = it }
        activity
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seedRule).around(compose)

    @Test
    fun selectedLiftSurvivesEditorRecreationAndReturnsToTheSameLibrarySearch() {
        val historyBefore = read { container.workoutRepository.observeHistory().first() }
        awaitTag("navigation-routines")
        tap("navigation-routines")
        tap(PlanTags.LIBRARY)
        awaitTag(LibraryTags.SEARCH)
        searchField().performTextReplacement(exercise.name)
        openSelectedLift()
        awaitTag(AddToRoutineTags.CREATE)
        scenario.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitTag(LibraryTags.SEARCH)
        assertTrue(read { container.routineRepository.observeAll().first().isEmpty() })

        openSelectedLift()
        compose.onNodeWithTag(AddToRoutineTags.CREATE).performScrollTo()
        tap(AddToRoutineTags.CREATE)
        awaitTag(RoutineEditorTags.SAVE)
        val created = read { container.routineRepository.observeAll().first().single() }
        assertEquals(exercise.id, created.exercises.single().exercise.id)
        scenario.scenario.recreate()
        awaitTag(RoutineEditorTags.SAVE)
        val restored = read { container.routineRepository.observeAll().first().single() }
        assertEquals(created, restored)
        compose.onNode(hasSetTextAction())
            .performTextReplacement("Library handoff routine")
        tap(RoutineEditorTags.SAVE)
        awaitTag(LibraryTags.SEARCH)
        searchField().assertIsDisplayed().assertTextContains(exercise.name)
        compose.onNodeWithContentDescription("Add ${exercise.name} to a routine").assertIsDisplayed()
        val saved = read { container.routineRepository.observeAll().first().single() }
        assertEquals(created.id, saved.id)
        assertEquals("Library handoff routine", saved.name)
        assertEquals(exercise.id, saved.exercises.single().exercise.id)
        assertEquals(historyBefore, read { container.workoutRepository.observeHistory().first() })
    }

    private fun searchField() = compose.onNode(
        matcher = hasSetTextAction() and hasAnyAncestor(hasTestTag(LibraryTags.SEARCH)),
        useUnmergedTree = true,
    )

    private fun openSelectedLift() {
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add ${exercise.name} to a routine")
            .assertIsDisplayed().performTouchInput { click(center) }
        compose.waitForIdle()
    }
    private fun awaitTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed().performTouchInput { click(center) }
        compose.waitForIdle()
    }
    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) {
        withTimeout(15_000) { block() }
    }
    private fun cleanup() {
        try {
            if (admittedEmptyCatalog) read {
                val rows = container.routineRepository.observeAll().first()
                check(rows.all { it.name == "Untitled routine" || it.name == "Library handoff routine" })
                rows.forEach { container.routineRepository.delete(it.id) }
            }
        } finally { environment.restore() }
    }
}
