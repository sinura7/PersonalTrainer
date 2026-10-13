package com.sinura.personaltrainer.data.draft

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.ui.components.CompletedNotesForm
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CompletedNotesDraftsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val io = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var root: File
    private val owner = CompletedNoteOwner(CompletedNoteKind.ACTIVITY, "synthetic-completed-session")
    private val records = mutableMapOf<CompletedNoteOwner, CompletedNoteRecord>()
    private var atMs = 500L
    private var attempts = 0
    private var writes = 0
    private val attemptedTimes = mutableListOf<Long>()
    private var failBeforeBusiness = false
    private var loseBusinessAck = false
    private var failRead = false
    private var restoreOpen = false
    private val files = FaultFiles()

    @Before fun setUp() {
        Dispatchers.setMain(io)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = io)
        root = temporary.newFolder()
        records[owner] = CompletedNoteRecord("a".repeat(64), "Original")
    }
    @After fun tearDown() { deps.close(); Dispatchers.resetMain() }

    @Test fun rawFieldsReopenWithoutAnyBusinessWrite() = runBlocking {
        val first = service()
        first.keep(first.open(owner), "  My words\nunchanged  ")
        val reopened = service().open(owner)
        assertEquals("  My words\nunchanged  ", reopened.raw)
        assertTrue(reopened.resumed)
        assertFalse(reopened.problem)
        assertEquals(0, attempts)
        assertEquals("Original", records[owner]?.notes)
    }

    @Test fun exactSaveAndDuplicateActivationWriteOnce() = runBlocking {
        val store = service()
        val handle = store.open(owner)
        store.save(handle, "  Intended  ")
        store.save(handle, "  Intended  ")
        assertEquals("Intended", records[owner]?.notes)
        assertEquals(1, writes)
        assertNull(service().open(owner).raw)
    }

    @Test fun retryPreservesTheFrozenCommandTime() = runBlocking {
        val first = service()
        val handle = first.open(owner)
        failBeforeBusiness = true
        assertFalse(runCatching { first.save(handle, "Intended") }.isSuccess)
        atMs = 9_000L
        failBeforeBusiness = false
        val reopened = service()
        reopened.save(reopened.open(owner), "Intended")
        assertEquals(listOf(500L, 500L), attemptedTimes)
        assertEquals(1, writes)
    }

    @Test fun committedButLostBusinessAckSettlesWithoutReplay() = runBlocking {
        val first = service()
        loseBusinessAck = true
        assertFalse(runCatching { first.save(first.open(owner), "Intended") }.isSuccess)
        val reopened = service()
        reopened.save(reopened.open(owner), "Intended")
        assertEquals(1, attempts)
        assertEquals(1, writes)
        assertEquals("Intended", records[owner]?.notes)
        assertNull(reopened.open(owner).raw)
    }

    @Test fun conflictingNotesNeverAuthorizeRetry() = runBlocking {
        val first = service()
        failBeforeBusiness = true
        assertFalse(runCatching { first.save(first.open(owner), "Intended") }.isSuccess)
        records[owner] = checkNotNull(records[owner]).copy(notes = "Changed elsewhere")
        failBeforeBusiness = false
        val reopened = service()
        val conflict = reopened.open(owner)
        assertTrue(conflict.problem)
        assertEquals("Intended", conflict.raw)
        assertFalse(runCatching { reopened.save(conflict, "Intended") }.isSuccess)
        assertEquals("Changed elsewhere", records[owner]?.notes)
        assertEquals(0, writes)
    }

    @Test fun deletedOrRecreatedOwnersRetainDraftWithoutRecreation() = runBlocking {
        val first = service()
        first.keep(first.open(owner), "Intended")
        records.remove(owner)
        assertTrue(service().open(owner).problem)
        assertTrue(records.isEmpty())
        records[owner] = CompletedNoteRecord("b".repeat(64), "Original")
        val reopened = service()
        val conflict = reopened.open(owner)
        assertTrue(conflict.problem)
        assertEquals("Intended", conflict.raw)
        assertFalse(runCatching { reopened.save(conflict, "Intended") }.isSuccess)
        assertEquals(0, attempts)
    }

    @Test fun oldVisitsCannotReplaceANewerOwnersFields() = runBlocking {
        val store = service()
        val old = store.open(owner)
        val current = store.open(owner)
        store.keep(current, "Newer authored text")
        assertFalse(runCatching { store.keep(old, "Old text") }.isSuccess)
        assertEquals("Newer authored text", service().open(owner).raw)
        assertEquals(0, writes)
    }

    @Test fun oldHandlesCannotCrossAnAcceptedRestoreGeneration() = runBlocking {
        val store = service()
        val handle = store.open(owner)
        store.keep(handle, "Kept")
        val generation = checkNotNull(handle.fields).currentGeneration
        assertTrue(DurableDraftStore(root, io, files).advanceGeneration(generation) is DraftGenerationChange.Changed)
        assertFalse(runCatching { store.save(handle, "Kept") }.isSuccess)
        val recovered = store.open(owner)
        assertFalse(recovered.problem)
        assertEquals("Kept", recovered.raw)
        assertEquals(0, writes)
    }

    @Test fun newerRawFieldsSurviveAnOlderFrozenSave() = runBlocking {
        val store = service()
        val handle = store.open(owner)
        store.keep(handle, "Newer words still being typed")
        store.save(handle, "Older admitted words")
        assertEquals("Older admitted words", records[owner]?.notes)
        val recovered = store.open(owner)
        assertEquals("Newer words still being typed", recovered.raw)
        assertEquals("Older admitted words", recovered.fields?.draft?.before)
        store.save(recovered, "Newer words still being typed")
        assertEquals("Newer words still being typed", records[owner]?.notes)
        assertEquals(2, writes)
    }

    @Test fun privateKeepFailureBlocksBusinessAdmission() = runBlocking {
        val store = service()
        val handle = store.open(owner)
        files.failBeforeNext = true
        assertFalse(runCatching { store.save(handle, "Intended") }.isSuccess)
        assertEquals("Original", records[owner]?.notes)
        assertEquals(0, attempts)
    }

    @Test fun lostPrivateAcceptanceAckCannotReplayTheBusinessWrite() = runBlocking {
        val store = service(loseAcceptanceAck = true)
        assertFalse(runCatching { store.save(store.open(owner), "Intended") }.isSuccess)
        val reopened = service()
        reopened.save(reopened.open(owner), "Intended")
        assertEquals("Intended", records[owner]?.notes)
        assertEquals(1, writes)
        assertEquals(1, attempts)
        assertNull(reopened.open(owner).command?.pending)
    }

    @Test fun unavailableReadDoesNotTurnUncertainWorkIntoARetry() = runBlocking {
        val first = service()
        failBeforeBusiness = true
        assertFalse(runCatching { first.save(first.open(owner), "Intended") }.isSuccess)
        failRead = true
        val reopened = service()
        val unreadable = reopened.open(owner)
        assertTrue(unreadable.problem)
        assertEquals("Intended", unreadable.raw)
        assertFalse(runCatching { reopened.save(unreadable, "Intended") }.isSuccess)
        assertEquals(0, writes)
    }

    @Test fun explicitDiscardKeepsAlreadySavedBusinessNotes() = runBlocking {
        val store = service()
        val handle = store.open(owner)
        store.save(handle, "Accepted")
        store.keep(handle, "Unfinished new words")
        store.discard(handle)
        assertEquals("Accepted", records[owner]?.notes)
        assertNull(store.open(owner).raw)
        assertEquals(1, writes)
    }

    @Test fun restoreCoordinatorRefusesCompetingSubmission() = runBlocking {
        val store = service()
        val handle = store.open(owner)
        store.keep(handle, "Kept")
        restoreOpen = true
        assertFalse(runCatching { store.save(handle, "Kept") }.isSuccess)
        assertEquals("Original", records[owner]?.notes)
        assertEquals(0, attempts)
    }

    @Test fun retainedOrphanFieldsCanBeKeptWithoutRecreatingTheirBusinessOwner() = runBlocking {
        val first = service()
        first.keep(first.open(owner), "Orphan words")
        records.remove(owner)
        val reopened = service()
        val handle = reopened.open(owner)
        assertTrue(handle.problem)
        reopened.keepRetained(handle, "Newer retained orphan words")
        assertEquals("Newer retained orphan words", service().open(owner).raw)
        assertTrue(records.isEmpty())
        assertEquals(0, attempts)
    }

    @Test fun retainedOrphanAfterRestoreKeepsTextWithoutReconcilingItsBusinessCommand() = runBlocking {
        val first = service()
        val old = first.open(owner)
        failBeforeBusiness = true
        assertFalse(runCatching { first.save(old, "Owed words") }.isSuccess)
        val oldGeneration = checkNotNull(old.command).currentGeneration
        assertTrue(DurableDraftStore(root, io, files).advanceGeneration(oldGeneration) is DraftGenerationChange.Changed)
        records.remove(owner)
        val reopened = service()
        val retained = reopened.open(owner)
        assertTrue(retained.problem)
        reopened.keepRetained(retained, "Newer orphan words")
        val confirmed = service().open(owner)
        assertEquals("Newer orphan words", confirmed.raw)
        assertFalse(checkNotNull(confirmed.fields).needsReconciliation)
        assertTrue(checkNotNull(confirmed.command).needsReconciliation)
        assertFalse(runCatching { reopened.save(retained, "Newer orphan words") }.isSuccess)
        assertTrue(records.isEmpty())
        assertEquals(0, writes)
    }

    @Test fun readableNewerTextRestoresBeforeAcceptedCommandCleanupFailure() = runBlocking {
        val first = service(loseAcceptanceAck = true)
        val old = first.open(owner)
        first.keep(old, "Newer text")
        assertFalse(runCatching { first.save(old, "Accepted text") }.isSuccess)
        assertEquals("Accepted text", records[owner]?.notes)
        files.failBeforeNext = true
        var restored = ""
        val scope = CoroutineScope(SupervisorJob() + io)
        try {
            val form = CompletedNotesForm(
                store = service(), owner = owner, saved = SavedStateHandle(), scope = scope,
                restoreText = { raw, _ -> restored = raw }, resetText = { restored = it.orEmpty() },
            )
            form.start()
            form.awaitInitial()
            assertEquals("Newer text", restored)
            assertFalse(form.state.value.ready)
            assertTrue(form.state.value.error != null)
            assertEquals(1, writes)
            assertEquals("Accepted text", records[owner]?.notes)
        } finally { scope.cancel() }
    }

    @Test fun acceptedDiscardWithFailedOwnerReadOffersRetryAndRehydratesSavedNotes() = runBlocking {
        val store = service()
        store.keep(store.open(owner), "Unfinished text")
        var restored = ""
        val scope = CoroutineScope(SupervisorJob() + io)
        try {
            val form = CompletedNotesForm(
                store = store, owner = owner, saved = SavedStateHandle(), scope = scope,
                restoreText = { raw, _ -> restored = raw }, resetText = { restored = it.orEmpty() },
            )
            form.start()
            form.awaitInitial()
            assertEquals("Unfinished text", restored)
            failRead = true
            assertTrue(form.beginDiscard())
            assertTrue(form.discard())
            assertFalse(form.state.value.ready)
            assertTrue(form.state.value.error?.contains("discarded") == true)
            assertFalse(form.hasUnfinished)
            failRead = false
            form.retryPrivate()
            assertTrue(form.state.value.ready)
            assertEquals("Original", restored)
            assertNull(service().open(owner).raw)
            assertEquals(0, attempts)
        } finally { scope.cancel() }
    }

    @Test fun exactAcceptedRecoveryRetiresTheConsumedFormMarkersWithoutAnotherWrite() = runBlocking {
        val first = service(loseAcceptanceAck = true)
        assertFalse(runCatching { first.save(first.open(owner), "Accepted text") }.isSuccess)
        val saved = SavedStateHandle()
        var restored = ""
        val scope = CoroutineScope(SupervisorJob() + io)
        try {
            val form = CompletedNotesForm(
                store = service(), owner = owner, saved = saved, scope = scope,
                restoreText = { raw, _ -> restored = raw }, resetText = { restored = it.orEmpty() },
            )
            form.start()
            form.awaitInitial()
            assertEquals("Accepted text", restored)
            assertTrue(form.state.value.ready)
            assertFalse(form.state.value.resumed)
            assertFalse(form.hasUnfinished)
            assertTrue(saved.keys().isEmpty())
            assertNull(service().open(owner).raw)
            assertEquals(1, writes)
            assertEquals(1, attempts)
        } finally { scope.cancel() }
    }

    private fun service(loseAcceptanceAck: Boolean = false) = CompletedNotesDrafts(
        store = DurableDraftStore(root, io, files), maintenance = deps.dbMaintenance,
        restoreOpen = { restoreOpen }, dispatcher = io, nowMillis = { atMs },
        readRecord = { id -> if (failRead) throw IOException("Synthetic read failure") else records[id] },
        writeLocked = { id, before, intended, time ->
            attempts++; attemptedTimes.add(time)
            if (failBeforeBusiness) throw IOException("Synthetic write failure")
            check(records[id] == before)
            records[id] = before.copy(notes = intended)
            writes++
            if (loseAcceptanceAck) files.loseNextAck = true
            if (loseBusinessAck) { loseBusinessAck = false; throw IOException("Synthetic committed-write acknowledgement loss") }
        },
    )

    private class FaultFiles : DraftFileIO by AtomicDraftFiles {
        var failBeforeNext = false
        var loseNextAck = false
        override fun replace(file: File, bytes: ByteArray) {
            if (failBeforeNext) { failBeforeNext = false; throw IOException("Synthetic private-write failure") }
            AtomicDraftFiles.replace(file, bytes)
            if (loseNextAck) { loseNextAck = false; throw IOException("Synthetic private-write acknowledgement loss") }
        }
    }
}
