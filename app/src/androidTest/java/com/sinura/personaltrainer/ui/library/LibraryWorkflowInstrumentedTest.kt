package com.sinura.personaltrainer.ui.library

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.draft.LibraryBrowseSnapshot
import com.sinura.personaltrainer.data.draft.DraftChange
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.LibraryBrowseCodec
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.exercise.ExerciseDetailTags
import com.sinura.personaltrainer.ui.plan.PlanTags
import com.sinura.personaltrainer.ui.routines.RoutineEditorTags
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
import java.io.File
import java.security.MessageDigest
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

/** Shipping AppNav, canonical catalogue and isolated private checkpoints. No phone acceptance. */
@RunWith(AndroidJUnit4::class)
class LibraryWorkflowInstrumentedTest {
    private val phase = InstrumentationRegistry.getArguments().getString("libraryRecoveryPhase") ?: "connected"
    private val environment = NativeWorkoutFixtureEnvironment("Synthetic P10")
    private lateinit var container: AppContainer
    private lateinit var marker: File
    private lateinit var fixture: Fixture
    private var retain = false
    private val gson = Gson()
    private data class Fixture(val original: LibraryBrowseSnapshot?, val inventory: String,
        var ownedRoutine: String? = null, var expectedPosition: Float? = null)
    private val preparation = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
            container = app.container
            environment.prepare(container)
            app.markColdStartIntroShown()
            marker = File(app.noBackupFilesDir, "synthetic-p10-library-native.json")
            try { read {
                check(container.workoutRepository.getInProgress() == null && container.activityRepository.getLive() == null)
                container.preferencesRepository.setOnboardingComplete(true)
                container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
                container.preferencesRepository.setLaunchPermissionsAsked(true)
                container.preferencesRepository.markRestAlertsAsked()
                container.preferencesRepository.markRestBatteryHintShown()
                if (phase == "resume") fixture = gson.fromJson(marker.readText(), Fixture::class.java)
                else {
                    check(!marker.exists()) { "Earlier P10 fixture needs reconciliation" }
                    val handle = container.libraryBrowseDrafts.open()
                    check(handle.readable) { "Native fixture refuses an unreadable private view" }
                    fixture = Fixture(handle.remembered, inventory())
                    marker.writeText(gson.toJson(fixture))
                    container.libraryBrowseDrafts.reset(handle)
                    if (phase == "picker") {
                        fixture.ownedRoutine = container.routineRepository.create("Synthetic P10 picker").id
                        marker.writeText(gson.toJson(fixture))
                    }
                }
            } } catch (failure: Throwable) {
                try { cleanup() } catch (other: Throwable) { failure.addSuppressed(other) }
                throw failure
            }
        }
        override fun after() { if (retain) environment.restore() else cleanup() }
    }
    private val scenario = ActivityScenarioRule<MainActivity>(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
    private val compose = AndroidComposeTestRule(scenario) { rule ->
        lateinit var activity: MainActivity
        rule.scenario.onActivity { activity = it }; activity
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(preparation).around(compose)

    @Test fun combinedFiltersDetailReturnAndProcessReopenKeepExactContext() {
        try { journey() }
        catch (failure: Throwable) { runCatching { capture("failure-before-cleanup") }; throw failure }
    }

    private fun journey() {
        awaitTag("navigation-routines")
        compose.onNodeWithTag("navigation-home").assertIsSelected()
        assertInventory()
        tap("navigation-routines")
        if (phase == "picker") { pickerJourney(); return }
        tap(PlanTags.LIBRARY)
        awaitTag(LibraryTags.SEARCH)
        val lift = "ex-incline-dumbbell-bench-press"
        if (phase != "resume") {
            search().performTextReplacement("  press  ")
            search().performImeAction()
            compose.onNodeWithTag(LibraryTags.MUSCLE_FILTERS).performScrollToNode(hasText("Chest"))
            facet("Chest", LibraryTags.MUSCLE_FILTERS).performTouchInput { click(center) }
            compose.onNodeWithTag(LibraryTags.EQUIPMENT_FILTERS).performScrollToNode(hasText("Dumbbell"))
            facet("Dumbbell", LibraryTags.EQUIPMENT_FILTERS).performTouchInput { click(center) }
            scrollLift(lift)
            compose.onNodeWithTag(LibraryTags.lift(lift)).assertIsDisplayed()
            capture("combined-filters")
            tap(LibraryTags.lift(lift))
            awaitTag(ExerciseDetailTags.BACK)
            compose.onNodeWithText("Incline Dumbbell Bench Press").assertIsDisplayed()
            capture("canonical-detail")
            tap(ExerciseDetailTags.BACK)
            awaitTag(LibraryTags.SEARCH)
            assertContext(lift)
        }
        // Clearing facets retains authored search; no-match is a successful read, not unavailable.
        if (phase != "resume") {
            tap(LibraryTags.CLEAR_FILTERS)
            search().assertTextContains("  press  ")
            // A broad, flat equipment range has enough actual catalogue rows to require scrolling.
            search().performTextReplacement("  ")
            search().performImeAction()
            compose.onNodeWithTag(LibraryTags.EQUIPMENT_FILTERS).performScrollToNode(hasText("Dumbbell"))
            facet("Dumbbell", LibraryTags.EQUIPMENT_FILTERS).performTouchInput { click(center) }
        } else search().assertTextContains("  ")
        val scrolledLift = "ex-dumbbell-romanian-deadlift"
        if (phase != "resume") {
            scrollLift(scrolledLift)
            val before = scrollPosition()
            assertTrue("This task must actually scroll away from the first result", before >= 1f)
            capture("scrolled-result-before-detail")
            tap(LibraryTags.lift(scrolledLift))
            awaitTag(ExerciseDetailTags.BACK)
            tap(ExerciseDetailTags.BACK)
            awaitTag(LibraryTags.SEARCH)
            compose.onNodeWithTag(LibraryTags.lift(scrolledLift)).assertIsDisplayed()
            assertEquals(before, scrollPosition(), 0.0001f)
            scenario.scenario.recreate()
            awaitTag(LibraryTags.SEARCH)
            compose.onNodeWithTag(LibraryTags.lift(scrolledLift)).assertIsDisplayed()
            assertEquals(before, scrollPosition(), 0.0001f)
            capture("scrolled-result-after-recreate")
            if (phase == "stage") {
                fixture.expectedPosition = before
                marker.writeText(gson.toJson(fixture))
                // Orderly Back durably flushes this nonzero position before the external process kill.
                tap(LibraryTags.BACK)
                awaitTag(PlanTags.LIBRARY)
                assertInventory()
                retain = true
                capture("checkpoint-before-process-kill")
                return
            }
        } else {
            facet("All", LibraryTags.MUSCLE_FILTERS).assertIsOn()
            facet("Dumbbell", LibraryTags.EQUIPMENT_FILTERS).assertIsOn()
            compose.onNodeWithTag(LibraryTags.lift(scrolledLift)).assertIsDisplayed()
            assertEquals(checkNotNull(fixture.expectedPosition), scrollPosition(), 0.0001f)
            capture("scrolled-result-after-process-reopen")
        }
        search().performTextReplacement("synthetic-p10-no-match-9ac7")
        search().performImeAction()
        compose.onNodeWithText("No matches").assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.EMPTY_CREATE).performScrollTo().assertIsDisplayed()
        capture("no-match")
        assertInventory()
        tap(LibraryTags.BACK)
        awaitTag(PlanTags.LIBRARY)
        tap(PlanTags.LIBRARY)
        awaitTag(LibraryTags.SEARCH)
        search().assertTextContains("synthetic-p10-no-match-9ac7")
        assertNull(read { container.workoutRepository.getInProgress() })
        assertNull(read { container.activityRepository.getLive() })
        assertInventory()
    }

    private fun pickerJourney() {
        val routine = checkNotNull(fixture.ownedRoutine)
        scrollTo(PlanTags.routine(routine))
        tap(PlanTags.routine(routine))
        awaitTag(RoutineEditorTags.ADD_LIFTS)
        scrollTo(RoutineEditorTags.ADD_LIFTS)
        tap(RoutineEditorTags.ADD_LIFTS)
        awaitTag("picker-search")
        val firstId = "ex-barbell-bench-press"
        val secondId = "ex-barbell-back-squat"
        fun pick(name: String, id: String) {
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("picker-search")), useUnmergedTree = true)
                .performTextReplacement(name)
            compose.waitUntil(30_000) { runCatching {
                compose.onNodeWithTag("picker-content").performScrollToNode(hasText(name))
            }.isSuccess }
            // Multi-add rows intentionally toggle; one user tap chooses each lift.
            compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag("picker-content")))
                .assertIsDisplayed().performTouchInput { click(center) }
            compose.waitUntil(30_000) { read {
                container.routineRepository.getById(routine)?.exercises?.any { it.exercise.id == id } == true
            } }
        }
        pick("Barbell Bench Press", firstId)
        pick("Barbell Back Squat", secondId)
        val saved = checkNotNull(read { container.routineRepository.getById(routine) })
        assertEquals(listOf(firstId, secondId), saved.exercises.map { it.exercise.id })
        capture("multi-picker-saved-order")
        // Repeated confirmation must not replay the already saved selection or reorder it.
        compose.onNodeWithTag("picker-action").performSemanticsAction(SemanticsActions.OnClick) { action ->
            action(); action()
        }
        awaitTag(RoutineEditorTags.SAVE)
        // Reopening and cancelling retains the already saved write-through choices.
        scrollTo(RoutineEditorTags.ADD_LIFTS)
        tap(RoutineEditorTags.ADD_LIFTS)
        awaitTag("picker-action")
        compose.waitUntil(30_000) { runCatching {
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("picker-search")), useUnmergedTree = true)
                .assertTextContains("Barbell Back Squat")
        }.isSuccess }
        capture("picker-resumed-query-and-saved-order")
        tap("picker-action")
        assertEquals(saved, read { container.routineRepository.getById(routine) })
        assertInventory()
        capture("picker-cancel-keeps-saved-lifts")
    }

    private fun assertContext(lift: String) {
        search().assertIsDisplayed().assertTextContains("  press  ")
        facet("Chest", LibraryTags.MUSCLE_FILTERS).assertIsOn()
        facet("Dumbbell", LibraryTags.EQUIPMENT_FILTERS).assertIsOn()
        // Do not scroll here: this proves orientation was restored by the shipping screen.
        compose.onNodeWithTag(LibraryTags.lift(lift)).assertIsDisplayed()
    }
    private fun search() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(LibraryTags.SEARCH)), useUnmergedTree = true)
    private fun scrollPosition() = compose.onNodeWithTag(LibraryTags.LIST).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun facet(text: String, row: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(row)))
    private fun scrollTo(tag: String) {
        if (runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess) return
        compose.waitUntil(30_000) { runCatching {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performScrollToNode(hasTestTag(tag))
        }.isSuccess }
    }
    private fun scrollLift(id: String) {
        compose.waitUntil(30_000) { runCatching {
            compose.onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasTestTag(LibraryTags.lift(id)))
        }.isSuccess }
    }
    private fun awaitTag(tag: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun tap(tag: String) {
        awaitTag(tag)
        compose.onNodeWithTag(tag).assertIsDisplayed().performTouchInput { click(center) }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        environment.assertNoBlockingPrompts()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { println("NATIVE_LIBRARY_CAPTURE=" + NativeArtifacts.write("library-$name", bitmap)) }
        finally { bitmap.recycle() }
    }
    private suspend fun inventory(): String {
        val records = listOf(container.exerciseRepository.observeAll().first().sortedBy { it.id },
            container.routineRepository.observeAll().first().filter { !::fixture.isInitialized || it.id != fixture.ownedRoutine }.sortedBy { it.id },
            container.workoutRepository.observeHistory().first().sortedBy { it.id })
        return MessageDigest.getInstance("SHA-256").digest(gson.toJson(records).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    private fun assertInventory() { assertEquals(fixture.inventory, read { inventory() }) }
    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(30_000) { block() } }
    private fun cleanup() {
        try {
            if (::fixture.isInitialized) read {
                assertEquals(fixture.inventory, inventory())
                fixture.ownedRoutine?.let { id ->
                    check(container.routineRepository.getById(id)?.name == "Synthetic P10 picker")
                    container.routineRepository.delete(id)
                }
                val handle = container.libraryBrowseDrafts.open()
                val original = fixture.original
                if (original != null) container.libraryBrowseDrafts.keep(handle, original)
                else check(container.durableDrafts.bind(DraftOwner(DraftForm.LIBRARY, "last-view"), LibraryBrowseCodec)
                    .clear(checkNotNull(handle.checkpoint).version) is DraftChange.Written)
                check(marker.delete())
            }
        } finally { environment.restore() }
    }
}
