package com.sinura.personaltrainer.ui.routines

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DraftRead
import com.sinura.personaltrainer.data.draft.RoutineFieldsCodec
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.components.AddToRoutineTags
import com.sinura.personaltrainer.ui.library.LibraryTags
import com.sinura.personaltrainer.ui.plan.PlanTags
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Shipping MainActivity/AppNav and private files, on an opted-in disposable AVD only. */
@RunWith(AndroidJUnit4::class)
class RoutineWorkflowInstrumentedTest {
    private val phase = InstrumentationRegistry.getArguments().getString("routineRecoveryPhase") ?: "connected"
    private val environment = NativeWorkoutFixtureEnvironment("Synthetic P09 routine")
    private lateinit var container: AppContainer
    private lateinit var marker: File
    private lateinit var fixture: Fixture
    private var retain = false
    private val gson = Gson()
    private data class Fixture(val id: String, val itemId: String?, val original: Routine,
        val originalIds: List<String>, val originalHash: String, val unit: WeightUnit,
        var ownedIds: List<String>)
    private val preparation = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
            container = app.container
            environment.prepare(container)
            app.markColdStartIntroShown()
            marker = File(app.noBackupFilesDir, "synthetic-p09-routine-native.json")
            try { read {
                check(container.workoutRepository.getInProgress() == null && container.activityRepository.getLive() == null)
                container.preferencesRepository.setOnboardingComplete(true)
                container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
                container.preferencesRepository.setLaunchPermissionsAsked(true)
                container.preferencesRepository.markRestAlertsAsked()
                container.preferencesRepository.markRestBatteryHintShown()
                if (phase == "resume") fixture = gson.fromJson(marker.readText(), Fixture::class.java)
                else {
                    check(!marker.exists()) { "An earlier P09 fixture needs reconciliation" }
                    val originals = container.routineRepository.observeAll().first()
                    val hash = inventoryHash(originals.map { it.id }.toSet())
                    if (phase == "library") {
                        check(originals.isEmpty()) { "The native empty-catalogue handoff requires an empty disposable fixture; no existing routines are cleared" }
                        fixture = Fixture(id = "", itemId = null,
                            original = Routine(id = "", name = "", notes = "", createdAt = 0, updatedAt = 0, exercises = emptyList()),
                            originalIds = emptyList(), originalHash = hash,
                            unit = container.preferencesRepository.weightUnit.first(), ownedIds = emptyList())
                        marker.writeText(gson.toJson(fixture))
                        return@read
                    }
                    val row = container.routineRepository.create(name = "Synthetic P09 routine", notes = "original notes")
                    if (phase != "import") {
                        val bench = container.exerciseRepository.observeAll().first { rows -> rows.any { it.id == "ex-barbell-bench-press" } }
                            .single { it.id == "ex-barbell-bench-press" }
                        container.routineRepository.addExercise(routineId = row.id, exercise = bench,
                            targetSets = 3, targetReps = 8, targetWeightKg = 50.0, restSeconds = 120)
                    }
                    val prepared = checkNotNull(container.routineRepository.getById(row.id))
                    fixture = Fixture(id = row.id, itemId = prepared.exercises.singleOrNull()?.id, original = prepared,
                        originalIds = originals.map { it.id }, originalHash = hash,
                        unit = container.preferencesRepository.weightUnit.first(), ownedIds = listOf(row.id))
                    marker.writeText(gson.toJson(fixture))
                }
            } } catch (failure: Throwable) { try { cleanup() } catch (other: Throwable) { failure.addSuppressed(other) }; throw failure }
        }
        override fun after() { if (retain) environment.restore() else cleanup() }
    }
    private val scenario = ActivityScenarioRule<MainActivity>(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
    private val compose = AndroidComposeTestRule(scenario) { rule ->
        lateinit var activity: MainActivity
        rule.scenario.onActivity { activity = it }; activity
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(preparation).around(compose)

    @Test fun connectedRoutineDraftAndImportPreserveExactRecords() {
        awaitTag("navigation-routines")
        compose.onNodeWithTag("navigation-home").assertIsSelected()
        if (phase == "library") { libraryJourney(); return }
        // Reopening does not redirect startup into the unfinished editor or submit it.
        assertEquals(fixture.original, read { container.routineRepository.getById(fixture.id) })
        compose.onNodeWithTag("navigation-routines").performClick()
        scrollTo(PlanTags.routine(fixture.id))
        compose.onNodeWithTag(PlanTags.routine(fixture.id)).performClick()
        if (phase == "import") { importJourney(); return }
        if (phase != "resume") {
            compose.waitUntil(30_000) { runCatching { compose.onNode(hasSetTextAction() and hasText(fixture.original.name)).assertIsDisplayed() }.isSuccess }
            compose.onNode(hasSetTextAction() and hasText(fixture.original.name)).performTextReplacement("Synthetic P09 intended routine")
            scrollTo(SessionLiftTags.card(checkNotNull(fixture.itemId)))
            compose.onNodeWithTag(SessionLiftTags.card(checkNotNull(fixture.itemId))).performClick()
            scrollTo(CompactLiftTags.TARGET_WEIGHT)
            compose.onNodeWithTag("Type a target weight").performClick()
            awaitTag(NumberEntryTags.FIELD)
            compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("72.")
            compose.waitUntil(30_000) { fields().numbers.singleOrNull()?.text == "72." && fields().name == "Synthetic P09 intended routine" }
            assertEquals(fixture.original, read { container.routineRepository.getById(fixture.id) })
            capture("raw-number-kept")
            if (phase == "stage") { retain = true; return }
        } else {
            awaitTag(NumberEntryTags.FIELD)
            assertEquals("72.", compose.onNodeWithTag(NumberEntryTags.FIELD).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            assertEquals(fixture.original, read { container.routineRepository.getById(fixture.id) })
            capture("number-after-process-reopen")
        }
        compose.onNodeWithTag(NumberEntryTags.CONFIRM).assertIsNotEnabled()
        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("72.0")
        compose.onNodeWithTag(NumberEntryTags.CONFIRM).performClick()
        compose.waitUntil(30_000) { read { container.routineRepository.getById(fixture.id)?.exercises?.single()?.targetWeightKg } ==
            com.sinura.personaltrainer.domain.WeightConverter.toKg(72.0, fixture.unit) }
        scrollTo(RoutineEditorTags.SAVE)
        compose.onNodeWithTag(RoutineEditorTags.SAVE).performTouchInput { click(center) }
        awaitTag("navigation-routines")
        val stored = checkNotNull(read { container.routineRepository.getById(fixture.id) })
        assertEquals("Synthetic P09 intended routine", stored.name)
        assertEquals(fixture.original.exercises.single().id, stored.exercises.single().id)
        assertEquals(com.sinura.personaltrainer.domain.WeightConverter.toKg(72.0, fixture.unit), checkNotNull(stored.exercises.single().targetWeightKg), 0.00001)
        assertEquals(fixture.original.exercises.single().targetReps, stored.exercises.single().targetReps)
        assertOriginalInventory()
        capture("saved-routine-return")
    }

    private fun libraryJourney() {
        compose.onNodeWithTag("navigation-routines").performClick()
        awaitTag(PlanTags.LIBRARY)
        compose.onNodeWithTag(PlanTags.LIBRARY).performClick()
        awaitTag(LibraryTags.SEARCH)
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(LibraryTags.SEARCH)))
            .performTextReplacement("Barbell Bench Press")
        val add = LibraryTags.add("ex-barbell-bench-press")
        compose.waitUntil(30_000) { runCatching {
            compose.onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasTestTag(add))
        }.isSuccess }
        compose.onNodeWithTag(add).performClick()
        awaitTag(AddToRoutineTags.CREATE)
        assertEquals(0, read { container.routineRepository.count() })
        // Cancelling the handoff stays in Library and creates no empty stub.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").use { pipe ->
            FileInputStream(pipe.fileDescriptor).use { it.readBytes() }
        }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(AddToRoutineTags.SHEET).fetchSemanticsNodes().isEmpty() }
        awaitTag(LibraryTags.SEARCH)
        assertEquals(0, read { container.routineRepository.count() })
        compose.onNodeWithTag(add).performClick()
        awaitTag(AddToRoutineTags.CREATE)
        compose.onNodeWithTag(AddToRoutineTags.CREATE).performScrollTo().performClick()
        awaitTag(RoutineEditorTags.SAVE)
        val made = read { container.routineRepository.observeAll().first { it.size == 1 }.single() }
        fixture = fixture.copy(id = made.id, itemId = made.exercises.single().id,
            original = made, ownedIds = listOf(made.id))
        marker.writeText(gson.toJson(fixture))
        assertEquals("ex-barbell-bench-press", made.exercises.single().exercise.id)
        scenario.scenario.recreate()
        awaitTag(RoutineEditorTags.SAVE)
        assertEquals(made, read { container.routineRepository.getById(made.id) })
        assertEquals(1, read { container.routineRepository.count() })
        compose.onNode(hasSetTextAction()).performTextReplacement("Upper A · strength")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").use { pipe ->
            FileInputStream(pipe.fileDescriptor).use { it.readBytes() }
        }
        compose.onNodeWithTag(RoutineEditorTags.SAVE).assertIsDisplayed()
        capture("library-selected-lift-editor")
        compose.onNodeWithTag(RoutineEditorTags.SAVE).performClick()
        awaitTag(LibraryTags.SEARCH)
        val saved = checkNotNull(read { container.routineRepository.getById(made.id) })
        assertEquals("Upper A · strength", saved.name)
        assertEquals(made.exercises.single().id, saved.exercises.single().id)
        assertEquals(1, read { container.routineRepository.count() })
        assertOriginalInventory()
        capture("library-handoff-saved")
    }

    private fun importJourney() {
        scrollTo(RoutineEditorTags.PASTE_FIELD)
        val text = "Upper A\n1. Barbell Bench Press — 3×8\n2. Barbell Bench Press — 5×6\n\nLower A\n1. Barbell Bench Press — 2×10"
        compose.onNodeWithTag(RoutineEditorTags.PASTE_FIELD).performTextReplacement(text)
        compose.onNodeWithTag(RoutineEditorTags.PASTE_CONFIRM).performScrollTo().performClick()
        awaitTag(RoutineEditorTags.IMPORT_PREVIEW)
        val preview = checkNotNull(fields().importPreview)
        fixture.ownedIds = (fixture.ownedIds + preview.changes.map { it.id }).distinct()
        marker.writeText(gson.toJson(fixture))
        assertEquals(2, preview.changes.size)
        assertEquals(1, fields().importDetails?.duplicates?.size)
        assertEquals(fixture.original, read { container.routineRepository.getById(fixture.id) })
        assertOriginalInventory()
        capture("import-preview")
        compose.onNodeWithTag(RoutineEditorTags.IMPORT_CLOSE).performScrollTo().performClick()
        compose.waitUntil(30_000) {
            !fields().importVisible && compose.onAllNodesWithTag(RoutineEditorTags.IMPORT_PREVIEW).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag(RoutineEditorTags.IMPORT_RESUME).performScrollTo().assertIsDisplayed()
            .performTouchInput { click(center) }
        awaitTag(RoutineEditorTags.IMPORT_PREVIEW)
        assertEquals(preview, fields().importPreview)
        compose.onNodeWithTag(RoutineEditorTags.IMPORT_CONFIRM).performScrollTo().assertIsDisplayed().performTouchInput { click(center); click(center) }
        compose.waitUntil(30_000) { fields().importApplied && fields().importPreview == null }
        val saved = read { preview.changes.map { checkNotNull(container.routineRepository.editorRecord(it.id)) } }
        assertEquals(preview.changes.map { it.after }, saved)
        assertOriginalInventory()
        assertNull(read { container.workoutRepository.getInProgress() })
        assertNull(read { container.activityRepository.getLive() })
        capture("import-receipt")
    }
    private fun binding() = container.durableDrafts.bind(DraftOwner(DraftForm.ROUTINE, "fields:existing:${fixture.id}"), RoutineFieldsCodec)
    private fun fields() = read { checkNotNull((binding().read() as DraftRead.PresentCheckpoint).checkpoint.draft) }
    private fun awaitTag(tag: String) = compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun scrollTo(tag: String) {
        if (runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess) return
        compose.waitUntil(30_000) { runCatching { compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasTestTag(tag)) }.isSuccess }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(100, 5_000)
        val bitmap = checkNotNull(automation.takeScreenshot())
        try { NativeArtifacts.write("routine-$name", bitmap) } finally { bitmap.recycle() }
    }
    private suspend fun inventoryHash(ids: Set<String>): String {
        val snapshot = listOf(container.routineRepository.observeAll().first().filter { it.id in ids }.sortedBy { it.id },
            container.scheduleRepository.slots(), container.plannerRepository.rules(),
            container.plannerRepository.observeOccurrences().first(), container.workoutRepository.observeHistory().first())
        return MessageDigest.getInstance("SHA-256").digest(gson.toJson(snapshot).toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private fun assertOriginalInventory() = assertEquals(fixture.originalHash, read { inventoryHash(fixture.originalIds.toSet()) })
    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(30_000) { block() } }
    private fun cleanup() {
        try { if (::fixture.isInitialized) read {
            check(fixture.ownedIds.none { it in fixture.originalIds })
            for (id in fixture.ownedIds) {
                if (container.routineRepository.getById(id) == null) continue
                val handle = container.routineDrafts.open(id = id, initialExerciseId = null)
                container.routineDrafts.discard(handle)
                container.routineRepository.delete(id)
            }
            container.preferencesRepository.setWeightUnit(fixture.unit)
            assertOriginalInventory()
            check(marker.delete())
        } } finally { environment.restore() }
    }
}
