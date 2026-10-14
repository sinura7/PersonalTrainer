package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.RoutineDrafts
import com.sinura.personaltrainer.data.draft.RoutineNumberDraft
import com.sinura.personaltrainer.data.draft.RoutineNumberField
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutPaste
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.insertTestExercise
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RoutineEditorDurabilityTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var directory: File
    private val models = mutableListOf<RoutineEditorViewModel>()
    private var successfulCommands = 0
    private var refusedSets: Int? = null
    private var refuseAll = false
    private var refuseAfterFirst = false
    private var loseImportAcknowledgement = false
    private var importReadUnavailableAfterCommit = false
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        directory = File.createTempFile("p09-editor-", "").apply { delete(); mkdirs() }
    }
    @After fun close() {
        runBlocking { models.toList().forEach { it.clearAndJoinForTest() } }
        deps.close(); directory.deleteRecursively(); Dispatchers.resetMain()
    }
    private fun port() = RoutineDrafts(store = DurableDraftStore(directory, dispatcher),
        maintenance = deps.dbMaintenance, restoreOpen = { false },
        readRecord = deps.routineRepository::editorRecord,
        inspectSaved = { command ->
            val outcome = deps.routineRepository.inspectEditorCommand(command)
            if (importReadUnavailableAfterCommit && outcome == com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome.EXACT_SAVED && command.changes.size > 1)
                throw IOException("synthetic unavailable import readback")
            outcome
        },
        commitLocked = { command ->
            if (refuseAll || refuseAfterFirst && successfulCommands >= 1 ||
                command.changes.any { change -> change.after?.items?.any { it.targetSets == refusedSets } == true })
                throw IOException("synthetic refused command")
            deps.routineRepository.commitEditorCommandLocked(command); successfulCommands++
            if (loseImportAcknowledgement && command.changes.size > 1) {
                loseImportAcknowledgement = false
                throw IOException("synthetic lost import acknowledgement")
            }
        }, dispatcher = dispatcher)
    private fun editor(id: String = "new", initial: String? = null, restored: SavedStateHandle? = null) = RoutineEditorViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = restored ?: SavedStateHandle(mapOf("routineId" to id, "initialExerciseId" to initial)),
        container = deps, durable = port()).also { models += it }
    private suspend fun retire(vm: RoutineEditorViewModel) { vm.clearAndJoinForTest(); models.remove(vm) }
    private suspend fun seededRoutine() = deps.routineRepository.create(name = "Upper A", notes = "original").also { routine ->
        val bench = insertTestExercise(deps = deps, id = "p09-bench", name = "Bench Press", muscleGroup = "Chest")
        deps.routineRepository.addExercise(routineId = routine.id, exercise = bench, targetSets = 3,
            targetReps = 8, targetWeightKg = 50.0, restSeconds = 90)
    }
    private suspend fun catalog() {
        insertTestExercise(deps = deps, id = "p09-bench", name = "Bench Press", muscleGroup = "Chest")
        insertTestExercise(deps = deps, id = "p09-row", name = "Cable Row", muscleGroup = "Back")
    }
    private val programme = "Upper A\n1. Bench Press — 3×8\n2. Cable Row — 2×10\n3. Bench Press — 5×6\n\nLower A\n1. Cable Row — 3×12"

    @Test fun freshHandleReopensRawFieldsTargetsNumericTextAndUnitsWithoutSaving() = runBlocking {
        val row = seededRoutine(); val vm = editor(row.id)
        val opened = vm.uiState.awaitFirst { !it.isLoading && it.routine?.exercises?.size == 1 }
        val item = checkNotNull(opened.routine).exercises.single()
        vm.onNameChange("Upper A · controlled")
        vm.onNotesChange("Pause at the bottom…")
        vm.stageTargets(itemId = item.id, targetSets = 5, targetReps = 9, targetWeightKg = 52.5, restSeconds = 100)
        vm.putNumberDraft(RoutineNumberDraft(itemId = item.id, exerciseId = item.exercise.id,
            field = RoutineNumberField.WEIGHT, text = "115.", selectionStart = 4, selectionEnd = 4, unit = WeightUnit.LBS))
        vm.recovery.awaitFirst { it.fields?.numbers?.singleOrNull()?.text == "115." && it.fields?.targets?.singleOrNull()?.sets == 5 }
        assertEquals(0, successfulCommands)
        assertEquals("original", deps.routineRepository.getById(row.id)?.notes)
        retire(vm)
        val reopened = editor(row.id)
        val restored = reopened.uiState.awaitFirst { !it.isLoading && it.name == "Upper A · controlled" }
        assertEquals("Pause at the bottom…", restored.notes)
        assertEquals(WeightUnit.LBS, reopened.retainedNumbers.value.single().unit)
        assertEquals(5, reopened.recovery.value.fields?.targets?.single()?.sets)
        assertEquals(3, deps.routineRepository.getById(row.id)?.exercises?.single()?.targetSets)
        assertEquals(0, successfulCommands)
    }

    @Test fun canonicalLibrarySelectionSavesExactlyOneRoutineAndRemainsConsumedAfterReopening() = runBlocking {
        val selected = insertTestExercise(deps = deps, id = "p09-selected", name = "Selected row")
        val vm = editor(initial = selected.id)
        val ready = vm.uiState.awaitFirst { !it.isLoading && it.routine?.exercises?.size == 1 }
        val id = checkNotNull(ready.routine).id
        vm.onNameChange("My routine")
        vm.saveAndLeave(); vm.exitRequested.awaitFirst { it }
        retire(vm)
        val reopened = editor(id)
        reopened.uiState.awaitFirst { !it.isLoading && it.routine?.id == id }
        assertEquals(1, deps.routineRepository.count())
        assertEquals("My routine", deps.routineRepository.getById(id)?.name)
        assertEquals(selected.id, deps.routineRepository.getById(id)?.exercises?.single()?.exercise?.id)
    }

    @Test fun newerDurableAuthoredFieldsOutrankAnOlderFrameworkSnapshot() = runBlocking {
        val row = seededRoutine()
        val state = SavedStateHandle(mapOf("routineId" to row.id))
        val vm = editor(restored = state)
        vm.uiState.awaitFirst { !it.isLoading && it.routine != null }
        vm.onNameChange("Older name"); vm.onNotesChange("Older notes")
        vm.recovery.awaitFirst { it.fields?.notes == "Older notes" }
        val stale = SavedStateHandle(state.keys().associateWith { key -> state.get<Any?>(key) })
        vm.onNameChange("Latest name"); vm.onNotesChange("Latest notes")
        vm.recovery.awaitFirst { it.fields?.name == "Latest name" && it.fields?.notes == "Latest notes" }
        retire(vm)
        val reopened = editor(restored = stale)
        val visible = reopened.uiState.awaitFirst { !it.isLoading && it.routine != null }
        assertEquals("Latest name", visible.name); assertEquals("Latest notes", visible.notes)
        assertEquals(0, successfulCommands)
        reopened.saveAndLeave(); reopened.exitRequested.awaitFirst { it }
        assertEquals("Latest name", deps.routineRepository.getById(row.id)?.name)
        assertEquals("Latest notes", deps.routineRepository.getById(row.id)?.notes)
        assertEquals(1, successfulCommands)
    }

    @Test fun discardingAFailedInitialAddNeverReplaysTheLiftAndRemovesOnlyTheEmptyNewStub() = runBlocking {
        catalog(); refuseAfterFirst = true
        val vm = editor(initial = "p09-bench")
        vm.uiState.awaitFirst { it.failed }
        val stub = deps.routineRepository.observeAll().awaitFirst { it.size == 1 }.single()
        assertTrue(stub.exercises.isEmpty()); assertEquals(1, successfulCommands)
        refuseAfterFirst = false
        vm.discardUnfinishedFields(); vm.exitRequested.awaitFirst { it }
        assertEquals(0, deps.routineRepository.count())
        assertEquals(2, successfulCommands) // create plus deliberate empty-stub cleanup, no Add
    }

    @Test fun unreadableFieldsAllowBackAndOwnerScopedDiscardWithoutChangingSavedRoutines() = runBlocking {
        val saved = seededRoutine()
        port().open(null, null)
        val file = directory.listFiles()!!.single { it.name.endsWith(".json") &&
            JsonParser.parseString(it.readText()).asJsonObject.get("payloadFormat")?.asString == "routine-fields" }
        file.writeText("{broken checkpoint")
        val unreadable = file.readBytes()
        val vm = editor()
        vm.uiState.awaitFirst { it.failed }
        vm.leave(); vm.exitRequested.awaitFirst { it }
        assertTrue(file.readBytes().contentEquals(unreadable))
        retire(vm)
        val retry = editor(); retry.uiState.awaitFirst { it.failed }
        retry.discardUnfinishedFields(); retry.exitRequested.awaitFirst { it }; retire(retry)
        val reopened = editor(); reopened.recovery.awaitFirst { it.ready && it.fields != null }
        assertEquals(saved.id, deps.routineRepository.observeAll().awaitFirst { it.size == 1 }.single().id)
        assertEquals("original", deps.routineRepository.getById(saved.id)?.notes)
        assertEquals(0, successfulCommands)
        assertTrue(directory.listFiles()!!.any { it.name.contains(".quarantine-") && it.readBytes().contentEquals(unreadable) })
    }

    @Test fun acceptedNoteOnlyImportIsNotDeletedOnReopeningAndDiscard() = runBlocking {
        catalog(); val vm = editor(); vm.recovery.awaitFirst { it.ready }
        val text = "Upper A\nNotes: breathe slowly\n\nLower A\n1. Cable Row — 3×12"
        vm.importPaste(text); vm.imports.awaitFirst { it.preview != null && it.visible && !it.keeping }
        val frozen = checkNotNull(vm.imports.value.preview)
        // Commit/inspection is the interrupted window before the VM receipt checkpoint.
        val service = port()
        val handle = service.open(null, null)
        loseImportAcknowledgement = true
        assertEquals(com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome.EXACT_SAVED,
            service.execute(handle) { frozen })
        assertFalse(handle.fields.createdHere); assertTrue(handle.fields.importApplied)
        val owner = handle.fields.routineId
        assertTrue(checkNotNull(deps.routineRepository.getById(owner)).exercises.isEmpty())
        retire(vm)
        val reopened = editor(owner)
        val ready = reopened.uiState.awaitFirst { !it.isLoading && it.routine != null }
        assertEquals("Upper A", ready.name)
        reopened.discardUnfinishedFields(); reopened.exitRequested.awaitFirst { it }
        assertEquals(2, deps.routineRepository.count())
        assertEquals("Upper A", deps.routineRepository.getById(owner)?.name)
        assertEquals(1, successfulCommands)
    }

    @Test fun anOlderFailedVisitCannotDiscardANewerReadableCheckpoint() = runBlocking {
        port().open(null, null)
        val file = directory.listFiles()!!.single { it.name.endsWith(".json") &&
            JsonParser.parseString(it.readText()).asJsonObject.get("payloadFormat")?.asString == "routine-fields" }
        val readable = file.readBytes()
        file.writeText("{broken checkpoint")
        val old = editor(); old.uiState.awaitFirst { it.failed }
        file.writeBytes(readable)
        val newer = editor(); newer.recovery.awaitFirst { it.ready && it.fields != null }
        newer.onNameChange("Newer owning visit")
        newer.recovery.awaitFirst { it.fields?.name == "Newer owning visit" }
        old.discardUnfinishedFields(); old.uiState.awaitFirst { it.saveError != null }
        assertFalse(old.exitRequested.value)
        retire(old); retire(newer)
        val reopened = editor(); reopened.recovery.awaitFirst { it.ready && it.fields != null }
        assertEquals("Newer owning visit", reopened.recovery.value.fields?.name)
        assertEquals(0, successfulCommands)
    }

    @Test fun unreadableCommandCanBeExplicitlyQuarantinedWithoutSubmittingItsIntent() = runBlocking {
        catalog(); refuseAll = true
        val vm = editor(initial = "p09-bench"); vm.uiState.awaitFirst { it.failed }; retire(vm)
        val file = directory.listFiles()!!.single { it.name.endsWith(".json") &&
            JsonParser.parseString(it.readText()).asJsonObject.get("payloadFormat")?.asString == "routine-command" }
        file.writeText("{broken command")
        val unreadable = file.readBytes()
        val failed = editor(); failed.uiState.awaitFirst { it.failed }
        failed.discardUnfinishedFields(); failed.exitRequested.awaitFirst { it }; retire(failed)
        refuseAll = false
        val reopened = editor(); reopened.recovery.awaitFirst { it.ready && it.fields != null }
        assertEquals(0, deps.routineRepository.count()); assertEquals(0, successfulCommands)
        assertTrue(directory.listFiles()!!.any { it.name.contains(".quarantine-") && it.readBytes().contentEquals(unreadable) })
    }

    @Test fun staleFrameworkFieldsAfterAcceptedPrivateCleanupRequireReviewBeforeAnotherSave() = runBlocking {
        val row = seededRoutine(); val state = SavedStateHandle(mapOf("routineId" to row.id))
        val vm = editor(restored = state)
        vm.uiState.awaitFirst { !it.isLoading && it.routine != null }
        vm.onNameChange("Old authored name"); vm.onNotesChange("Old authored notes")
        vm.recovery.awaitFirst { it.fields?.notes == "Old authored notes" }
        val stale = SavedStateHandle(state.keys().associateWith { key -> state.get<Any?>(key) })
        vm.onNameChange("Accepted latest name"); vm.onNotesChange("Accepted latest notes")
        vm.saveAndLeave(); vm.exitRequested.awaitFirst { it }; retire(vm)
        val reopened = editor(restored = stale)
        reopened.uiState.awaitFirst { !it.isLoading && it.routine != null }
        assertTrue(reopened.recovery.value.fields?.needsReview == true)
        reopened.saveAndLeave(); reopened.uiState.awaitFirst { it.saveError != null }
        assertFalse(reopened.exitRequested.value)
        assertEquals("Accepted latest name", deps.routineRepository.getById(row.id)?.name)
        assertEquals("Accepted latest notes", deps.routineRepository.getById(row.id)?.notes)
        assertEquals(1, successfulCommands)
    }

    @Test fun sameVisitDiscardSettlesAnUncertainNoteOnlyImportWithoutDeletingSavedWork() = runBlocking {
        catalog(); val vm = editor(initial = "p09-bench")
        val first = checkNotNull(vm.uiState.awaitFirst { !it.isLoading && it.routine?.exercises?.size == 1 }.routine)
        vm.removeExercise(first.exercises.single().id)
        vm.uiState.awaitFirst { it.routine?.exercises?.isEmpty() == true }
        vm.importPaste("Upper A\nNotes: breathe slowly\n\nLower A\n1. Cable Row — 3×12")
        vm.imports.awaitFirst { it.preview != null && it.visible }
        importReadUnavailableAfterCommit = true
        vm.confirmImport()
        vm.imports.awaitFirst { !it.applying && it.outcome == com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome.UNAVAILABLE }
        assertEquals("Upper A", deps.routineRepository.getById(first.id)?.name)
        val writesAfterImport = successfulCommands
        importReadUnavailableAfterCommit = false
        vm.discardUnfinishedFields(); vm.exitRequested.awaitFirst { it }
        assertEquals(writesAfterImport, successfulCommands)
        assertEquals(2, deps.routineRepository.count())
        assertEquals("Upper A", deps.routineRepository.getById(first.id)?.name)
    }

    @Test fun failedDetailsSaveKeepsNameAndNotesAcrossReopeningAndRetriesTheFrozenIntentOnce() = runBlocking {
        val row = seededRoutine(); val vm = editor(row.id)
        vm.uiState.awaitFirst { !it.isLoading && it.routine != null }
        vm.onNameChange("Intended name"); vm.onNotesChange("Intended notes")
        refuseAll = true
        vm.saveAndLeave(); val failed = vm.uiState.awaitFirst { it.saveError != null }
        assertEquals("Intended name", failed.name); assertEquals("Intended notes", failed.notes)
        assertFalse(vm.exitRequested.value); assertEquals(0, successfulCommands)
        vm.leaveAnyway(); vm.exitRequested.awaitFirst { it }; retire(vm)
        refuseAll = false
        val reopened = editor(row.id)
        val kept = reopened.uiState.awaitFirst { !it.isLoading && it.routine != null }
        assertEquals("Intended name", kept.name); assertEquals("Intended notes", kept.notes)
        assertEquals("Upper A", deps.routineRepository.getById(row.id)?.name)
        reopened.saveAndLeave(); reopened.saveAndLeave(); reopened.exitRequested.awaitFirst { it }
        assertEquals("Intended name", deps.routineRepository.getById(row.id)?.name)
        assertEquals("Intended notes", deps.routineRepository.getById(row.id)?.notes)
        assertEquals(1, successfulCommands)
    }

    @Test fun successfulBackFlushesAuthoredDetailsAndTargetsUnderTheDurablePath() = runBlocking {
        val row = seededRoutine(); val vm = editor(row.id)
        val ready = checkNotNull(vm.uiState.awaitFirst { !it.isLoading && it.routine != null }.routine)
        vm.onNameChange("Back accepted"); vm.onNotesChange("Back notes")
        vm.stageTargets(itemId = ready.exercises.single().id, targetSets = 4, targetReps = 10,
            targetWeightKg = 55.0, restSeconds = 100)
        vm.leave(); vm.exitRequested.awaitFirst { it }
        val saved = checkNotNull(deps.routineRepository.getById(row.id))
        assertEquals("Back accepted", saved.name); assertEquals("Back notes", saved.notes)
        assertEquals(4, saved.exercises.single().targetSets); assertEquals(10, saved.exercises.single().targetReps)
        assertEquals(55.0, checkNotNull(saved.exercises.single().targetWeightKg), 0.00001)
        assertEquals(100, saved.exercises.single().restSeconds)
    }

    @Test fun readingAndClosingAnImportKeepsExactPreviewButCreatesNothing() = runBlocking {
        catalog(); val vm = editor()
        vm.recovery.awaitFirst { it.ready }
        vm.onPasteTextChange(programme); vm.importPaste(programme)
        val preview = vm.imports.awaitFirst { it.preview != null && it.visible }
        val ids = checkNotNull(preview.preview).changes.map { it.id }
        assertEquals(1, preview.details?.duplicates?.size)
        assertEquals(0, deps.routineRepository.count())
        assertEquals(0, successfulCommands)
        vm.closeImportPreview()
        vm.imports.awaitFirst { !it.visible && !it.keeping }
        assertFalse(checkNotNull(vm.recovery.value.fields).importVisible)
        retire(vm)
        val reopened = editor()
        reopened.recovery.awaitFirst { it.ready }
        assertEquals(ids, reopened.imports.value.preview?.changes?.map { it.id })
        assertFalse(reopened.imports.value.visible)
        assertEquals(programme, reopened.recovery.value.fields?.pasteText)
        assertEquals(0, deps.routineRepository.count())
    }

    @Test fun repeatedImportConfirmationCommitsTheFrozenIdsAndOrderOnce() = runBlocking {
        catalog(); val vm = editor()
        vm.recovery.awaitFirst { it.ready }
        vm.importPaste(programme)
        val preview = vm.imports.awaitFirst { it.preview != null && it.visible }
        val ids = checkNotNull(preview.preview).changes.map { it.id }
        vm.confirmImport(); vm.confirmImport()
        vm.imports.awaitFirst { it.applied && !it.applying }
        assertEquals(1, successfulCommands)
        assertEquals(ids.toSet(), deps.routineRepository.observeAll().awaitFirst { it.size == 2 }.map { it.id }.toSet())
        val primary = checkNotNull(deps.routineRepository.getById(ids.first()))
        assertEquals(listOf("p09-bench", "p09-row"), primary.exercises.map { it.exercise.id })
        assertEquals(listOf(3, 2), primary.exercises.map { it.targetSets })
    }

    @Test fun failedImportReopensWithoutSubmittingThenRetriesTheSameAllRoutineIdsOnce() = runBlocking {
        catalog(); val vm = editor()
        vm.recovery.awaitFirst { it.ready }
        vm.importPaste(programme)
        val expected = checkNotNull(vm.imports.awaitFirst { it.preview != null && it.visible }.preview).changes.map { it.id }
        refuseAll = true
        vm.confirmImport(); vm.imports.awaitFirst { !it.applying && it.problem != null }
        assertEquals(0, deps.routineRepository.count())
        retire(vm)
        val reopened = editor(); reopened.recovery.awaitFirst { it.ready }
        assertEquals(expected, reopened.imports.value.preview?.changes?.map { it.id })
        assertEquals(0, successfulCommands)
        refuseAll = false
        reopened.confirmImport(); reopened.imports.awaitFirst { it.applied && !it.applying }
        assertEquals(1, successfulCommands)
        assertEquals(expected.toSet(), deps.routineRepository.observeAll().awaitFirst { it.size == 2 }.map { it.id }.toSet())
    }

    @Test fun unrelatedLiftWriteCannotRebaseAnExternalNotesConflict() = runBlocking {
        val row = seededRoutine(); val vm = editor(row.id)
        vm.uiState.awaitFirst { !it.isLoading && it.routine?.exercises?.size == 1 }
        vm.onNotesChange("my unfinished notes")
        vm.recovery.awaitFirst { it.fields?.notes == "my unfinished notes" }
        deps.routineRepository.updateDetails(id = row.id, name = row.name, notes = "external notes")
        val extra = insertTestExercise(deps = deps, id = "p09-extra", name = "Cable Row")
        vm.addExercise(exercise = extra, targetSets = 2, targetReps = 10, targetWeightKg = null, restSeconds = 75)
        vm.recovery.awaitFirst { it.problem != null }
        assertEquals(1, deps.routineRepository.getById(row.id)?.exercises?.size)
        vm.saveAndLeave(); vm.uiState.awaitFirst { it.saveError != null }
        assertFalse(vm.exitRequested.value)
        assertEquals("external notes", deps.routineRepository.getById(row.id)?.notes)
        assertEquals("my unfinished notes", vm.uiState.value.notes)
        vm.reviewCurrentRoutine(); vm.recovery.awaitFirst { it.fields?.before?.routine?.notes == "external notes" && it.problem == null }
        vm.saveAndLeave(); vm.exitRequested.awaitFirst { it }
        assertEquals("my unfinished notes", deps.routineRepository.getById(row.id)?.notes)
    }

    @Test fun partialTargetFlushRetainsOnlyOutstandingWorkAcrossReopeningAndRetriesItOnce() = runBlocking {
        val row = seededRoutine()
        val extra = insertTestExercise(deps = deps, id = "p09-second", name = "Cable Row")
        deps.routineRepository.addExercise(routineId = row.id, exercise = extra, targetSets = 3,
            targetReps = 10, targetWeightKg = null, restSeconds = 75)
        val vm = editor(row.id)
        val ready = vm.uiState.awaitFirst { !it.isLoading && it.routine?.exercises?.size == 2 }
        val items = checkNotNull(ready.routine).exercises
        val wanted = mapOf(items[0].id to 5, items[1].id to 9)
        items.forEach { item -> vm.stageTargets(itemId = item.id, targetSets = wanted[item.id],
            targetReps = item.targetReps, targetWeightKg = item.targetWeightKg, restSeconds = item.restSeconds) }
        refuseAfterFirst = true
        vm.saveAndLeave(); vm.uiState.awaitFirst { it.saveError != null }
        assertFalse(vm.exitRequested.value)
        assertEquals(1, successfulCommands)
        val partial = checkNotNull(deps.routineRepository.getById(row.id)).exercises
        assertEquals(1, partial.count { it.targetSets == wanted[it.id] })
        assertEquals(1, partial.count { it.targetSets == 3 })
        vm.recovery.awaitFirst { it.fields?.targets?.size == 1 }
        retire(vm)
        val reopened = editor(row.id)
        reopened.uiState.awaitFirst { !it.isLoading && it.routine?.exercises?.size == 2 }
        assertEquals(1, successfulCommands)
        refuseAfterFirst = false
        reopened.saveAndLeave(); reopened.exitRequested.awaitFirst { it }
        assertEquals(2, successfulCommands)
        assertTrue(checkNotNull(deps.routineRepository.getById(row.id)).exercises.all { it.targetSets == wanted[it.id] })
    }

    @Test fun restoredNewRouteAfterAcceptedSaveKeepsItsSavedOwnerAndDoesNotReAddARemovedInitialLift() = runBlocking {
        catalog()
        val state = SavedStateHandle(mapOf("routineId" to "new", "initialExerciseId" to "p09-bench"))
        val vm = editor(restored = state)
        val first = checkNotNull(vm.uiState.awaitFirst { !it.isLoading && it.routine?.exercises?.size == 1 }.routine)
        val row = checkNotNull(deps.exerciseRepository.getById("p09-row"))
        vm.addExercise(exercise = row, targetSets = 2, targetReps = 10, targetWeightKg = null, restSeconds = 75)
        vm.uiState.awaitFirst { it.routine?.exercises?.size == 2 }
        vm.removeExercise(first.exercises.single().id)
        vm.uiState.awaitFirst { it.routine?.exercises?.singleOrNull()?.exercise?.id == "p09-row" }
        vm.onNameChange("Accepted routine")
        vm.saveAndLeave(); vm.exitRequested.awaitFirst { it }
        // SavedState can outlive the accepted private checkpoint before navigation pops.
        val restored = SavedStateHandle(state.keys().associateWith { key -> state.get<Any?>(key) })
        retire(vm)
        val reopened = editor(restored = restored)
        val loaded = reopened.uiState.awaitFirst { !it.isLoading && it.routine != null }
        assertEquals(first.id, loaded.routine?.id)
        assertEquals("Accepted routine", loaded.name)
        assertEquals(listOf("p09-row"), loaded.routine?.exercises?.map { it.exercise.id })
        assertEquals(1, deps.routineRepository.count())
    }

    @Test fun malformedImportShowsItsSpecificParsingReasonAndKeepsTheTextWithoutWrites() = runBlocking {
        catalog(); val vm = editor()
        vm.recovery.awaitFirst { it.ready }
        val text = "asdf potato"
        val exercises = deps.exerciseRepository.observeAll().awaitFirst { it.size == 2 }
        val reason = checkNotNull(WorkoutPaste.unreadableReason(text, WorkoutPaste.parseAndMatch(text, exercises)))
        vm.importPaste(text)
        val refused = vm.uiState.awaitFirst { it.error != null }
        assertEquals(reason, refused.error)
        assertEquals(text, vm.recovery.value.fields?.pasteText)
        assertEquals(0, deps.routineRepository.count())
        assertEquals(0, successfulCommands)
    }
}
