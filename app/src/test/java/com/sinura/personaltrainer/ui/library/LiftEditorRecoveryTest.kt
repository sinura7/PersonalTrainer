package com.sinura.personaltrainer.ui.library

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.CustomExerciseDrafts
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.testutil.awaitFirst
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
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

/** Coupled controller, durable files and real Room: effects are asserted separately from settlement. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LiftEditorRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var deps: FakeAppDependencies
    private lateinit var service: CustomExerciseDrafts
    private lateinit var controller: LiftEditorRecovery
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private val fields = AtomicReference<ExerciseEditorDraft?>()
    private val saved = ConcurrentLinkedQueue<String>()
    private val savedEffect = CompletableDeferred<String>()
    private val generation = AtomicLong()
    private val files = FaultFiles()

    @Before fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        service = CustomExerciseDrafts(store = DurableDraftStore(directory = temporary.newFolder(), files = files),
            repository = deps.exerciseRepository, maintenance = deps.dbMaintenance, restoreOpen = { false })
        controller = freshController()
    }
    @After fun tearDown() { runBlocking { job.cancelAndJoin() }; deps.close() }
    private fun freshController() = LiftEditorRecovery(scope = scope, drafts = service, onFields = fields::set,
        onSaved = { saved.add(it); savedEffect.complete(it) },
        currentWriteGeneration = generation::get, canWrite = { it == generation.get() })
    private suspend fun await(phase: LiftEditorPhase) { controller.status.awaitFirst { it.phase == phase } }
    private suspend fun open() { controller.open(ExerciseEditorDraft(muscleGroup = "Back")); await(LiftEditorPhase.EDITING) }
    private fun authored() = checkNotNull(fields.get()).copy(name = "  Controlled row  ", notes = "  Raw\nnotes  ", loadType = LoadType.STACK)

    @Test fun closeAndFreshControllerRestoreLatestAuthoredFieldsWithoutSaving() = runBlocking {
        open()
        val raw = authored()
        controller.update(raw)
        controller.close()
        await(LiftEditorPhase.CLOSED)
        assertNull(fields.get())
        controller = freshController()
        open()
        assertEquals(raw, fields.get())
        assertEquals("Draft resumed", controller.status.value.message)
        assertTrue(saved.isEmpty())
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun synchronousReservationIgnoresDuplicateSaveAndCloseUntilTheExactCommitFinishes() = runBlocking {
        open()
        controller.update(authored())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blocker = async(Dispatchers.Default) { deps.dbMaintenance.withMaintenanceLock { entered.complete(Unit); release.await() } }
        withTimeout(30_000) { entered.await() }
        controller.save()
        assertEquals(LiftEditorPhase.SAVING, controller.status.value.phase)
        controller.save()
        controller.close()
        assertEquals(LiftEditorPhase.SAVING, controller.status.value.phase)
        release.complete(Unit)
        blocker.await()
        await(LiftEditorPhase.CLOSED)
        withTimeout(30_000) { savedEffect.await() }
        assertEquals(listOf("Controlled row"), saved.toList())
        assertNull(fields.get())
        val row = deps.database.exerciseDao().getAllCustom().single()
        assertEquals("Controlled row", row.name)
        assertEquals("Raw\nnotes", row.notes)
        assertEquals(LoadType.STACK.name, row.loadType)
    }

    @Test fun changedLibraryReadGenerationRefusesAnAlreadyAdmittedSuspendedSaveAndCheckAllowsExactRetry() = runBlocking {
        open()
        val raw = authored()
        controller.update(raw)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blocker = async(Dispatchers.Default) { deps.dbMaintenance.withMaintenanceLock { entered.complete(Unit); release.await() } }
        withTimeout(30_000) { entered.await() }
        controller.save()
        generation.incrementAndGet()
        release.complete(Unit)
        blocker.await()
        await(LiftEditorPhase.UNCERTAIN)
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
        controller.check()
        await(LiftEditorPhase.RETRYABLE)
        controller.update(raw.copy(name = "Must stay frozen"))
        assertEquals(raw, fields.get())
        controller.save()
        await(LiftEditorPhase.CLOSED)
        withTimeout(30_000) { savedEffect.await() }
        assertEquals(listOf("Controlled row"), saved.toList())
        assertEquals("Controlled row", deps.database.exerciseDao().getAllCustom().single().name)
    }

    @Test fun discardWithLostAcknowledgementChecksToClosedWithoutEmittingSaved() = runBlocking {
        open()
        files.after = true
        controller.discard()
        await(LiftEditorPhase.DISCARD_UNCERTAIN)
        assertTrue(saved.isEmpty())
        assertFalse(controller.status.value.editable)
        controller.check()
        await(LiftEditorPhase.CLOSED)
        assertNull(fields.get())
        assertTrue(saved.isEmpty())
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
        controller = freshController()
        open()
        assertEquals("", fields.get()!!.name)
    }

    @Test fun failedCloseLeavesFieldsActionableAndASecondCloseDurablyKeepsThem() = runBlocking {
        open()
        files.before = true
        controller.close()
        await(LiftEditorPhase.CHECKPOINT_FAILED)
        assertTrue(controller.status.value.editable)
        val raw = authored()
        controller.update(raw)
        controller.close()
        await(LiftEditorPhase.CLOSED)
        controller = freshController()
        open()
        assertEquals(raw, fields.get())
        assertTrue(saved.isEmpty())
    }

    private class FaultFiles : DraftFileIO {
        @Volatile var before = false
        @Volatile var after = false
        override fun read(file: File): ByteArray? = AtomicDraftFiles.read(file)
        override fun replace(file: File, bytes: ByteArray) {
            if (before) { before = false; throw IOException("Synthetic checkpoint failure") }
            AtomicDraftFiles.replace(file, bytes)
            if (after) { after = false; throw IOException("Synthetic acknowledgement failure") }
        }
    }
}
