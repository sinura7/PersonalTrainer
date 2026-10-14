package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.draft.CustomExerciseDrafts
import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.CustomLiftResume
import com.sinura.personaltrainer.data.draft.DraftGenerationChange
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.DurableWorkoutDrafts
import com.sinura.personaltrainer.data.draft.PickerContextDrafts
import com.sinura.personaltrainer.data.draft.PickerSwapDrafts
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.testutil.TestWorkoutFixture
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.components.PickerCreatePhase
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class WorkoutPickerRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var fixture: TestWorkoutFixture
    private lateinit var replacement: Exercise
    private lateinit var store: DurableDraftStore
    private lateinit var swaps: PickerSwapDrafts
    private lateinit var contexts: PickerContextDrafts
    private lateinit var custom: CustomExerciseDrafts
    private var failSwap = false
    private var failChildCleanup = false
    private var loseChildCleanupAck = false
    private var pauseChildRead = false
    private val childReadEntered = CompletableDeferred<Unit>()
    private val releaseChildRead = CountDownLatch(1)
    private var swapCommitCalls = 0
    private val files = object : DraftFileIO by AtomicDraftFiles {
        override fun read(file: File): ByteArray? {
            val bytes = AtomicDraftFiles.read(file) ?: return null
            if (pauseChildRead && JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                    .get("payloadFormat")?.asString == "custom-lift") {
                pauseChildRead = false
                childReadEntered.complete(Unit)
                check(releaseChildRead.await(10, TimeUnit.SECONDS)) { "Synthetic paused child read was not released" }
            }
            return bytes
        }
        override fun replace(file: File, bytes: ByteArray) {
            val record = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            if (failChildCleanup && record.get("payloadFormat")?.asString == "custom-lift" && record.get("draft").isJsonNull)
                throw IOException("Synthetic accepted-child cleanup failure")
            AtomicDraftFiles.replace(file, bytes)
            if (loseChildCleanupAck && record.get("payloadFormat")?.asString == "custom-lift" && record.get("draft").isJsonNull) {
                loseChildCleanupAck = false
                throw IOException("Synthetic completed-child cleanup acknowledgement failure")
            }
        }
    }
    private val models = mutableListOf<ActiveWorkoutViewModel>()

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        fixture = seedTestWorkout(deps)
        replacement = insertTestExercise(deps, "synthetic-new-press", "Synthetic new press", "Chest")
        store = DurableDraftStore(directory = temporary.newFolder(), dispatcher = dispatcher, files = files)
        contexts = PickerContextDrafts(store)
        custom = CustomExerciseDrafts(store = store, repository = deps.exerciseRepository,
            maintenance = deps.dbMaintenance, restoreOpen = { false }, dispatcher = Dispatchers.IO)
        swaps = PickerSwapDrafts(store = store, repository = deps.workoutRepository,
            maintenance = deps.dbMaintenance, restoreOpen = { false }, dispatcher = dispatcher)
        deps.workoutDraftCache.persistence = DurableWorkoutDrafts(store = store, repository = deps.workoutRepository,
            maintenance = deps.dbMaintenance, restoreOpen = { false }, dispatcher = dispatcher)
        deps.workoutRepository.beforeWorkoutDestruction = { event ->
            swapCommitCalls++
            check(!failSwap) { "Synthetic swap write failure" }
            check(store.invalidateOnce(event) is DraftGenerationChange.Changed)
        }
    }
    @After fun close() {
        releaseChildRead.countDown()
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        deps.restTimerController.stop(); deps.close(); Dispatchers.resetMain()
    }
    private fun model() = ActiveWorkoutViewModel(application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to fixture.session.id)), container = deps,
        undoTimeout = UndoTimeoutProvider { it.toLong() }, pickerContexts = contexts, customLiftDrafts = custom,
        swapDrafts = swaps).also(models::add)
    private suspend fun ready(vm: ActiveWorkoutViewModel) {
        vm.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READY }
        vm.uiState.awaitFirst { it.canLog }
    }
    private suspend fun openView(vm: ActiveWorkoutViewModel) {
        vm.uiState.awaitFirst { it.showExercisePicker && !it.mutating }
        val recovery = checkNotNull(vm.pickerRecovery)
        recovery.view.open()
        recovery.view.status.awaitFirst { !it.busy }
        assertNull(recovery.view.status.value.error)
    }

    @Test fun aDoubleSelectionSwapsOnceAndKeepsItsTargetsBeforeClosing() = runBlocking {
        withTimeout(30_000) {
            val vm = model(); ready(vm)
            vm.requestSwap(); openView(vm)
            vm.addExercise(replacement); vm.addExercise(replacement)
            vm.uiState.awaitFirst { !it.showExercisePicker && !it.mutating && it.selectedExerciseId == replacement.id }
            val saved = deps.workoutRepository.getSession(fixture.session.id)!!
            val original = fixture.session.exercises.single()
            val actual = saved.exercises.single()
            assertEquals(replacement.id, actual.exercise.id)
            assertEquals(original.targetSets, actual.targetSets)
            assertEquals(original.targetReps, actual.targetReps)
            assertEquals(original.restSeconds, actual.restSeconds)
            assertNull(actual.targetWeightKg)
            assertTrue(saved.sets.isEmpty())
            assertNull(swaps.openPending(fixture.session.id))
        }
    }

    @Test fun reopeningAnUncommittedSwapRequiresTheExplicitExactRetry() = runBlocking {
        withTimeout(30_000) {
            val original = fixture.session.exercises.single()
            val handle = swaps.open(fixture.session.id, original.id)
            swaps.admit(handle, fixture.session.id, original.id, replacement)
            val intended = handle.checkpoint.pending!!.value
            val vm = model(); ready(vm)
            vm.setPickerVisible(true); openView(vm)
            assertTrue(vm.uiState.value.swapping)
            assertEquals("Retry exact swap", vm.pickerRecovery!!.retryLabel())
            assertFalse(vm.pickerRecovery!!.canSelect())
            assertEquals(fixture.exercise.id, deps.workoutRepository.getSession(fixture.session.id)!!.exercises.single().exercise.id)
            vm.retryPicker()
            vm.uiState.awaitFirst { !it.showExercisePicker && !it.mutating }
            assertEquals(listOf(intended.after), deps.database.workoutDao().getAllSessionExercises())
            assertNull(swaps.openPending(fixture.session.id))
        }
    }

    @Test fun aCommittedSwapReopensAsContinueWithoutRewritingTheWorkout() = runBlocking {
        withTimeout(30_000) {
            val original = fixture.session.exercises.single()
            val handle = swaps.open(fixture.session.id, original.id)
            swaps.admit(handle, fixture.session.id, original.id, replacement)
            swaps.execute(handle) { true }
            val intended = deps.database.workoutDao().getAllSessionExercises()
            val vm = model(); ready(vm)
            vm.setPickerVisible(true); openView(vm)
            assertEquals("Continue", vm.pickerRecovery!!.retryLabel())
            assertEquals(intended, deps.database.workoutDao().getAllSessionExercises())
            vm.retryPicker()
            vm.uiState.awaitFirst { !it.showExercisePicker && !it.mutating }
            assertEquals(intended, deps.database.workoutDao().getAllSessionExercises())
            assertNull(swaps.openPending(fixture.session.id))
        }
    }

    @Test fun createAndSwapSettlesTheChildAcrossTheWorkoutGenerationChange() = runBlocking {
        withTimeout(30_000) {
            val vm = model(); ready(vm)
            vm.requestSwap(); openView(vm)
            val recovery = checkNotNull(vm.pickerRecovery)
            recovery.openCreate()
            recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.EDITING }
            recovery.create.update(name = "  Synthetic controlled replacement  ", muscle = "Chest",
                loadType = LoadType.EXTERNAL, notes = "  Exact private notes  ")
            val identity = recovery.create.status.value.fields!!.createId
            recovery.saveCreate(); recovery.saveCreate()
            vm.uiState.awaitFirst { !it.showExercisePicker && !it.mutating && it.selectedExerciseId == identity }
            val saved = deps.workoutRepository.getSession(fixture.session.id)!!
            assertEquals(listOf(identity), saved.exercises.map { it.exercise.id })
            assertEquals(listOf(identity), deps.database.exerciseDao().getAllCustom().map { it.id })
            assertEquals("Exact private notes", saved.exercises.single().exercise.notes)
            assertEquals(PickerCreatePhase.CLOSED, recovery.create.status.value.phase)
            assertNull(swaps.openPending(fixture.session.id))
            assertFalse(recovery.view.fields.value.creating)
        }
    }

    @Test fun aSavedCustomLiftWithAFailedSwapHasCheckThenExactRetryWithoutAnotherCreate() = runBlocking {
        withTimeout(30_000) {
            val vm = model(); ready(vm)
            vm.requestSwap(); openView(vm)
            val recovery = checkNotNull(vm.pickerRecovery)
            recovery.openCreate()
            recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.EDITING }
            recovery.create.update(name = "Synthetic failed parent swap", muscle = "Chest", loadType = LoadType.EXTERNAL, notes = "")
            val identity = recovery.create.status.value.fields!!.createId
            failSwap = true
            recovery.saveCreate()
            recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.SAVED }
            vm.uiState.awaitFirst { it.pickerProgress.canRetry && !it.mutating }
            assertEquals("Check again", recovery.retryLabel())
            assertFalse(recovery.canSelect())
            assertEquals(fixture.exercise.id, deps.workoutRepository.getSession(fixture.session.id)!!.exercises.single().exercise.id)
            failSwap = false
            vm.retryPicker()
            vm.uiState.awaitFirst { it.pickerProgress.canRetry && !it.mutating && recovery.retryLabel() == "Retry exact swap" }
            assertEquals(fixture.exercise.id, deps.workoutRepository.getSession(fixture.session.id)!!.exercises.single().exercise.id)
            vm.retryPicker()
            vm.uiState.awaitFirst { !it.showExercisePicker && !it.mutating }
            assertEquals(listOf(identity), deps.database.exerciseDao().getAllCustom().map { it.id })
            assertEquals(listOf(identity), deps.workoutRepository.getSession(fixture.session.id)!!.exercises.map { it.exercise.id })
            assertEquals(PickerCreatePhase.CLOSED, recovery.create.status.value.phase)
            assertNull(swaps.openPending(fixture.session.id))
        }
    }

    @Test fun anUnreadableRecoveryLinkHasAnActionableResetInTheActualWorkoutCaller() = runBlocking {
        withTimeout(30_000) {
            val original = fixture.session.exercises.single()
            val handle = swaps.open(fixture.session.id, original.id)
            swaps.admit(handle, fixture.session.id, original.id, replacement)
            temporary.root.walkTopDown().filter { it.isFile && it.readText().contains("picker-swap-link") }
                .single().writeText("{synthetic-invalid")
            val vm = model(); ready(vm)
            vm.setPickerVisible(true); openView(vm)
            vm.uiState.awaitFirst { it.pickerProgress.canRetry && !it.mutating }
            val recovery = checkNotNull(vm.pickerRecovery)
            assertFalse(recovery.canSelect())
            recovery.discardPrivate()
            recovery.view.status.awaitFirst { !it.busy && recovery.canSelect() }
            assertEquals(fixture.exercise.id, deps.workoutRepository.getSession(fixture.session.id)!!.exercises.single().exercise.id)
            vm.setPickerVisible(false)
            recovery.view.closeVisit(); recovery.create.closeVisit()
            vm.requestSwap(); openView(vm)
            vm.addExercise(replacement)
            vm.uiState.awaitFirst { !it.showExercisePicker && !it.mutating }
            assertEquals(listOf(replacement.id), deps.workoutRepository.getSession(fixture.session.id)!!.exercises.map { it.exercise.id })
        }
    }

    @Test fun callerOwnersSeparateModeAndAmbiguousImportedIdPairs() {
        assertTrue(workoutPickerOwner("s:swap:t", "u") != workoutPickerOwner("s", "t:swap:u"))
        assertTrue(workoutPickerOwner("s", null) != workoutPickerOwner("s:add", "u"))
        assertTrue(workoutPickerOwner("s", null) != workoutPickerOwner("s", "add"))
    }

    @Test fun acceptedParentWithFailedChildCleanupCanContinueWithoutAnotherBusinessWrite() = runBlocking {
        withTimeout(30_000) {
            val vm = model(); ready(vm)
            vm.requestSwap(); openView(vm)
            val recovery = checkNotNull(vm.pickerRecovery)
            recovery.openCreate(); recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.EDITING }
            recovery.create.update(name = "Synthetic saved cleanup", muscle = "Chest", loadType = LoadType.EXTERNAL, notes = "")
            val identity = recovery.create.status.value.fields!!.createId
            failChildCleanup = true
            recovery.saveCreate()
            recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.SAVED && it.message?.contains("Selection was not confirmed") == true }
            val once = deps.database.workoutDao().getAllSessionExercises()
            assertEquals(identity, once.single().exerciseId)
            assertEquals("Continue", recovery.retryLabel())
            failChildCleanup = false
            recovery.useSaved()
            vm.uiState.awaitFirst { !it.showExercisePicker && !it.mutating }
            assertEquals(once, deps.database.workoutDao().getAllSessionExercises())
            assertEquals(listOf(identity), deps.database.exerciseDao().getAllCustom().map { it.id })
            assertEquals(PickerCreatePhase.CLOSED, recovery.create.status.value.phase)
            assertNull(swaps.openPending(fixture.session.id))
        }
    }

    @Test fun listModeRemountRetriesItsSavedCustomHandoffAndClearsTheSameChild() = runBlocking {
        withTimeout(30_000) {
            val vm = model(); ready(vm)
            vm.requestSwap(); openView(vm)
            val recovery = checkNotNull(vm.pickerRecovery)
            recovery.openCreate(); recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.EDITING }
            recovery.create.update(name = "Synthetic remounted handoff", muscle = "Chest", loadType = LoadType.EXTERNAL, notes = "")
            val identity = recovery.create.status.value.fields!!.createId
            failSwap = true; recovery.saveCreate()
            recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.SAVED }
            vm.uiState.awaitFirst { it.pickerProgress.canRetry && !it.mutating }
            recovery.backToList()
            recovery.view.fields.awaitFirst { !it.creating && it.customDraftId == identity }
            recovery.leave { vm.setPickerVisible(false) }
            vm.uiState.awaitFirst { !it.showExercisePicker }
            recovery.view.closeVisit(); recovery.create.closeVisit()
            vm.setPickerVisible(true); openView(vm)
            assertEquals(PickerCreatePhase.CLOSED, recovery.create.status.value.phase)
            assertEquals("Retry exact swap", recovery.retryLabel())
            failSwap = false; vm.retryPicker()
            vm.uiState.awaitFirst { !it.showExercisePicker && !it.mutating }
            assertEquals(listOf(identity), deps.workoutRepository.getSession(fixture.session.id)!!.exercises.map { it.exercise.id })
            assertEquals(PickerCreatePhase.CLOSED, recovery.create.status.value.phase)
            assertNull(recovery.view.fields.value.customDraftId)
            assertNull(swaps.openPending(fixture.session.id))
        }
    }

    @Test fun lostClearAcknowledgementRemountConfirmsTheSameClearedChildWithoutSeedingAnother() = runBlocking {
        withTimeout(30_000) {
            val vm = model(); ready(vm)
            vm.requestSwap(); openView(vm)
            val recovery = checkNotNull(vm.pickerRecovery)
            recovery.openCreate(); recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.EDITING }
            recovery.create.update(name = "Synthetic lost cleanup acknowledgement", muscle = "Chest", loadType = LoadType.EXTERNAL, notes = "")
            val identity = recovery.create.status.value.fields!!.createId
            loseChildCleanupAck = true; recovery.saveCreate()
            recovery.create.status.awaitFirst { it.phase == PickerCreatePhase.SAVED && it.message?.contains("Selection was not confirmed") == true }
            val once = deps.database.workoutDao().getAllSessionExercises()
            val childFiles = privateChildNames()
            recovery.view.closeVisit(); recovery.create.closeVisit()
            openView(vm)
            recovery.openCreate()
            recovery.view.fields.awaitFirst { it.customDraftId == null && !it.creating }
            vm.uiState.awaitFirst { it.pickerProgress.canRetry && !it.mutating }
            assertEquals("Continue", recovery.retryLabel())
            assertEquals(childFiles, privateChildNames())
            assertEquals(listOf(identity), deps.database.exerciseDao().getAllCustom().map { it.id })
            vm.retryPicker()
            vm.uiState.awaitFirst { !it.showExercisePicker && !it.mutating }
            assertEquals(once, deps.database.workoutDao().getAllSessionExercises())
            assertNull(swaps.openPending(fixture.session.id))
        }
    }

    private fun privateChildNames(): Set<String> = temporary.root.walkTopDown().filter { it.isFile }.filter {
        JsonParser.parseString(it.readText()).asJsonObject.get("payloadFormat")?.asString == "custom-lift"
    }.map { it.name }.toSet()

    @Test fun disposalDuringExactChildResumeRetiresItsJobAndLeavesAnExplicitContinueAfterRemount() = runBlocking {
        withTimeout(30_000) {
            val original = fixture.session.exercises.single()
            val first = model(); ready(first)
            first.requestSwap(); openView(first)
            val initial = checkNotNull(first.pickerRecovery)
            initial.openCreate(); initial.create.status.awaitFirst { it.phase == PickerCreatePhase.EDITING }
            initial.create.update(name = "Synthetic disposed resume", muscle = "Chest", loadType = LoadType.EXTERNAL, notes = "")
            val identity = initial.create.status.value.fields!!.createId
            failSwap = true; initial.saveCreate()
            initial.create.status.awaitFirst { it.phase == PickerCreatePhase.SAVED }
            first.uiState.awaitFirst { it.pickerProgress.canRetry && !it.mutating }
            initial.backToList()
            initial.view.fields.awaitFirst { !it.creating && it.customDraftId == identity }
            initial.leave { first.setPickerVisible(false) }
            first.uiState.awaitFirst { !it.showExercisePicker }
            first.clearAndJoinForTest(); models.remove(first)
            deps.workoutDraftCache.clear(fixture.session.id)
            val vm = model(); ready(vm)
            vm.setPickerVisible(true); openView(vm)
            val recovery = checkNotNull(vm.pickerRecovery)
            failSwap = false; pauseChildRead = true
            vm.retryPicker()
            withTimeout(15_000) { childReadEntered.await() }
            val once = deps.database.workoutDao().getAllSessionExercises()
            val commits = swapCommitCalls
            assertEquals(identity, once.single().exerciseId)
            recovery.view.closeVisit(); recovery.create.closeVisit()
            releaseChildRead.countDown()
            vm.uiState.awaitFirst { it.pickerProgress.canRetry && !it.mutating }
            assertEquals("Continue", recovery.retryLabel())
            assertTrue(vm.uiState.value.showExercisePicker)
            assertEquals(PickerCreatePhase.CLOSED, recovery.create.status.value.phase)
            openView(vm)
            assertEquals(identity, recovery.view.fields.value.customDraftId)
            val owner = workoutPickerOwner(fixture.session.id, original.id)
            assertTrue(custom.resumeForPicker(owner, identity) is CustomLiftResume.Existing)
            vm.retryPicker()
            vm.uiState.awaitFirst { !it.showExercisePicker && !it.mutating }
            assertEquals(commits, swapCommitCalls)
            assertEquals(once, deps.database.workoutDao().getAllSessionExercises())
            assertEquals(listOf(identity), deps.database.exerciseDao().getAllCustom().map { it.id })
            assertNull(recovery.view.fields.value.customDraftId)
            assertNull(swaps.openPending(fixture.session.id))
            assertEquals(CustomLiftResume.AlreadyCleared, custom.resumeForPicker(owner, identity))
        }
    }
}
