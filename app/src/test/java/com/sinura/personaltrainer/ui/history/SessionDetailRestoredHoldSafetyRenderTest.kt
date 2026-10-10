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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
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
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.summary.SavedWorkRenderHost
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.holdingTheClock
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
 * Independent cases pass BackupService.prepareRestore/commitRestore, including the production
 * decoder/validator, verified safety copy, LocalBackupRepository replacement and journal
 * epilogue over real isolated Room. The shipping Detail VM and screen supply the rows and
 * Edit sheet and Save button. Each test restores fresh dependencies; an untouched-save
 * refusal cannot prevent a separate duration/effort-adjustment counter from running.
 * This is a native-graphics component counter, not AppNav, device touch feel or TalkBack.
 *
 * An exception is a failure. Native frames/semantics are retained when rendering allows it;
 * the complete post-restore inventory is checked again in teardown even if a counter fails.
 * Only the complete original row or exact expressly requested row may differ from that baseline.
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
    private val permittedInventories = mutableListOf<List<Any?>>()
    private val failSessionObservation = MutableStateFlow(false)
    private val sessionObservationCalls = AtomicInteger()
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
            workoutDaoDecorator = { real -> object : WorkoutDao by real {
                override fun observeSession(id: String): Flow<SessionWithDetails?> {
                    if (id == SESSION_ID) sessionObservationCalls.incrementAndGet()
                    return combine(real.observeSession(id), failSessionObservation) { row, failed ->
                        if (failed && id == SESSION_ID) error("controlled Detail session observation failure")
                        row
                    }
                }
            } },
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

    // The old rep-nudge UI counters deliberately become time/effort controls. Direct
    // repository rep-draft guards remain; a removed Rep control is not an attempted edit.
    @Test fun restoredPlannedHoldUntouchedSavePreservesExactTimedOriginal() =
        verify(holdFixture(planned = true))
    @Test fun restoredSavedOnlyHoldUntouchedSavePreservesExactTimedOriginalAndTruthfulOutcome() =
        verify(holdFixture(planned = false))
    @Test fun restoredSavedOnlyHoldEffortSaveCannotConvertOriginalToRepWork() =
        verify(holdFixture(planned = false), Draft.EFFORT)
    @Test fun restoredPlannedHoldEffortSaveKeepsOriginalTimedType() =
        verify(holdFixture(planned = true), Draft.EFFORT)
    @Test fun restoredSavedOnlyNegativeRepHoldUntouchedSavePreservesExactOriginalRepresentation() =
        verify(holdFixture(planned = false, originalReps = -3))
    @Test fun restoredSavedOnlyHoldKeypadCancelWithoutEffortKeepsExactTimedOriginal() =
        verify(holdFixture(planned = false), Draft.KEYPAD_CANCEL)

    @Test fun anActualPlannedHoldTimeNudgeSavesFortyFiveAsFiftySeconds() =
        verify(holdFixture(planned = true), Draft.TIME)
    @Test fun anActualSavedOnlyNegativeHoldTimeNudgePreservesItsLiteralRepRepresentation() =
        verify(holdFixture(planned = false, originalReps = -3), Draft.TIME)
    @Test fun capturedOneSecondSavesUntouched() =
        verify(holdFixture(planned = false, originalSeconds = 1))
    @Test fun capturedTwoSecondsSaveUntouched() =
        verify(holdFixture(planned = false, originalSeconds = 2))
    @Test fun capturedThreeSecondsSaveUntouched() =
        verify(holdFixture(planned = false, originalSeconds = 3))
    @Test fun capturedFourSecondsSaveUntouched() =
        verify(holdFixture(planned = false, originalSeconds = 4))
    @Test fun capturedSecondsAboveTheLiveTargetRangeSaveUntouched() =
        verify(holdFixture(planned = false, originalSeconds = 1801))
    @Test fun capturedMaximumSecondsAndItsPlusNudgeNeverOverflow() =
        verify(holdFixture(planned = false, originalSeconds = Int.MAX_VALUE), Draft.OVERFLOW)
    @Test fun decrementingAShortCapturedTimeStopsAtOneSecond() =
        verify(holdFixture(planned = false, originalSeconds = 4), Draft.SHORT_DECREMENT)
    @Test fun invalidTimeTextNeverConfirmsOrMutatesTheRestoredOriginal() =
        verify(holdFixture(planned = false), Draft.INVALID_TIME)
    @Test fun cancellingAndDismissingADurationDraftKeepsTheRestoredOriginal() =
        verify(holdFixture(planned = false), Draft.CANCEL_DISMISS)
    @Test fun aWarmupCorrectionChangesOnlyTheRequestedFlag() =
        verify(holdFixture(planned = false), Draft.WARMUP)
    @Test fun positiveRepsUnderPlannedHoldMetadataKeepTheirRepsAndStopwatchOnActualEffortSave() =
        verify(holdFixture(planned = true, originalReps = 8), Draft.EFFORT)
    @Test fun savedOnlyStopwatchStrengthKeepsItsRepsAndDurationOnActualEffortSave() =
        verify(holdFixture(planned = false, originalReps = 8), Draft.EFFORT)
    @Test fun unknownLoadZeroWeightIsAnExplicitRefusalWithNoInventoryChange() =
        verify(holdFixture(planned = false, originalWeight = 0.0), refusal = SetLogRules.ZERO_WORKING_WEIGHT)
    @Test fun stopwatchStrengthStillRequiresEffortThroughItsActualEditor() =
        verify(holdFixture(planned = false, originalReps = 8), Draft.CLEAR_EFFORT, SetLogRules.EFFORT_MISSING)
    @Test fun historicalPositiveRepsUnderHoldMetadataNudgeAndSaveOneHundredAndTwoExactly() =
        verify(holdFixture(planned = true, originalReps = 101), Draft.REP_NUDGE)

    @Test fun removingAnAddTargetsActualGraphClosesAndClearsTheStaleSheet() =
        host.evidence("restored-detail-add-target-graph-disappears") {
            val model = openFixture(holdFixture(planned = true, originalReps = 8))
            host.touch(host.tag(SetEditTestTags.CANCEL))
            host.touch(host.tag(FilledLiftCardTags.addSet(HOLD_ID)))
            assertExpandedSheet()
            host.readable(compose.onNode(matcher = hasText("Add set") and hasAnyAncestor(actualDialog()), useUnmergedTree = true), "Add set")
            authorAddDraft()
            val before = checkNotNull(restoredInventory)
            assertEquals("uncommitted Add changes no inventory part", before, inventory())
            @Suppress("UNCHECKED_CAST")
            val originalPlans = before[1] as List<SessionExerciseEntity>
            val originalPlan = originalPlans.single { it.sessionId == SESSION_ID && it.exerciseId == HOLD_ID }
            val withoutSet = before.toMutableList()
            @Suppress("UNCHECKED_CAST")
            val originalSets = before[2] as List<SetLogEntity>
            withoutSet[2] = originalSets.filterNot { it.id == SET_ID }
            withoutSet[8] = (before[8] as BackupDocument).let { snapshot ->
                snapshot.copy(setLogs = snapshot.setLogs.filterNot { it.id == SET_ID })
            }
            permittedInventories += withoutSet.toList()
            val withoutGraph = withoutSet.toMutableList()
            withoutGraph[1] = originalPlans.filterNot { it.id == originalPlan.id }
            withoutGraph[8] = (withoutSet[8] as BackupDocument).let { snapshot ->
                snapshot.copy(sessionExercises = snapshot.sessionExercises.filterNot { it.id == originalPlan.id })
            }
            permittedInventories += withoutGraph.toList()
            host.capture("actual-add-draft-before-external-graph-removal")
            runBlocking {
                deps.database.workoutDao().deleteSet(SET_ID)
                deps.database.workoutDao().deleteSessionExercise(originalPlan.id)
            }
            compose.awaitThat("shipping Detail receives the graph without the selected Add target", { model.uiState.value }) {
                !model.uiState.value.isLoading && !model.uiState.value.failed &&
                    model.uiState.value.session?.filledLifts()?.none { it.exercise.id == HOLD_ID } == true
            }
            host.drain()
            compose.onNodeWithTag(SetEditTestTags.CONTENT).assertDoesNotExist()
            assertEquals("external removal changes only its exact set and prescription", withoutGraph, inventory())
            writeInventory("actual-add-target-graph-removed", inventory())
            host.capture("actual-add-selection-cleared")
            runBlocking {
                deps.database.workoutDao().insertSessionExercises(listOf(originalPlan))
                deps.database.workoutDao().insertSet(rawOriginal(before))
            }
            compose.awaitThat("shipping Detail receives the restored exact graph", { model.uiState.value }) {
                model.uiState.value.session?.filledLifts()?.any { it.exercise.id == HOLD_ID } == true
            }
            host.drain()
            compose.onNodeWithTag(SetEditTestTags.CONTENT).assertDoesNotExist()
            host.action(host.tag(FilledLiftCardTags.addSet(HOLD_ID)))
            assertEquals("returning graph does not resurrect the cleared draft or change any inventory part", before, inventory())
            host.capture("actual-returning-graph-has-no-stale-add-sheet")
            host.touch(host.tag(FilledLiftCardTags.addSet(HOLD_ID)))
            assertExpandedSheet()
            assertAddDraft(weight = "12.5", reps = "8", effortSelected = false, warmupSelected = false)
            assertEquals("reopening the exact returning owner starts a fresh draft without an inventory write", before, inventory())
            host.capture("actual-returning-owner-has-fresh-add-draft")
            host.touch(host.tag(SetEditTestTags.CANCEL))
            compose.onNodeWithTag(SetEditTestTags.CONTENT).assertDoesNotExist()
            assertEquals("cancelling the returning owner's fresh Add leaves all eleven parts exact", before, inventory())
        }

    @Test fun failedDetailReadAndActualRetryRetainTheSameAddSelectionWithoutAWrite() =
        host.evidence("restored-detail-add-selection-failed-read-retry") {
            val model = openFixture(holdFixture(planned = true, originalReps = 8))
            host.touch(host.tag(SetEditTestTags.CANCEL))
            host.touch(host.tag(FilledLiftCardTags.addSet(HOLD_ID)))
            assertExpandedSheet()
            host.readable(compose.onNode(matcher = hasText("Add set") and hasAnyAncestor(actualDialog()), useUnmergedTree = true), "Add set")
            authorAddDraft()
            val before = checkNotNull(restoredInventory)
            assertEquals("the manually authored Add draft changes no restored inventory part", before, inventory())
            host.capture("actual-authored-add-draft-before-read-failure")
            val readsBefore = sessionObservationCalls.get()
            failSessionObservation.value = true
            compose.awaitThat("the actual observed read fails explicitly", { model.uiState.value }) {
                model.uiState.value.failed && !model.uiState.value.isLoading
            }
            host.drain()
            assertFalse("unavailable is not a missing session", model.uiState.value.missing)
            compose.onNodeWithTag(SetEditTestTags.CONTENT).assertDoesNotExist()
            assertEquals("a failed read changes no restored inventory part", before, inventory())
            host.capture("actual-failed-read-before-retry")
            failSessionObservation.value = false
            host.touch(host.tag(SessionDetailTestTags.RETRY))
            compose.awaitThat("actual Retry reloads the exact session", { model.uiState.value }) {
                !model.uiState.value.failed && !model.uiState.value.isLoading && model.uiState.value.session?.id == SESSION_ID
            }
            host.drain()
            assertTrue("pointer Retry starts a fresh observation", sessionObservationCalls.get() > readsBefore)
            // No second Add tap: recovery must preserve the selection made before failure.
            assertExpandedSheet()
            host.readable(compose.onNode(matcher = hasText("Add set") and hasAnyAncestor(actualDialog()), useUnmergedTree = true), "Add set")
            host.capture("actual-retry-draft-before-retention-check")
            assertAddDraft(weight = "17.5", reps = "9", effortSelected = true, warmupSelected = true)
            assertEquals("Retry and recovered selection change no inventory part", before, inventory())
            host.capture("actual-retry-retains-original-add-selection")
            host.touch(host.tag(SetEditTestTags.CANCEL))
            compose.onNodeWithTag(SetEditTestTags.CONTENT).assertDoesNotExist()
            assertEquals("cancelling the recovered Add leaves all eleven parts exact", before, inventory())
            host.touch(host.tag(FilledLiftCardTags.addSet(HOLD_ID)))
            assertExpandedSheet()
            assertAddDraft(weight = "12.5", reps = "8", effortSelected = false, warmupSelected = false)
            assertEquals("explicit Cancel discards the recovered draft before reopening its exact owner", before, inventory())
            host.capture("actual-cancelled-owner-reopens-with-fresh-add-draft")
            host.touch(host.tag(SetEditTestTags.CANCEL))
            compose.onNodeWithTag(SetEditTestTags.CONTENT).assertDoesNotExist()
            assertEquals("cancelling the fresh Add still leaves all eleven parts exact", before, inventory())
        }

    @Test fun actualDeleteAndUndoRestoreTheExactTimedRowAndEveryInventoryPart() =
        host.evidence("restored-hold-delete-undo") {
            val fixture = holdFixture(planned = false, originalReps = -3)
            val model = openFixture(fixture)
            val before = checkNotNull(restoredInventory)
            val without = before.toMutableList()
            @Suppress("UNCHECKED_CAST")
            val rows = before[2] as List<SetLogEntity>
            without[2] = rows.filterNot { it.id == SET_ID }
            without[8] = (before[8] as BackupDocument).let { it.copy(setLogs = it.setLogs.filterNot { row -> row.id == SET_ID }) }
            permittedInventories += without.toList()
            completedAction(model) { host.touch(host.tag(SetEditTestTags.DELETE)) }
            compose.awaitThat("actual Delete removes only the original row", { inventory() }) {
                @Suppress("UNCHECKED_CAST")
                (inventory()[2] as List<SetLogEntity>).none { it.id == SET_ID }
            }
            assertEquals("Delete leaves every other inventory part exact", without, inventory())
            host.readable(host.words("Set deleted · 12.5 kg × 45s"), "Set deleted · 12.5 kg × 45s")
            host.capture("actual-delete-before-undo")
            completedAction(model) { host.touch(compose.onNode(hasText("Undo"))) }
            compose.awaitThat("actual Undo restores the exact complete inventory", { inventory() }) { inventory() == before }
            host.capture("actual-undo-restored-original")
            assertEquals(before, inventory())
        }

    private fun verify(fixture: RestoredFixture, draft: Draft = Draft.NONE, refusal: String? = null) =
        host.evidence("restored-" + fixture.name + "-" + draft.name.lowercase()) {
            val model = openFixture(fixture)
            val before = checkNotNull(restoredInventory)
            val original = rawOriginal(before)
            val desired = applyDraft(original, draft)
            assertEquals("draft gestures have not written any inventory part", before, inventory())
            if (refusal == null) permittedInventories += expectedInventory(before, desired)
            host.capture("actual-editor-before-save")
            completedAction(model) { host.touch(compose.onNode(hasText("Save") and hasAnyAncestor(actualDialog()))) }
            compose.awaitThat("actual Save closes the original editor", { model.error.value }) {
                compose.onAllNodes(hasTestTag(SetEditTestTags.CONTENT)).fetchSemanticsNodes().isEmpty()
            }
            val after = inventory()
            writeInventory("actual-post-save-before-safety-assertions", after)
            val stored = rawOriginal(after)
            lastSaveOutcome = model.error.value?.let { "explicit-refusal: $it" } ?: "completed-without-refusal"
            File(artifactDirectory, fixtureName + "-actual-save-outcome.txt").writeText(
                "fixture=$fixtureName\ndraft=$draft\noutcome=$lastSaveOutcome\nrequested=$desired\nactual=$stored\n",
            )
            host.capture("actual-save-outcome-before-safety-assertions")
            if (refusal == null) {
                assertNull("the valid actual correction succeeds", model.error.value)
                assertEquals("actual Save writes exactly the requested original row", desired, stored)
                assertEquals("all eleven inventory parts match only the requested correction", expectedInventory(before, desired), after)
            } else {
                assertEquals("the refusal names the unchanged rule", refusal, model.error.value)
                host.readable(host.words(refusal), refusal)
                assertEquals("a refused correction changes no inventory part", before, after)
            }
            compose.awaitThat("actual Detail reflects the completed stored row", { model.uiState.value }) {
                model.uiState.value.session?.sets?.singleOrNull()?.let {
                    it.id == stored.id && it.reps == stored.reps && it.rpe == stored.rpe &&
                        it.durationSeconds == stored.durationSeconds && it.weightKg == stored.weightKg && it.isWarmup == stored.isWarmup
                } == true
            }
            assertSavedType(checkNotNull(model.uiState.value.session), stored)
            val row = FilledLiftCardTags.setRow(SET_ID)
            host.readable(host.words(workWords(stored), row), workWords(stored))
            host.capture("preserved-row-after-truthful-outcome")
            assertInventorySafety(before, inventory(), "complete-counter")
        }

    private fun openFixture(fixture: RestoredFixture): SessionDetailViewModel {
        fixtureName = fixture.name
        host.font = 1f
        val configuration = RuntimeEnvironment.getApplication().resources.configuration
        assertEquals("actual native font", 1f, configuration.fontScale, .001f)
        assertEquals("actual resource layout", android.view.View.LAYOUT_DIRECTION_LTR, configuration.layoutDirection)
        restore(fixture.document, fixture.originalReps)
        mount()
        val model = checkNotNull(detail)
        compose.awaitThat("actual Detail loads the supported restored original", { model.uiState.value }) {
            !model.uiState.value.isLoading && model.uiState.value.session?.id == SESSION_ID
        }
        assertEquals(originalSession, model.uiState.value.session)
        val loaded = checkNotNull(model.uiState.value.session)
        assertTrue("the restored fixture retains a nonempty prescription graph", loaded.exercises.isNotEmpty())
        assertEquals("the positive-rep counter uses actual matching hold metadata", fixture.planned,
            loaded.exercises.any { it.exercise.id == HOLD_ID })
        if (fixture.planned) {
            val matching = loaded.exercises.single { it.exercise.id == HOLD_ID }
            assertEquals("hold", matching.exercise.movementKey)
            assertEquals(45, matching.targetSeconds)
        }
        fixture.prescriptions.forEach { plan ->
            val under = FilledLiftCardTags.plannedEntry(plan.id)
            for (value in listOf(plan.work, plan.load, plan.rest)) host.readable(host.words(value, under), value)
        }
        assertSavedType(checkNotNull(model.uiState.value.session), rawOriginal(checkNotNull(restoredInventory)))
        traverseActualList()
        openOriginalEditor()
        assertInventoryUnchanged("original-editor-open")
        return model
    }

    private fun openOriginalEditor() {
        val row = FilledLiftCardTags.setRow(SET_ID)
        val original = rawOriginal(checkNotNull(restoredInventory))
        host.readable(host.words(workWords(original), row), workWords(original))
        assertEquals(1, compose.onAllNodes(hasTestTag(row), useUnmergedTree = true).fetchSemanticsNodes().size)
        val edit = hasTestTag(SessionDetailTestTags.EDIT_SET) and hasAnyAncestor(hasTestTag(row))
        assertEquals(1, compose.onAllNodes(edit).fetchSemanticsNodes().size)
        host.touch(compose.onNode(edit))
        assertExpandedSheet()
        host.readable(host.words("Edit set 1"), "Edit set 1")
        val pureTimed = original.reps < 1 && (original.durationSeconds ?: 0) > 0
        if (pureTimed) {
            compose.onNodeWithTag("Type a rep count").assertDoesNotExist()
            host.readable(host.words(checkNotNull(original.durationSeconds).toString(), TYPE_TIME), original.durationSeconds.toString(), singleLine = true)
            host.readable(host.words("s", TYPE_TIME), "s", singleLine = true)
        } else {
            compose.onNodeWithTag(TYPE_TIME).assertDoesNotExist()
            compose.onNodeWithTag("Type a rep count").assertContentDescriptionEquals("reps " + original.reps)
            host.readable(host.words(original.reps.toString(), "Type a rep count"), original.reps.toString(), singleLine = true)
        }
        host.readable(host.words(WeightConverter.formatDisplayNumber(original.weightKg), "Type a weight"),
            WeightConverter.formatDisplayNumber(original.weightKg), singleLine = true)
        host.capture("original-expanded-editor")
    }

    private fun applyDraft(original: SetLogEntity, draft: Draft): SetLogEntity {
        val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        fun effort(value: Int) = compose.onNode(hasText(value.toString()) and checkbox and hasAnyAncestor(actualDialog()))
        return when (draft) {
            Draft.NONE -> original
            Draft.EFFORT -> {
                val value = if (original.rpe == 8) 9 else 8
                host.touch(effort(value))
                effort(value).assertIsOn()
                original.copy(rpe = value)
            }
            Draft.CLEAR_EFFORT -> {
                host.touch(effort(checkNotNull(original.rpe)))
                effort(checkNotNull(original.rpe)).assertIsOff()
                original.copy(rpe = null)
            }
            Draft.REP_NUDGE -> {
                assertEquals("the durable counter pins the accepted historical value", 101, original.reps)
                compose.onNodeWithTag(TYPE_TIME).assertDoesNotExist()
                host.touch(compose.onNode(hasText("+1") and hasAnyAncestor(actualDialog())))
                host.readable(host.words("102", "Type a rep count"), "102", singleLine = true)
                original.copy(reps = 102)
            }
            Draft.TIME, Draft.OVERFLOW, Draft.SHORT_DECREMENT -> {
                val label = if (draft == Draft.SHORT_DECREMENT) "−5" else "+5"
                host.touch(compose.onNode(hasText(label) and hasAnyAncestor(actualDialog())))
                val target = (checkNotNull(original.durationSeconds).toLong() +
                    if (draft == Draft.SHORT_DECREMENT) -5L else 5L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
                host.readable(host.words(target.toString(), TYPE_TIME), target.toString(), singleLine = true)
                original.copy(durationSeconds = target)
            }
            Draft.KEYPAD_CANCEL -> {
                compose.holdingTheClock {
                    host.touch(host.tag(TYPE_TIME))
                    compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("90")
                    host.drain()
                    host.touch(keypadCancel())
                }
                host.readable(host.words(checkNotNull(original.durationSeconds).toString(), TYPE_TIME), original.durationSeconds.toString())
                original
            }
            Draft.INVALID_TIME -> {
                compose.holdingTheClock {
                    host.touch(host.tag(TYPE_TIME))
                    host.readable(host.words("Time"), "Time")
                    for (text in listOf("", "0", "-1", "1.5", "1,5", "00:45", "+1", "1e3", "abc", "2147483648")) {
                        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement(text)
                        host.drain()
                        host.reach(host.tag(NumberEntryTags.CONFIRM)).assertIsNotEnabled()
                        assertInventoryUnchanged("invalid-time-" + text.ifEmpty { "blank" })
                        host.capture("invalid-time-" + text.ifEmpty { "blank" }.replace(':', '_'))
                    }
                    host.touch(keypadCancel())
                }
                original
            }
            Draft.CANCEL_DISMISS -> {
                compose.holdingTheClock {
                    host.touch(host.tag(TYPE_TIME))
                    compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("90")
                    host.drain()
                    host.touch(host.tag(NumberEntryTags.CONFIRM))
                }
                host.touch(host.tag(SetEditTestTags.CANCEL))
                host.drain()
                assertInventoryUnchanged("explicit-cancel-after-duration-draft")
                openOriginalEditor()
                val dismiss = SemanticsMatcher("the actual bottom sheet offers Dismiss") {
                    it.config.getOrNull(SemanticsActions.Dismiss)?.action != null
                }
                compose.onNode(dismiss, useUnmergedTree = true).performSemanticsAction(SemanticsActions.Dismiss) {
                    assertTrue("the shipping sheet accepts Dismiss", it())
                }
                host.drain()
                assertInventoryUnchanged("dismiss-original-editor")
                openOriginalEditor()
                original
            }
            Draft.WARMUP -> {
                host.touch(compose.onNode(hasText("Warm-up") and checkbox and hasAnyAncestor(actualDialog())))
                original.copy(isWarmup = true)
            }
        }
    }

    private fun authorAddDraft() {
        assertAddDraft(weight = "12.5", reps = "8", effortSelected = false, warmupSelected = false)
        compose.holdingTheClock {
            host.touch(host.tag("Type a weight"))
            compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("17.5")
            host.drain()
            host.touch(host.tag(NumberEntryTags.CONFIRM))
        }
        host.touch(compose.onNode(hasText("+1") and hasAnyAncestor(actualDialog())))
        host.touch(addChip("9"))
        host.touch(addChip("Warm-up"))
        assertAddDraft(weight = "17.5", reps = "9", effortSelected = true, warmupSelected = true)
    }

    private fun assertAddDraft(weight: String, reps: String, effortSelected: Boolean, warmupSelected: Boolean) {
        host.readable(compose.onNode(matcher = hasText("Add set") and hasAnyAncestor(actualDialog()), useUnmergedTree = true), "Add set")
        host.readable(host.tag(value = SetEditTestTags.NAME, unmerged = true), "Original weighted static hold")
        host.tag("set-edit-art-$HOLD_ID").assertExists()
        host.tag("Type a rep count").assertContentDescriptionEquals("reps $reps")
        host.readable(host.words(reps, "Type a rep count"), reps, true)
        host.tag("Type a weight").assertContentDescriptionEquals("Weight $weight kg")
        host.readable(host.words(weight, "Type a weight"), weight, true)
        host.readable(host.words("kg", "Type a weight"), "kg", true)
        if (effortSelected) addChip("9").assertIsOn() else addChip("9").assertIsOff()
        addChip("8").assertIsOff()
        if (warmupSelected) addChip("Warm-up").assertIsOn() else addChip("Warm-up").assertIsOff()
    }

    private fun addChip(label: String) = compose.onNode(
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and hasAnyAncestor(actualDialog()),
    )

    private fun actualDialog() = SemanticsMatcher("the actual editor dialog") {
        it.config.getOrNull(SemanticsProperties.IsDialog) != null
    }

    private fun keypadCancel() = compose.onNode(hasText("Cancel") and hasAnyAncestor(
        actualDialog() and hasAnyDescendant(hasTestTag(NumberEntryTags.FIELD)),
    ))

    private fun assertExpandedSheet() {
        val pane = SemanticsMatcher("the actual modal sheet pane") {
            it.config.getOrNull(SemanticsProperties.PaneTitle) != null
        } and hasAnyAncestor(actualDialog())
        compose.awaitThat("the shipping sheet settles at its full default anchor", {
            compose.onAllNodes(pane, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInWindow }
        }) {
            compose.onAllNodes(pane, useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()?.let {
                it.boundsInWindow.height >= it.size.height - 1f
            } == true
        }
        assertTrue("the full sheet offers no Expand workaround", compose.onAllNodes(SemanticsMatcher("Expand") {
            it.config.getOrNull(SemanticsActions.Expand)?.action != null
        }, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    private fun completedAction(model: SessionDetailViewModel, action: () -> Unit) {
        val scope = checkNotNull(model.viewModelScope.coroutineContext[Job])
        val priorJobs = scope.children.toSet()
        action()
        compose.awaitThat("the actual pointer action's VM jobs complete", {
            scope.children.filter { it !in priorJobs }.map { "$it active=" + it.isActive }
        }) { scope.children.none { it !in priorJobs && it.isActive } }
        host.drain()
    }

    private fun assertSavedType(session: WorkoutSession, expected: SetLogEntity) {
        val set = session.sets.single()
        assertEquals(expected.id, set.id)
        assertEquals(expected.sessionId, set.sessionId)
        assertEquals(expected.exerciseId, set.exerciseId)
        assertEquals("original rep representation remains literal", expected.reps, set.reps)
        assertEquals(expected.durationSeconds, set.durationSeconds)
        assertEquals(expected.weightKg, set.weightKg, .0001)
        assertEquals(expected.completedAt, set.completedAt)
        assertEquals(expected.setNumber, set.setNumber)
        assertEquals(expected.isWarmup, set.isWarmup)
        val volume = if (expected.isWarmup || expected.reps < 1) 0.0 else expected.weightKg * expected.reps
        assertEquals("stopwatch strength remains rep work and timed originals remain time", volume, session.work().volumeKg, .0001)
        assertEquals(0, session.work().bodyweightReps)
        assertEquals(if (expected.isWarmup) 0 else 1, session.workingSetCount())
        assertEquals(mapOf(SET_ID to 1), session.filledLifts().flatMap { it.sets }.groupingBy { it.id }.eachCount())
    }

    private fun workWords(set: SetLogEntity): String =
        (if (set.weightKg == 0.0) SetCopy.NO_WEIGHT else WeightConverter.formatDisplayNumber(set.weightKg) + " kg") + " × " +
            if (set.reps < 1 && (set.durationSeconds ?: 0) > 0) set.durationSeconds.toString() + "s"
            else set.reps.toString() + (set.durationSeconds?.takeIf { it > 0 }?.let { " · ${it}s" } ?: "")

    @Suppress("UNCHECKED_CAST")
    private fun expectedInventory(before: List<Any?>, expected: SetLogEntity): List<Any?> {
        val wanted = before.toMutableList()
        wanted[2] = (before[2] as List<SetLogEntity>).map { if (it.id == SET_ID) expected else it }
        wanted[8] = (before[8] as BackupDocument).let { snapshot ->
            snapshot.copy(setLogs = snapshot.setLogs.map {
                if (it.id == SET_ID) it.copy(weightKg = expected.weightKg, reps = expected.reps, rpe = expected.rpe,
                    isWarmup = expected.isWarmup, durationSeconds = expected.durationSeconds) else it
            })
        }
        return wanted
    }

    private enum class Draft { NONE, EFFORT, TIME, KEYPAD_CANCEL, INVALID_TIME, CANCEL_DISMISS, WARMUP, CLEAR_EFFORT, OVERFLOW, SHORT_DECREMENT, REP_NUDGE }

    private fun restore(document: BackupDocument, originalReps: Int) = runBlocking {
        check(artifactDirectory.isDirectory || artifactDirectory.mkdirs())
        val json = BackupJson.encode(document)
        File(artifactDirectory, "$fixtureName-incoming.json").writeText(json)
        val decoded = BackupJson.decode(json)
        assertEquals("the accepted fixture uses the supported v5 format", 5, decoded.version)
        assertEquals("the fixture pins the literal original timed rep representation", originalReps, decoded.setLogs.single { it.id == SET_ID }.reps)
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
        permittedInventories += checkNotNull(restoredInventory)
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
        assertEquals(decoded.sessionExercises.filter { it.sessionId == SESSION_ID }.map { it.id }, checkNotNull(originalSession).exercises.map { it.id })
        val saved = checkNotNull(originalSession).sets.single()
        assertEquals(SET_ID, saved.id)
        assertEquals(SESSION_ID, saved.sessionId)
        assertEquals(decoded.setLogs.single { it.id == SET_ID }.exerciseId, saved.exerciseId)
        assertEquals(1, saved.setNumber)
        assertEquals(decoded.setLogs.single { it.id == SET_ID }.weightKg, saved.weightKg, .0001)
        assertEquals(originalReps, saved.reps)
        assertEquals(decoded.setLogs.single { it.id == SET_ID }.rpe, saved.rpe)
        assertFalse(saved.isWarmup)
        assertEquals(STAMP + 1_000L, saved.completedAt)
        assertEquals(decoded.setLogs.single { it.id == SET_ID }.durationSeconds, saved.durationSeconds)
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

    private fun assertInventorySafety(original: List<Any?>, after: List<Any?>, stage: String) {
        assertEquals("the complete comparison has eleven inventory parts", 11, original.size)
        assertTrue("only the exact baseline or expressly requested full inventory is permitted at $stage",
            permittedInventories.any { it == after })
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

    private fun holdFixture(
        planned: Boolean,
        originalReps: Int = 0,
        originalSeconds: Int = 45,
        originalWeight: Double = 12.5,
    ): RestoredFixture {
        val plan = if (planned) BackupSessionExercise(
            "hold-matching-plan", SESSION_ID, HOLD_ID, 0, 1, 1, 12.5, 60, 45, null,
        ) else BackupSessionExercise("hold-unrelated-plan", SESSION_ID, INTACT_ID, 0, 3, 5, 100.0, 90)
        return RestoredFixture(
            name = (if (planned) "planned" else "saved-only") + "-reps$originalReps-seconds$originalSeconds-weight$originalWeight",
            document = BackupDocument(
                version = 5,
                exportedAt = EXPORTED_AT,
                preferences = BackupPreferences(weightUnit = "kg", onboardingComplete = true),
                exercises = listOf(exercise(INTACT_ID, "Untouched prescribed lift"), exercise(HOLD_ID, "Original weighted static hold")),
                routines = emptyList(), routineExercises = emptyList(),
                sessions = listOf(
                    BackupSession(SESSION_ID, null, "Restored typed hold safety", STAMP,
                        "Keep original type and all captured values", 10, STAMP, STAMP + 600_000L),
                    BackupSession(SENTINEL_SESSION_ID, null, "Untouched sentinel session", STAMP - 86_400_000L,
                        "Untouched sentinel notes", 10, STAMP - 86_400_000L, STAMP - 85_800_000L),
                ).sortedBy { it.id },
                sessionExercises = listOf(plan,
                    BackupSessionExercise("sentinel-plan", SENTINEL_SESSION_ID, INTACT_ID, 0, 3, 5, 100.0, 90)),
                setLogs = listOf(
                    BackupSetLog(
                        id = SET_ID, sessionId = SESSION_ID, exerciseId = HOLD_ID, setNumber = 1,
                        weightKg = originalWeight, reps = originalReps, rpe = if (originalReps > 0) 8 else null, isWarmup = false,
                        completedAt = STAMP + 1_000L, durationSeconds = originalSeconds,
                    ),
                    BackupSetLog(id = "untouched-sentinel-set", sessionId = SENTINEL_SESSION_ID,
                        exerciseId = INTACT_ID, setNumber = 2, weightKg = 37.5, reps = 7, rpe = 9,
                        isWarmup = false, completedAt = STAMP - 86_399_000L, durationSeconds = null),
                ),
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
        const val SENTINEL_SESSION_ID = "hold-safety-sentinel-session"
        const val TYPE_TIME = "Type a duration"
        const val SET_ID = "hold-safety-original-set"
        const val INTACT_ID = "hold-intact-lift"
        const val HOLD_ID = "hold-original-lift"

        const val STAMP = 1_790_856_000_000L
        const val EXPORTED_AT = "2026-10-09T00:00:00Z"
    }
}
