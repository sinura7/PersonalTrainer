package com.sinura.personaltrainer.ui.components

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.CustomExerciseDrafts
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.testutil.awaitFirst
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PickerCreateRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var deps: FakeAppDependencies
    private lateinit var drafts: CustomExerciseDrafts
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private val owner = DraftOwner(DraftForm.ROUTINE, "synthetic-routine")
    private var owned = true
    private lateinit var controller: PickerCreateRecovery
    private val io = object : DraftFileIO by AtomicDraftFiles {
        @Volatile var after = false
        @Volatile var heldAdmission = false
        val admissionEntered = CountDownLatch(1)
        val admissionRelease = CountDownLatch(1)
        override fun replace(file: File, bytes: ByteArray) {
            if (heldAdmission && bytes.toString(Charsets.UTF_8).contains("\"phase\":\"PENDING\"")) {
                admissionEntered.countDown()
                check(admissionRelease.await(30, TimeUnit.SECONDS))
                heldAdmission = false
            }
            AtomicDraftFiles.replace(file, bytes)
            if (after) { after = false; throw IOException("synthetic lost acknowledgement") }
        }
    }
    @Before fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        drafts = CustomExerciseDrafts(store = DurableDraftStore(directory = temporary.newFolder(), files = io),
            repository = deps.exerciseRepository, maintenance = deps.dbMaintenance, restoreOpen = { false })
        controller = fresh()
    }
    @After fun close() { runBlocking { job.cancelAndJoin() }; deps.close() }
    private fun fresh() = PickerCreateRecovery(scope = scope, drafts = drafts,
        repository = deps.exerciseRepository, stillOwned = ::owns, ownedAtCommit = ::owns)
    private fun owns(requested: DraftOwner): Boolean = owned && requested == owner
    private suspend fun await(phase: PickerCreatePhase) { controller.status.awaitFirst { it.phase == phase } }
    private fun draftReady(id: String) { assertTrue(id.startsWith("ex-custom-")) }
    private fun ignoredCompletion() = Unit
    private suspend fun open() {
        controller.open(requested = owner, name = "New row", muscle = "Back", onReady = ::draftReady)
        await(PickerCreatePhase.EDITING)
    }

    @Test fun confirmedDiscardOfSavedCreateKeepsItsLibraryRecordAndStartsANewIdentity() = runBlocking {
        open()
        val originalId = checkNotNull(controller.status.value.fields).createId
        controller.save()
        await(PickerCreatePhase.SAVED)
        val saved = deps.exerciseRepository.getById(originalId)
        assertTrue(saved != null)
        val cleared = CompletableDeferred<Unit>()
        val onDiscarded: () -> Unit = { cleared.complete(Unit) }
        controller.discard(requested = owner, onDiscarded = onDiscarded)
        withTimeout(30_000) { cleared.await() }
        assertEquals(saved, deps.exerciseRepository.getById(originalId))
        controller.open(requested = owner, name = "Another row", muscle = "Back", onReady = ::draftReady)
        await(PickerCreatePhase.EDITING)
        assertTrue(checkNotNull(controller.status.value.fields).createId != originalId)
        assertEquals(1, deps.database.exerciseDao().getAllCustom().size)
    }

    @Test fun orderlyKeepAndReopeningRestoreRawFieldsWithoutCreatingALibraryRecord() = runBlocking {
        open()
        controller.update(name = "  Raw row 🏋  ", muscle = "Back", loadType = LoadType.STACK, notes = "  Raw\nnotes  ")
        val intended = controller.status.value.fields
        val kept = CompletableDeferred<Unit>()
        controller.keep { kept.complete(Unit) }
        withTimeout(30_000) { kept.await() }
        controller.closeVisit()
        controller = fresh()
        open()
        assertEquals(intended, controller.status.value.fields)
        assertEquals("Draft resumed", controller.status.value.message)
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun duplicateSaveCannotCreateTwiceAndRecoveredSavedResultDoesNotSelectAutomatically() = runBlocking {
        open()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blocker = async(Dispatchers.Default) { deps.dbMaintenance.withMaintenanceLock { entered.complete(Unit); release.await() } }
        withTimeout(30_000) { entered.await() }
        controller.save()
        controller.save()
        assertEquals(PickerCreatePhase.SAVING, controller.status.value.phase)
        release.complete(Unit)
        blocker.await()
        await(PickerCreatePhase.SAVED)
        val once = deps.database.exerciseDao().getAllCustom()
        controller.closeVisit()
        controller = fresh()
        controller.open(requested = owner, name = "Must not replace", muscle = "Chest", onReady = ::draftReady)
        await(PickerCreatePhase.SAVED)
        assertEquals(once, deps.database.exerciseDao().getAllCustom())
        assertEquals(1, once.size)
        val selected = AtomicInteger()
        val complete = CompletableDeferred<Unit>()
        suspend fun selectOnce(exercise: Exercise): Boolean { assertEquals(once.single().id, exercise.id); selected.incrementAndGet(); return true }
        fun completed() { complete.complete(Unit) }
        controller.useSaved(select = ::selectOnce, onComplete = ::completed)
        controller.useSaved(select = ::selectOnce, onComplete = ::ignoredCompletion)
        withTimeout(30_000) { complete.await() }
        assertEquals(1, selected.get())
        assertEquals(once, deps.database.exerciseDao().getAllCustom())
    }

    @Test fun lostAdmissionAcknowledgementIsInspectedBeforeRetryAndKeepsFrozenValues() = runBlocking {
        open()
        io.after = true
        controller.save()
        await(PickerCreatePhase.UNCERTAIN)
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
        controller.check()
        await(PickerCreatePhase.RETRYABLE)
        val frozen = controller.status.value.fields
        controller.update(name = "Must not change", muscle = "Chest", loadType = LoadType.BODYWEIGHT, notes = "changed")
        assertEquals(frozen, controller.status.value.fields)
        controller.save()
        await(PickerCreatePhase.SAVED)
        assertEquals("New row", deps.database.exerciseDao().getAllCustom().single().name)
    }

    @Test fun failedParentSelectionRetainsTheExactSavedLiftForAnotherExplicitSelection() = runBlocking {
        open()
        controller.save()
        await(PickerCreatePhase.SAVED)
        val once = deps.database.exerciseDao().getAllCustom()
        var completed = 0
        suspend fun refuse(exercise: Exercise): Boolean { assertEquals(once.single().id, exercise.id); return false }
        fun firstCompleted() { completed++ }
        controller.useSaved(select = ::refuse, onComplete = ::firstCompleted)
        await(PickerCreatePhase.SAVED)
        assertEquals(0, completed)
        assertEquals(once, deps.database.exerciseDao().getAllCustom())
        val complete = CompletableDeferred<Unit>()
        suspend fun selectExact(exercise: Exercise): Boolean = exercise.id == once.single().id
        fun secondCompleted() { completed++; complete.complete(Unit) }
        controller.useSaved(select = ::selectExact, onComplete = ::secondCompleted)
        withTimeout(30_000) { complete.await() }
        assertEquals(1, completed)
        assertEquals(once, deps.database.exerciseDao().getAllCustom())
    }

    @Test fun missingOwnerRefusesSaveWithoutCreatingALift() = runBlocking {
        open()
        owned = false
        controller.save()
        await(PickerCreatePhase.CONFLICT)
        assertEquals("New row", controller.status.value.fields?.name)
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun retiringTheVisitBeforeCommitPreventsLateEffectsAndRetainsAnInspectableCommand() = runBlocking {
        open()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blocker = async(Dispatchers.Default) { deps.dbMaintenance.withMaintenanceLock { entered.complete(Unit); release.await() } }
        withTimeout(30_000) { entered.await() }
        var savedEffects = 0
        controller.save { savedEffects++ }
        controller.closeVisit()
        release.complete(Unit)
        blocker.await()
        controller = fresh()
        controller.open(requested = owner, name = "New row", muscle = "Back", onReady = ::draftReady)
        controller.status.awaitFirst { it.phase in setOf(PickerCreatePhase.EDITING, PickerCreatePhase.RETRYABLE) }
        assertEquals(0, savedEffects)
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun ownerLossDuringHeldAdmissionIsRecheckedBeforeTheBusinessWrite() = runBlocking {
        open()
        io.heldAdmission = true
        controller.save()
        assertTrue(io.admissionEntered.await(30, TimeUnit.SECONDS))
        owned = false
        io.admissionRelease.countDown()
        await(PickerCreatePhase.UNCERTAIN)
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
        controller.check()
        await(PickerCreatePhase.CONFLICT)
        assertEquals("New row", controller.status.value.fields?.name)
    }

    @Test fun reopeningAPendingCommandDoesNotPublishAnEditablePhaseBeforeInspection() = runBlocking {
        val pending = drafts.openForPicker(owner, "Back")
        drafts.admit(pending, pending.authored.copy(name = "Frozen row"))
        val phases = ConcurrentLinkedQueue<PickerCreatePhase>()
        val watching = scope.launch { controller.status.collect { phases.add(it.phase) } }
        controller.open(requested = owner, name = "Do not replace", muscle = "Chest", onReady = ::draftReady)
        await(PickerCreatePhase.RETRYABLE)
        watching.cancelAndJoin()
        assertFalse(phases.contains(PickerCreatePhase.EDITING))
        assertEquals("Frozen row", controller.status.value.fields?.name)
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }
}
