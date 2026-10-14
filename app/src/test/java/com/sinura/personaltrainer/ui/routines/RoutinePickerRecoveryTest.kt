package com.sinura.personaltrainer.ui.routines

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
import com.sinura.personaltrainer.data.draft.RoutineDrafts
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.components.PickerCreatePhase
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
class RoutinePickerRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var store: DurableDraftStore
    private lateinit var contexts: PickerContextDrafts
    private lateinit var custom: CustomExerciseDrafts
    private val models = mutableListOf<RoutineEditorViewModel>()
    private val io = object : DraftFileIO by AtomicDraftFiles {
        var failView = false
        override fun replace(file: File, bytes: ByteArray) {
            if (failView && bytes.toString(Charsets.UTF_8).contains("owned-picker-view")) throw IOException("synthetic picker Keep failure")
            AtomicDraftFiles.replace(file, bytes)
        }
    }
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        store = DurableDraftStore(directory = temporary.newFolder(), files = io, dispatcher = dispatcher)
        contexts = PickerContextDrafts(store)
        custom = CustomExerciseDrafts(store = store, repository = deps.exerciseRepository,
            maintenance = deps.dbMaintenance, restoreOpen = { false }, dispatcher = dispatcher)
    }
    @After fun close() {
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        deps.close()
        Dispatchers.resetMain()
    }
    private fun editor(id: String) = RoutineEditorViewModel(
        application = ApplicationProvider.getApplicationContext(), savedStateHandle = SavedStateHandle(mapOf("routineId" to id)),
        container = deps, durable = RoutineDrafts(store = store, maintenance = deps.dbMaintenance,
            restoreOpen = { false }, readRecord = deps.routineRepository::editorRecord,
            inspectSaved = deps.routineRepository::inspectEditorCommand,
            commitLocked = deps.routineRepository::commitEditorCommandLocked, dispatcher = dispatcher),
        pickerContexts = contexts, customLiftDrafts = custom,
    ).also { models += it }
    private suspend fun open(vm: RoutineEditorViewModel) {
        vm.uiState.awaitFirst { !it.isLoading }
        vm.setPickerVisible(true)
        val recovery = checkNotNull(vm.pickerRecovery)
        recovery.view.open()
        recovery.view.status.awaitFirst { !it.busy }
    }

    @Test fun aFreshEditorRestoresItsPickerContextWithoutReplayingRoutineSelections() = runBlocking {
        val row = deps.routineRepository.create("Synthetic picker owner")
        val before = deps.routineRepository.getById(row.id)
        val first = editor(row.id)
        open(first)
        val recovery = checkNotNull(first.pickerRecovery)
        val intended = PickerContextSnapshot(query = "  bench  ", muscle = CanonicalMuscle.CHEST,
            anchor = "ex-bench", index = 12, offsetDp = 26.5f)
        recovery.change(intended)
        recovery.leave { first.setPickerVisible(false) }
        first.uiState.awaitFirst { !it.showExercisePicker }
        first.clearAndJoinForTest(); models.remove(first)
        val reopened = editor(row.id)
        open(reopened)
        val returned = checkNotNull(reopened.pickerRecovery)
        assertEquals(intended, returned.view.fields.value)
        assertTrue(returned.view.status.value.resumed)
        assertEquals(intended.query, reopened.uiState.awaitFirst { it.searchQuery == intended.query }.searchQuery)
        assertEquals(before, deps.routineRepository.getById(row.id))
    }

    @Test fun failedCloseStaysOnTheRoutineUntilTheExactViewIsKept() = runBlocking {
        val row = deps.routineRepository.create("Synthetic picker owner")
        val vm = editor(row.id)
        open(vm)
        val recovery = checkNotNull(vm.pickerRecovery)
        io.failView = true
        recovery.change(PickerContextSnapshot(query = "  exact latest  "))
        var departures = 0
        recovery.leave { departures++; vm.setPickerVisible(false) }
        recovery.view.status.awaitFirst { it.error != null && !it.busy }
        assertEquals(0, departures)
        assertTrue(vm.uiState.awaitFirst { it.showExercisePicker }.showExercisePicker)
        assertEquals("  exact latest  ", recovery.view.fields.value.query)
        io.failView = false
        recovery.view.keep()
        vm.uiState.awaitFirst { !it.showExercisePicker }
        assertEquals(1, departures)
        assertTrue(deps.routineRepository.getById(row.id)!!.exercises.isEmpty())
    }

    @Test fun createAndSelectCommitsOneCanonicalLiftAndOneRoutineRowBeforeClearingItsDraft() = runBlocking {
        val row = deps.routineRepository.create("Synthetic picker owner")
        val vm = editor(row.id)
        open(vm)
        val recovery = checkNotNull(vm.pickerRecovery)
        recovery.change(PickerContextSnapshot(query = "  Controlled custom row  "))
        recovery.openCreate()
        recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.EDITING }
        recovery.create.update(name = "  Controlled custom row  ", muscle = "Back", loadType = LoadType.STACK, notes = "  Exact notes  ")
        val identity = checkNotNull(recovery.create.status.value.fields).createId
        recovery.saveCreate()
        recovery.saveCreate()
        recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.CLOSED }
        recovery.view.status.awaitFirst { !it.busy }
        val saved = deps.routineRepository.getById(row.id)!!
        assertEquals(listOf(identity), saved.exercises.map { it.exercise.id })
        assertEquals(listOf(identity), deps.database.exerciseDao().getAllCustom().map { it.id })
        assertEquals("Exact notes", saved.exercises.single().exercise.notes)
        assertFalse(recovery.view.fields.value.creating)
        assertEquals(null, recovery.view.fields.value.customDraftId)
    }
}
