package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.backup.AuthoredInventory
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupExercise
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupPreferences
import com.sinura.personaltrainer.data.backup.BackupSession
import com.sinura.personaltrainer.data.backup.BackupSessionExercise
import com.sinura.personaltrainer.data.backup.BackupSetLog
import com.sinura.personaltrainer.data.backup.BackupValidation
import com.sinura.personaltrainer.data.backup.BackupValidator
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.summary.SavedWorkRenderHost
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accepted restored timed originals must keep their saved type through actual Edit/Save.
 *
 * Six independent cases pass BackupService.prepareRestore/commitRestore, including the production
 * decoder/validator, verified safety copy, LocalBackupRepository replacement and journal
 * epilogue over real isolated Room. The shipping Detail VM and screen supply the rows and
 * Edit sheet and Save button. Each test restores fresh dependencies; an untouched-save
 * refusal cannot prevent the separate rep/effort-adjustment counter from running.
 * This is a native-graphics component counter, not AppNav, device touch feel or TalkBack.
 *
 * An exception is a failure. Native frames/semantics are retained when rendering allows it;
 * the complete post-restore inventory is checked again in teardown even if a counter fails.
 * Only the expressly attempted original-row RPE correction may differ from that baseline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
class SessionDetailRestoredHoldSafetyRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var host: SavedWorkRenderHost
    private var detail: SessionDetailViewModel? = null
    private var originalSession: WorkoutSession? = null
    private var restoredInventory: List<Any?>? = null
    private var fixtureName = "not-restored"
    private var permittedOriginalRpe: Set<Int?> = setOf(null)
    private var lastSaveOutcome = "not-attempted"
    private var direction = LayoutDirection.Ltr
    private val artifactRunId = UUID.randomUUID().toString()
    private val artifactDirectory = File("build/screen-renders/session-detail-restored-hold-safety/$artifactRunId")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        host = SavedWorkRenderHost(
            compose = compose,
            contentTag = { SessionDetailTestTags.CONTENT },
            facts = {
                "fixture=$fixtureName; runId=$artifactRunId; sourceInventory=${artifactDirectory.absolutePath}; " +
                    "direction=$direction; resourceFont=${RuntimeEnvironment.getApplication().resources.configuration.fontScale}; " +
                    "resourceLocales=${RuntimeEnvironment.getApplication().resources.configuration.locales}; " +
                    "resourceDirection=${RuntimeEnvironment.getApplication().resources.configuration.layoutDirection}; " +
                    "defaultLocale=${Locale.getDefault()}; " +
                    "saveOutcome=$lastSaveOutcome; original=$originalSession; detail=${detail?.uiState?.value}"
            },
        )
    }

    @After
    fun tearDown() {
        try {
            try {
                runBlocking { detail?.clearAndJoinForTest() }
            } finally {
                restoredInventory?.let { original ->
                    val after = inventory()
                    writeInventory("teardown", after)
                    assertInventorySafety(original, after, "teardown")
                }
            }
        } finally {
            try {
                deps.restTimerController.stop()
                dispatcher.scheduler.advanceUntilIdle()
            } finally {
                try { deps.close() } finally { Dispatchers.resetMain() }
            }
        }
    }

    @Test
    fun restoredPlannedHoldUntouchedSavePreservesExactTimedOriginal() =
        host.evidence("planned-hold-untouched-save-360x640-font10") {
            verifyHold(holdFixture(planned = true), adjusted = false)
        }

    @Test
    fun restoredSavedOnlyHoldUntouchedSavePreservesExactTimedOriginalAndTruthfulOutcome() =
        host.evidence("saved-only-hold-untouched-save-360x640-font10") {
            verifyHold(holdFixture(planned = false), adjusted = false)
        }

    @Test
    fun restoredSavedOnlyHoldRepAndEffortSaveCannotConvertOriginalToRepWork() =
        host.evidence("saved-only-hold-rep-effort-save-360x640-font10") {
            verifyHold(holdFixture(planned = false), adjusted = true)
        }

    @Test
    fun restoredPlannedHoldRepAndEffortSaveKeepsOriginalTimedType() =
        host.evidence("planned-hold-rep-effort-save-360x640-font10") {
            verifyHold(holdFixture(planned = true), adjusted = true)
        }

    @Test
    fun restoredSavedOnlyNegativeRepHoldUntouchedSavePreservesExactOriginalRepresentation() =
        host.evidence("saved-only-negative-rep-hold-untouched-save-360x640-font10") {
            verifyHold(holdFixture(planned = false, originalReps = -3), adjusted = false)
        }

    @Test
    fun restoredSavedOnlyHoldRepSaveWithoutEffortKeepsExactTimedOriginal() =
        host.evidence("saved-only-hold-rep-without-effort-save-360x640-font10") {
            verifyHold(holdFixture(planned = false), adjusted = true, withEffort = false)
        }

    private fun verifyHold(fixture: RestoredFixture, adjusted: Boolean, withEffort: Boolean = true) {
        fixtureName = fixture.name
        host.font = 1f
        val configuration = RuntimeEnvironment.getApplication().resources.configuration
        assertEquals("actual native font", 1f, configuration.fontScale, .001f)
        assertEquals("actual resource layout", android.view.View.LAYOUT_DIRECTION_LTR, configuration.layoutDirection)
        assertTrue("the controlled original is a pure timed representation", fixture.originalReps < 1)
        restore(fixture.document, fixture.originalReps)
        mount()
        val model = checkNotNull(detail)
        compose.awaitThat("actual Detail loads the restored hold", { model.uiState.value }) {
            !model.uiState.value.isLoading && model.uiState.value.session?.id == SESSION_ID
        }
        assertEquals(originalSession, model.uiState.value.session)
        assertTrue("the fixture has a nonempty prescription graph", checkNotNull(originalSession).exercises.isNotEmpty())
        assertEquals(fixture.planned, checkNotNull(originalSession).exercises.any { it.exercise.id == HOLD_ID })
        fixture.prescriptions.forEach { plan ->
            val under = FilledLiftCardTags.plannedEntry(plan.id)
            for (value in listOf(plan.work, plan.load, plan.rest)) host.readable(host.words(value, under), value)
        }
        assertTimedType(checkNotNull(model.uiState.value.session), fixture.originalReps)
        traverseActualList()
        val row = FilledLiftCardTags.setRow(SET_ID)
        host.readable(host.words("12.5 kg × 45s", row), "12.5 kg × 45s")
        host.readable(host.words("Set 1", row), "Set 1")
        assertEquals(1, compose.onAllNodes(hasTestTag(row), useUnmergedTree = true).fetchSemanticsNodes().size)
        val edit = hasTestTag(SessionDetailTestTags.EDIT_SET) and hasAnyAncestor(hasTestTag(row))
        assertEquals(1, compose.onAllNodes(edit).fetchSemanticsNodes().size)
        host.capture("original-timed-row-and-edit")
        host.touch(compose.onNode(edit))
        host.readable(host.words("Edit set 1"), "Edit set 1")
        compose.onNodeWithTag("Type a weight").assertContentDescriptionEquals("Weight 12.5 kg")
        // The shipping sheet initially passes the exact original reps into RepsStepper;
        // its numeral/description expose -3 literally for the accepted negative fixture.
        // Preserve that stored representation rather than inventing a zero normalization.
        val originalRepNumeral = fixture.originalReps.toString()
        compose.onNodeWithTag("Type a rep count").assertContentDescriptionEquals("reps $originalRepNumeral")
        expandCorrectionSheetIfOffered(SET_ID)
        host.readable(host.words("12.5", "Type a weight"), "12.5")
        host.readable(host.words(originalRepNumeral, "Type a rep count"), originalRepNumeral)
        val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        for (value in listOf(6, 7, 8, 9, 10)) compose.onNode(hasText(value.toString()) and checkbox).assertIsOff()
        assertInventoryUnchanged("original-editor-open")
        if (adjusted) {
            host.touch(compose.onNode(hasText("+1") and hasAnyAncestor(actualDialog())))
            compose.onNodeWithTag("Type a rep count").assertContentDescriptionEquals("reps 1")
            host.readable(host.words("1", "Type a rep count"), "1")
            if (withEffort) {
                host.touch(compose.onNode(hasText("8") and checkbox and hasAnyAncestor(actualDialog())))
                compose.onNode(hasText("8") and checkbox).assertIsOn()
                host.readable(host.words("8"), "8")
            } else {
                for (value in listOf(6, 7, 8, 9, 10)) compose.onNode(hasText(value.toString()) and checkbox).assertIsOff()
                host.action(compose.onNode(hasText("8") and checkbox)).assertIsOff()
                host.readable(host.words("8"), "8")
            }
            assertInventoryUnchanged("adjusted-draft-before-save")
            // Only the attempted original-row effort correction is permitted. No rep,
            // duration, load, identity, timestamp or unrelated inventory change is masked.
            permittedOriginalRpe = if (withEffort) setOf(null, 8) else setOf(null)
        }
        host.capture("actual-editor-before-save")
        saveThroughActualEditor(model)
        val after = inventory()
        writeInventory("actual-post-save-before-safety-assertions", after)
        lastSaveOutcome = model.error.value?.let { "explicit-refusal: $it" } ?: "completed-without-refusal"
        val stored = rawOriginal(after)
        File(artifactDirectory, "$fixtureName-actual-save-outcome.txt").writeText(
            "fixture=$fixtureName\nadjusted=$adjusted\nwithEffort=$withEffort\noutcome=$lastSaveOutcome\noriginal=$stored\n",
        )
        compose.awaitThat("actual Detail reflects the completed original-row write or refusal", { model.uiState.value }) {
            model.uiState.value.session?.sets?.singleOrNull()?.let {
                it.id == stored.id && it.reps == stored.reps && it.rpe == stored.rpe &&
                    it.durationSeconds == stored.durationSeconds && it.weightKg == stored.weightKg
            } == true
        }
        host.capture("actual-save-outcome-before-safety-assertions")
        assertTimedType(checkNotNull(model.uiState.value.session), fixture.originalReps)
        assertInventorySafety(checkNotNull(restoredInventory), after, "actual-post-save")
        val refusal = model.error.value
        if (refusal != null) {
            assertTrue("a refusal has an explicit message", refusal.isNotBlank())
            host.readable(host.words(refusal), refusal)
        }
        assertNull("a valid restored timed original must save successfully through its actual editor", refusal)
        assertEquals("a completed correction carries only the requested effort", if (adjusted && withEffort) 8 else null, stored.rpe)
        host.readable(host.words("12.5 kg × 45s", row), "12.5 kg × 45s")
        host.readable(host.words(if (stored.rpe == null) "Set 1" else "Set 1 · RPE 8", row),
            if (stored.rpe == null) "Set 1" else "Set 1 · RPE 8")
        host.capture("preserved-timed-row-after-truthful-outcome")
        assertInventorySafety(checkNotNull(restoredInventory), inventory(), "complete-counter")
    }

    private fun actualDialog() = SemanticsMatcher("the actual editor dialog") {
        it.config.getOrNull(SemanticsProperties.IsDialog) != null
    }

    private fun saveThroughActualEditor(model: SessionDetailViewModel) {
        val scope = checkNotNull(model.viewModelScope.coroutineContext[Job])
        val priorJobs = scope.children.toSet()
        host.touch(compose.onNode(hasText("Save") and hasAnyAncestor(actualDialog())))
        // Save synchronously launches the real VM request after the actual pointer click.
        // Observe its real scope; do not clear, cancel, invoke the repository, or infer
        // successful saving from unchanged values while a write might still be pending.
        compose.awaitThat("the actual Save-launched VM jobs complete", {
            scope.children.filter { it !in priorJobs }.map { "$it active=${it.isActive}" }.toList()
        }) {
            scope.children.none { it !in priorJobs && it.isActive }
        }
        host.drain()
        compose.awaitThat("the actual Save closes the original editor", { model.error.value }) {
            compose.onAllNodes(hasTestTag(SetEditTestTags.DELETE)).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun assertTimedType(session: WorkoutSession, originalReps: Int) {
        val set = session.sets.single()
        assertEquals(SET_ID, set.id)
        assertEquals(SESSION_ID, set.sessionId)
        assertEquals(HOLD_ID, set.exerciseId)
        assertEquals("a typed original hold keeps its exact original nonpositive rep representation", originalReps, set.reps)
        assertEquals(45, set.durationSeconds)
        assertEquals(12.5, set.weightKg, .0001)
        assertEquals(STAMP + 1_000L, set.completedAt)
        assertEquals(1, set.setNumber)
        assertFalse(set.isWarmup)
        assertEquals("a timed original contributes no rep-work volume", 0.0, session.work().volumeKg, .0001)
        assertEquals(0, session.work().bodyweightReps)
        assertEquals(1, session.workingSetCount())
        assertEquals(mapOf(SET_ID to 1), session.filledLifts().flatMap { it.sets }.groupingBy { it.id }.eachCount())
    }
    private fun restore(document: BackupDocument, originalReps: Int) = runBlocking {
        check(artifactDirectory.isDirectory || artifactDirectory.mkdirs())
        val json = BackupJson.encode(document)
        File(artifactDirectory, "$fixtureName-incoming.json").writeText(json)
        val decoded = BackupJson.decode(json)
        assertEquals("the accepted fixture uses the supported v5 format", 5, decoded.version)
        assertEquals("the fixture pins the literal original timed rep representation", originalReps, decoded.setLogs.single().reps)
        assertEquals("serialized backup preserves every original session", document.sessions, decoded.sessions)
        assertEquals("serialized backup preserves every distinct prescription", document.sessionExercises, decoded.sessionExercises)
        assertEquals("serialized backup preserves every original saved row", document.setLogs, decoded.setLogs)
        assertEquals("serialized backup preserves all original exercise identities", document.exercises, decoded.exercises)
        val validation = BackupValidator.validate(decoded, AuthoredInventory.EMPTY)
        assertTrue("this is an accepted supported backup shape: $validation", validation is BackupValidation.Valid)
        assertTrue("the isolated fixture begins with no authored rows", deps.localBackupRepository.authoredInventory().isEmpty)

        // Take the same prepare/confirm-commit path as a supported file restore. There is
        // no destructive-restore override, DAO seed or shortened journal/catalog epilogue.
        val beforePrepare = inventory()
        writeInventory("before-prepare", beforePrepare)
        val plan = deps.backupService.prepareRestore(json, sourceName = "$fixtureName.json")
        assertEquals("the service accepts the exact supported serialized graph", decoded, plan.document)
        assertEquals("prepare is read-only for all saved data and preferences", beforePrepare, inventory())
        val restored = deps.backupService.commitRestore(plan)
        restoredInventory = inventory()
        writeInventory("restored", checkNotNull(restoredInventory))
        assertTrue("the full service commit restores its preferences", restored.preferencesRestored)
        assertFalse("the supported restore has no unfinished settings phase", restored.settingsPending)
        assertEquals(
            "the full commit records its verified safety copy",
            restored.safetySnapshotId,
            deps.backupService.listSafetySnapshots().single().id,
        )

        val snapshot = deps.localBackupRepository.createSnapshot()
        assertEquals("replacement keeps all sessions and captured timestamps", decoded.sessions, snapshot.sessions)
        assertEquals("replacement keeps distinct row IDs and every prescription field", decoded.sessionExercises, snapshot.sessionExercises)
        assertEquals("replacement keeps complete original SetLog values", decoded.setLogs, snapshot.setLogs)
        val originalExerciseIds = decoded.exercises.map { it.id }.toSet()
        assertEquals(
            "catalog reconciliation preserves these custom restored exercise identities",
            decoded.exercises,
            snapshot.exercises.filter { it.id in originalExerciseIds },
        )
        assertTrue("no live workout is introduced by replacement", deps.database.workoutDao().getAllSessions().all { it.finishedAt != null })
        assertNull(deps.workoutRepository.getInProgress())
        assertFalse("the full supported restore closes its journal", deps.restoreJournal.isOpen())
        originalSession = checkNotNull(deps.workoutRepository.getSession(SESSION_ID))
        assertEquals(decoded.sessionExercises.map { it.id }, checkNotNull(originalSession).exercises.map { it.id })
        val saved = checkNotNull(originalSession).sets.single()
        assertEquals(SET_ID, saved.id)
        assertEquals(SESSION_ID, saved.sessionId)
        assertEquals(decoded.setLogs.single().exerciseId, saved.exerciseId)
        assertEquals(1, saved.setNumber)
        assertEquals(12.5, saved.weightKg, .0001)
        assertEquals(originalReps, saved.reps)
        assertNull(saved.rpe)
        assertFalse(saved.isWarmup)
        assertEquals(STAMP + 1_000L, saved.completedAt)
        assertEquals(45, saved.durationSeconds)
        assertInventoryUnchanged("restored-before-mount")
    }

    private fun mount() {
        detail = SessionDetailViewModel(
            ApplicationProvider.getApplicationContext(),
            SavedStateHandle(mapOf("sessionId" to SESSION_ID)),
            deps,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalWeightUnit provides WeightUnit.KG,
                LocalLayoutDirection provides direction,
            ) {
                PersonalTrainerTheme(reduceMotion = true) {
                    SessionDetailScreen(
                        onBack = { throw AssertionError("inspection must not leave this saved session") },
                        onOpenExercise = { throw AssertionError("Edit must use the original saved row") },
                        onOpenActiveSession = { throw AssertionError("inspection must not create a live workout") },
                        viewModel = checkNotNull(detail),
                    )
                }
            }
        }
        host.drain()
    }

    private fun expandCorrectionSheetIfOffered(setId: String) {
        val dialog = SemanticsMatcher("the actual correction dialog") {
            it.config.getOrNull(SemanticsProperties.IsDialog) != null
        }
        val sheet = SemanticsMatcher("the actual modal sheet pane") {
            it.config.getOrNull(SemanticsProperties.PaneTitle) != null
        } and hasAnyAncestor(dialog)
        val expand = SemanticsMatcher("the actual sheet offers Expand") {
            it.config.getOrNull(SemanticsActions.Expand)?.action != null
        } and hasAnyAncestor(dialog)
        assertEquals(
            "the original correction uses exactly one actual modal sheet",
            1,
            compose.onAllNodes(sheet, useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        host.capture("correction-default-anchor-$setId")
        assertInventoryUnchanged("correction-default-anchor-$setId")
        val initial = compose.onNode(sheet, useUnmergedTree = true).fetchSemanticsNode()
        val initialTop = initial.positionInWindow.y
        val expanders = compose.onAllNodes(expand, useUnmergedTree = true).fetchSemanticsNodes()
        if (expanders.isEmpty()) {
            assertTrue(
                "a sheet without an Expand action is already fully inside its actual window",
                initial.boundsInWindow.height >= initial.size.height - 1f,
            )
            return
        }
        assertEquals("the visible sheet offers one actual Expand action", 1, expanders.size)
        assertTrue("the actual Expand handle is visible", expanders.single().boundsInWindow.height > 1f)

        // A half-open anchored sheet can have zero inner scroll range because its content
        // fits its fully expanded measured height. Use its shipping accessibility action
        // before asking those real scroll ancestors to reveal the original RPE control.
        compose.onNode(expand, useUnmergedTree = true).performSemanticsAction(SemanticsActions.Expand) {
            assertTrue("the actual sheet accepts its own Expand action", it())
        }
        host.drain()
        compose.awaitThat("the actual correction sheet settles fully expanded", {
            compose.onNode(sheet, useUnmergedTree = true).fetchSemanticsNode().let {
                "origin=${it.positionInWindow}; size=${it.size}; clip=${it.boundsInWindow}"
            }
        }) {
            val expanded = compose.onNode(sheet, useUnmergedTree = true).fetchSemanticsNode()
            compose.onAllNodes(expand, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() &&
                expanded.positionInWindow.y < initialTop - 1f &&
                expanded.boundsInWindow.height >= expanded.size.height - 1f
        }
        val expanded = compose.onNode(sheet, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("the expanded sheet moved above its original half-open anchor", expanded.positionInWindow.y < initialTop - 1f)
        assertTrue("the expanded sheet fits its actual window", expanded.boundsInWindow.height >= expanded.size.height - 1f)
        host.capture("correction-expanded-anchor-$setId")
        assertInventoryUnchanged("correction-expanded-anchor-$setId")
    }

    private fun traverseActualList() {
        repeat(30) { frame ->
            host.drain()
            val rows = compose.onAllNodes(SemanticsMatcher("actual recorded row tags") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("session-detail-set-") == true
            }, useUnmergedTree = true).fetchSemanticsNodes()
            val counts = rows.groupingBy { it.config[SemanticsProperties.TestTag] }.eachCount()
            assertTrue("actual list never composes two logical rows for one saved ID: $counts", counts.values.all { it == 1 })
            host.capture("actual-list-frame-$frame")
            val list = compose.onNodeWithTag(SessionDetailTestTags.CONTENT).fetchSemanticsNode()
            val range = checkNotNull(list.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))
            if (range.value() >= range.maxValue() - .001f) return
            val height = list.boundsInWindow.height
            assertTrue("actual Detail leaves a usable scroll viewport", height > 1f)
            compose.runOnUiThread {
                assertTrue(
                    "actual list accepts a forward scroll before its end",
                    checkNotNull(list.config.getOrNull(SemanticsActions.ScrollBy)?.action)(0f, height * .75f),
                )
            }
        }
        throw AssertionError("the actual restored Detail did not reach its end after 30 bounded scrolls")
    }

    private fun assertInventoryUnchanged(stage: String) {
        val after = inventory()
        writeInventory(stage, after)
        assertInventorySafety(checkNotNull(restoredInventory), after, stage)
    }

    @Suppress("UNCHECKED_CAST")
    private fun rawOriginal(value: List<Any?>): SetLogEntity =
        (value[2] as List<SetLogEntity>).single { it.id == SET_ID }

    @Suppress("UNCHECKED_CAST")
    private fun assertInventorySafety(original: List<Any?>, after: List<Any?>, stage: String) {
        val beforeSet = rawOriginal(original)
        val afterSet = rawOriginal(after)
        assertTrue("only original or explicitly attempted effort is permitted at $stage", afterSet.rpe in permittedOriginalRpe)
        val normalized = after.toMutableList()
        normalized[2] = (after[2] as List<SetLogEntity>).map {
            if (it.id == SET_ID) it.copy(rpe = beforeSet.rpe) else it
        }
        normalized[8] = (after[8] as BackupDocument).let { snapshot ->
            snapshot.copy(setLogs = snapshot.setLogs.map {
                if (it.id == SET_ID) it.copy(rpe = beforeSet.rpe) else it
            })
        }
        assertEquals("every other restored field/row/preference remains exact at $stage", original, normalized)
    }
    /**
     * All raw workout/catalog/routine rows, including unfinished rows that export excludes,
     * plus the complete exportable Room tables and restored preference payload. Only the
     * generated export time is normalized; all saved/captured timestamps remain literal.
     */
    private fun inventory(): List<Any?> = runBlocking {
        listOf(
            deps.database.workoutDao().getAllSessions().sortedBy { it.id },
            deps.database.workoutDao().getAllSessionExercises().sortedBy { it.id },
            deps.database.workoutDao().getAllSets().sortedBy { it.id },
            deps.database.exerciseDao().getAll().sortedBy { it.id },
            deps.database.routineDao().getAllRoutines().sortedBy { it.id },
            deps.database.routineDao().getAllRoutineExercises().sortedBy { it.id },
            deps.database.catalogDao().getAllCredits().sortedWith(compareBy({ it.exerciseId }, { it.muscleKey })),
            deps.database.catalogDao().getSeedMeta(),
            deps.localBackupRepository.createSnapshot().copy(exportedAt = EXPORTED_AT),
            deps.workoutRepository.getInProgress(),
            deps.restoreJournal.isOpen(),
        )
    }

    private fun writeInventory(stage: String, value: List<Any?>) {
        check(artifactDirectory.isDirectory || artifactDirectory.mkdirs())
        File(artifactDirectory, "$fixtureName-$stage-inventory.txt").writeText(
            "fixture=$fixtureName\nrunId=$artifactRunId\n" +
                value.mapIndexed { index, rows -> "$index: $rows" }.joinToString("\n"),
        )
    }

    private fun holdFixture(planned: Boolean, originalReps: Int = 0): RestoredFixture {
        val plan = if (planned) BackupSessionExercise(
            "hold-matching-plan", SESSION_ID, HOLD_ID, 0, 1, 1, 12.5, 60, 45, null,
        ) else BackupSessionExercise("hold-unrelated-plan", SESSION_ID, INTACT_ID, 0, 3, 5, 100.0, 90)
        return RestoredFixture(
            name = if (planned) "planned-hold-control" else if (originalReps < 0) "saved-only-negative-rep-hold" else "saved-only-hold",
            document = BackupDocument(
                version = 5,
                exportedAt = EXPORTED_AT,
                preferences = BackupPreferences(weightUnit = "kg", onboardingComplete = true),
                exercises = listOf(exercise(INTACT_ID, "Untouched prescribed lift"), exercise(HOLD_ID, "Original weighted static hold")),
                routines = emptyList(), routineExercises = emptyList(),
                sessions = listOf(BackupSession(SESSION_ID, null, "Restored typed hold safety", STAMP,
                    "Keep original hold type and all captured values", 10, STAMP, STAMP + 600_000L)),
                sessionExercises = listOf(plan),
                setLogs = listOf(BackupSetLog(
                    id = SET_ID, sessionId = SESSION_ID, exerciseId = HOLD_ID, setNumber = 1,
                    weightKg = 12.5, reps = originalReps, rpe = null, isWarmup = false,
                    completedAt = STAMP + 1_000L, durationSeconds = 45,
                )),
            ),
            prescriptions = listOf(PrescriptionWords(plan.id, plan.exerciseId,
                if (planned) "1 × 45s" else "3 × 5", if (planned) "12.5 kg" else "100 kg", if (planned) "1:00" else "1:30")),
            planned = planned,
            originalReps = originalReps,
        )
    }

    private fun exercise(id: String, name: String) = BackupExercise(
        id = id, name = name, muscleGroup = "Quads", notes = "", isCustom = true,
        equipment = "OTHER", loadType = "EXTERNAL", movementKey = if (id == HOLD_ID) "hold" else null,
    )
    private data class RestoredFixture(
        val name: String,
        val document: BackupDocument,
        val prescriptions: List<PrescriptionWords>,
        val planned: Boolean,
        val originalReps: Int,
    )

    private data class PrescriptionWords(
        val id: String,
        val exerciseId: String,
        val work: String,
        val load: String,
        val rest: String,
        val originalPosition: Int = 1,
    )

    private companion object {
        const val SESSION_ID = "hold-safety-session"
        const val SET_ID = "hold-safety-original-set"
        const val INTACT_ID = "hold-intact-lift"
        const val HOLD_ID = "hold-original-lift"

        const val STAMP = 1_790_856_000_000L
        const val EXPORTED_AT = "2026-10-09T00:00:00Z"
    }
}
