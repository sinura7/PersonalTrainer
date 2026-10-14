package com.sinura.personaltrainer.ui.components

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.CustomExerciseDrafts
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.PickerContextDrafts
import com.sinura.personaltrainer.data.draft.PickerContextSnapshot
import com.sinura.personaltrainer.data.draft.StagedPickerForms
import com.sinura.personaltrainer.data.draft.WeekFormCodec
import com.sinura.personaltrainer.data.draft.WeekFormFields
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.activity.ActivityComposerViewModel
import com.sinura.personaltrainer.ui.activity.StrengthAdderFields
import com.sinura.personaltrainer.ui.routines.CustomWeekViewModel
import com.sinura.personaltrainer.util.IdFactory
import com.sinura.personaltrainer.util.JvmTime
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StagedPickerCallerRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var forms: StagedPickerForms
    private lateinit var contexts: PickerContextDrafts
    private lateinit var custom: CustomExerciseDrafts
    private val activities = mutableListOf<ActivityComposerViewModel>()
    private val weeks = mutableListOf<CustomWeekViewModel>()
    private val io = object : DraftFileIO by AtomicDraftFiles {
        var failParent = false
        var failCleanup = false
        var failParentRead = false
        override fun read(file: File): ByteArray? {
            val bytes = AtomicDraftFiles.read(file)
            if (failParentRead && bytes?.toString(Charsets.UTF_8)?.contains("activity-picker-parent") == true) throw IOException("synthetic initial parent read failure")
            return bytes
        }
        override fun replace(file: File, bytes: ByteArray) {
            if (failCleanup && bytes.toString(Charsets.UTF_8).contains("activity-picker-parent") && bytes.toString(Charsets.UTF_8).contains("\"draft\":null")) throw IOException("synthetic accepted cleanup failure")
            if (failParent && (bytes.toString(Charsets.UTF_8).contains("activity-picker-parent") ||
                bytes.toString(Charsets.UTF_8).contains("custom-week-picker-parent"))) throw IOException("synthetic parent checkpoint failure")
            AtomicDraftFiles.replace(file, bytes)
        }
    }
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        val store = DurableDraftStore(directory = temporary.newFolder(), dispatcher = dispatcher, files = io)
        forms = StagedPickerForms(store); contexts = PickerContextDrafts(store)
        custom = CustomExerciseDrafts(store = store, repository = deps.exerciseRepository,
            maintenance = deps.dbMaintenance, restoreOpen = { false }, dispatcher = dispatcher)
    }
    @After fun close() {
        runBlocking { activities.forEach { it.clearAndJoinForTest() }; weeks.forEach { it.clearAndJoinForTest() } }
        deps.close(); Dispatchers.resetMain()
    }
    private fun activity(mode: String = "mixed", occurrence: String? = null) = ActivityComposerViewModel(
        application = ApplicationProvider.getApplicationContext(), savedStateHandle = SavedStateHandle(buildMap {
            put("mode", mode); if (occurrence != null) put("composer-held-occurrence", occurrence)
        }),
        container = deps, ids = IdFactory.Uuid, clock = JvmTime, forms = forms, contexts = contexts, customDrafts = custom,
    ).also { activities += it }
    private fun week() = CustomWeekViewModel(application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = SavedStateHandle(), container = deps, forms = forms, contexts = contexts, customDrafts = custom,
    ).also { weeks += it }
    private suspend fun ready(vm: ActivityComposerViewModel) { checkNotNull(vm.formRecovery).status.awaitFirst { it.ready } }
    private suspend fun ready(vm: CustomWeekViewModel) { checkNotNull(vm.formRecovery).status.awaitFirst { it.ready } }
    private suspend fun picker(recovery: OwnedPickerRecovery) {
        recovery.view.open(); recovery.view.status.awaitFirst { !it.busy }
        assertEquals(null, recovery.view.status.value.error)
    }
    private suspend fun seedLift() = (deps.exerciseRepository.createCustom("Synthetic baseline press", "Chest") as SaveExerciseResult.Saved).exercise

    @Test fun reopeningActivityRestoresItsFormAndPickerWithoutSavingAnActivity() = runBlocking {
        val lift = seedLift()
        val first = activity(); ready(first)
        first.setTitle("  Yesterday's mixed work  ")
        val day = first.uiState.awaitFirst { it.title.isNotEmpty() }.epochDay - 1L
        first.setEpochDay(day); first.addStrength(lift, 17.5, 11)
        first.addCardio(CardioType.RUN, 19, 3.25, true)
        first.changeAdder(StrengthAdderFields(lift.id, " 18.75 ", "09", WeightUnit.KG))
        first.setPickerVisible(true)
        val recovery = checkNotNull(first.pickerRecovery); picker(recovery)
        val view = PickerContextSnapshot(query = "  overhead  ", muscle = CanonicalMuscle.SHOULDERS, index = 7, offsetDp = 13f)
        recovery.change(view); recovery.leave { first.setPickerVisible(false) }
        first.showPicker.awaitFirst { !it }
        assertTrue(checkNotNull(first.formRecovery).flush())
        first.clearAndJoinForTest(); activities.remove(first)
        val reopened = activity(); ready(reopened)
        val restored = reopened.uiState.awaitFirst { it.title == "  Yesterday's mixed work  " }
        assertEquals(day, restored.epochDay); assertEquals(17.5, restored.strength.single().weightKg, 0.0)
        assertEquals(11, restored.strength.single().reps); assertEquals(3.25, restored.cardio.single().distanceKm)
        assertEquals(StrengthAdderFields(lift.id, " 18.75 ", "09", WeightUnit.KG), reopened.adderFields.value)
        reopened.setPickerVisible(true); picker(checkNotNull(reopened.pickerRecovery))
        assertEquals(view, reopened.pickerRecovery.view.fields.value)
        assertEquals(null, reopened.savedId.value)
        assertTrue(deps.database.activityDao().getAllGraphs().isEmpty())
    }

    @Test fun weekSelectionsTargetsAndDayScopedSearchSurviveFreshNavigation() = runBlocking {
        val lift = seedLift(); val first = week(); ready(first)
        first.selectDay(Weekday.TUESDAY); first.selectPickerExercise(lift)
        val row = first.uiState.awaitFirst { it.selectedLifts.isNotEmpty() }.selectedLifts.single()
        first.stageTargets(itemId = row.id, sets = 4, reps = 9, rest = 75, weightKg = 17.5, seconds = null, secondsMax = null)
        first.setPickerVisible(true); val recovery = checkNotNull(first.pickerRecovery); picker(recovery)
        recovery.change(PickerContextSnapshot(query = "  Tuesday press  "))
        recovery.leave { first.setPickerVisible(false) }
        first.uiState.awaitFirst { !it.showPicker }; assertTrue(checkNotNull(first.formRecovery).flush())
        first.clearAndJoinForTest(); weeks.remove(first)
        val reopened = week(); ready(reopened)
        val state = reopened.uiState.awaitFirst { it.selectedLifts.isNotEmpty() }
        assertEquals(Weekday.TUESDAY, state.selectedDay)
        assertEquals(row.id, state.selectedLifts.single().id); assertEquals(4, state.selectedLifts.single().targetSets)
        assertEquals(9, state.selectedLifts.single().targetReps)
        reopened.setPickerVisible(true); picker(checkNotNull(reopened.pickerRecovery))
        assertEquals("  Tuesday press  ", reopened.pickerRecovery.view.fields.value.query)
        reopened.pickerRecovery.leave { reopened.setPickerVisible(false) }
        reopened.uiState.awaitFirst { !it.showPicker }
        reopened.pickerRecovery.view.closeVisit()
        reopened.selectDay(Weekday.THURSDAY); reopened.setPickerVisible(true); picker(reopened.pickerRecovery)
        assertEquals("", reopened.pickerRecovery.view.fields.value.query)
        assertTrue(reopened.uiState.value.selectedLifts.isEmpty())
        assertFalse(reopened.finished.value)
    }

    @Test fun savedActivityCreateWaitsForDurableSelectionAndRetriesWithoutAnotherCreate() = runBlocking {
        val vm = activity(); ready(vm); vm.setPickerVisible(true)
        val recovery = checkNotNull(vm.pickerRecovery); picker(recovery)
        recovery.openCreate(); recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.EDITING }
        recovery.create.update(name = "Exact activity custom", muscle = "Chest", loadType = LoadType.STACK, notes = "  retained notes  ")
        val identity = checkNotNull(recovery.create.status.value.fields).createId
        io.failParent = true
        recovery.saveCreate(); recovery.saveCreate()
        recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.SAVED && it.message?.contains("Selection was not confirmed") == true }
        assertEquals(1, deps.database.exerciseDao().getAllCustom().count { it.id == identity })
        assertEquals(identity, vm.adderFields.value.pickedId)
        assertTrue(vm.showPicker.value)
        io.failParent = false
        recovery.useSaved(); vm.showPicker.awaitFirst { !it }
        assertEquals(1, deps.database.exerciseDao().getAllCustom().count { it.id == identity })
        vm.clearAndJoinForTest(); activities.remove(vm)
        val reopened = activity(); ready(reopened)
        assertEquals(identity, reopened.adderFields.value.pickedId)
        assertTrue(deps.database.activityDao().getAllGraphs().isEmpty())
    }

    @Test fun savedWeekCreateResumesTheSameDayAndDoesNotDuplicateTheStagedLift() = runBlocking {
        val vm = week(); ready(vm); vm.selectDay(Weekday.FRIDAY); vm.setPickerVisible(true)
        val recovery = checkNotNull(vm.pickerRecovery); picker(recovery)
        recovery.openCreate(); recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.EDITING }
        recovery.create.update(name = "Exact Friday custom", muscle = "Chest", loadType = LoadType.EXTERNAL, notes = "")
        val identity = checkNotNull(recovery.create.status.value.fields).createId
        io.failParent = true; recovery.saveCreate()
        recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.SAVED && it.message?.contains("Selection was not confirmed") == true }
        vm.selectDay(Weekday.MONDAY)
        assertEquals(Weekday.FRIDAY, vm.uiState.awaitFirst { it.selectedLifts.isNotEmpty() }.selectedDay)
        val stagedId = vm.uiState.value.selectedLifts.single().id
        io.failParent = false; recovery.useSaved()
        recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.CLOSED }
        assertEquals(stagedId, vm.uiState.value.selectedLifts.single().id)
        assertEquals(identity, vm.uiState.value.selectedLifts.single().exercise.id)
        assertEquals(1, deps.database.exerciseDao().getAllCustom().count { it.id == identity })
        vm.clearAndJoinForTest(); weeks.remove(vm)
        val reopened = week(); ready(reopened)
        assertEquals(stagedId, reopened.uiState.awaitFirst { it.selectedLifts.isNotEmpty() }.selectedLifts.single().id)
        assertFalse(reopened.finished.value)
    }

    @Test fun failedOrderlyExitKeepsFieldsActionableUntilAnAcknowledgedCheckpoint() = runBlocking {
        val vm = activity(); ready(vm); vm.setTitle("Exact unsaved title")
        io.failParent = true
        var left = false; vm.keepAndLeave { left = true }
        checkNotNull(vm.formRecovery).status.awaitFirst { it.error != null }
        assertFalse(left); assertEquals("Exact unsaved title", vm.uiState.awaitFirst { it.title.isNotEmpty() }.title)
        io.failParent = false; vm.keepAndLeave { left = true }
        vm.formRecovery.status.awaitFirst { it.ready }
        assertTrue(left)
        vm.clearAndJoinForTest(); activities.remove(vm)
        val reopened = activity(); ready(reopened)
        assertEquals("Exact unsaved title", reopened.uiState.awaitFirst { it.title.isNotEmpty() }.title)
    }

    @Test fun acceptedActivityWithFailedPrivateCleanupCannotSubmitAgainAfterReopening() = runBlocking {
        val vm = activity(); ready(vm); vm.addCardio(CardioType.RUN, 19, 3.25, true)
        vm.setTitle("Exact accepted activity"); io.failCleanup = true
        vm.save(); val id = vm.savedId.awaitFirst { it != null }
        assertEquals(1, deps.database.activityDao().getAllGraphs().size)
        vm.clearAndJoinForTest(); activities.remove(vm)
        val reopened = activity()
        val recovered = checkNotNull(reopened.formRecovery).status.awaitFirst { !it.loading }
        assertTrue(recovered.accepted); assertTrue(recovered.pending); assertFalse(recovered.ready)
        reopened.save(); reopened.setTitle("An unintended replacement")
        assertEquals("Exact accepted activity", reopened.uiState.awaitFirst { it.title.isNotEmpty() }.title)
        assertEquals(id, deps.database.activityDao().getAllGraphs().single().session.id)
        assertEquals(null, reopened.savedId.value)
    }

    @Test fun recoveredReadNeverOverwritesNewFieldsAuthoredWhileTheReadWasUnavailable() = runBlocking {
        val vm = activity(); ready(vm); vm.setTitle("Older kept title")
        assertTrue(checkNotNull(vm.formRecovery).flush()); vm.clearAndJoinForTest(); activities.remove(vm)
        io.failParentRead = true
        val reopened = activity(); reopened.formRecovery!!.status.awaitFirst { !it.loading && it.error != null }
        reopened.setTitle("Newer authored title")
        io.failParentRead = false; reopened.formRecovery.retry()
        reopened.formRecovery.status.awaitFirst { it.error?.contains("newer fields") == true }
        assertEquals("Newer authored title", reopened.uiState.awaitFirst { it.title.isNotEmpty() }.title)
        assertFalse(reopened.formRecovery.status.value.ready)
        var left = false; reopened.keepAndLeave { left = true }
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(left)
        reopened.clearAndJoinForTest(); activities.remove(reopened)
        val original = activity(); ready(original)
        assertEquals("Older kept title", original.uiState.awaitFirst { it.title.isNotEmpty() }.title)
    }

    @Test fun noOccurrenceAndAnOccurrenceLiterallyNamedUnplannedHaveDifferentOwners() = runBlocking {
        val ordinary = activity(); ready(ordinary); ordinary.setTitle("Unplanned draft")
        assertTrue(checkNotNull(ordinary.formRecovery).flush())
        ordinary.clearAndJoinForTest(); activities.remove(ordinary)
        val planned = activity(occurrence = "unplanned"); ready(planned); planned.setTitle("Owned occurrence draft")
        assertTrue(checkNotNull(planned.formRecovery).flush())
        planned.clearAndJoinForTest(); activities.remove(planned)
        val reopened = activity(); ready(reopened)
        assertEquals("Unplanned draft", reopened.uiState.awaitFirst { it.title.isNotEmpty() }.title)
        val occurrence = activity(occurrence = "unplanned"); ready(occurrence)
        assertEquals("Owned occurrence draft", occurrence.uiState.awaitFirst { it.title.isNotEmpty() }.title)
    }

    @Test fun missingRequiredWeekDayIsRejectedBeforeAnyLiveStatePublication() {
        val fields = WeekFormFields(identity = java.util.UUID.randomUUID().toString(), selectedDay = Weekday.TUESDAY,
            days = emptyMap(), userPickedDay = true, answers = null, weightUnit = null, preferredDays = emptySet())
        val encoded = WeekFormCodec.encodeDraft(fields).asJsonObject
        encoded.remove("selectedDay")
        assertTrue(runCatching { WeekFormCodec.decodeDraft(encoded) }.isFailure)
    }

    @Test fun closingAWeekPickerKeepsTheParentBeforeExitAndOffersExactRetryOnFailure() = runBlocking {
        val lift = seedLift(); val vm = week(); ready(vm)
        vm.setPickerVisible(true); val recovery = checkNotNull(vm.pickerRecovery); picker(recovery)
        vm.togglePicked(lift)
        val row = vm.uiState.awaitFirst { it.selectedLifts.isNotEmpty() }.selectedLifts.single()
        io.failParent = true; vm.setPickerVisible(false)
        vm.uiState.awaitFirst { it.pickerProgress.canRetry && !it.pickerProgress.busy }
        assertTrue(vm.uiState.value.showPicker); assertFalse(recovery.canSelect())
        recovery.openCreate(); assertEquals(PickerCreatePhase.CLOSED, recovery.create.status.value.phase)
        io.failParent = false; vm.retryPicker()
        vm.uiState.awaitFirst { !it.showPicker }
        vm.clearAndJoinForTest(); weeks.remove(vm)
        val reopened = week(); ready(reopened)
        assertEquals(row.id, reopened.uiState.awaitFirst { it.selectedLifts.isNotEmpty() }.selectedLifts.single().id)
        assertEquals(lift.id, reopened.uiState.value.selectedLifts.single().exercise.id)
    }
}
