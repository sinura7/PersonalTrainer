package com.sinura.personaltrainer.data.draft

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.room.withTransaction
import com.google.gson.JsonParser
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetSaveResolution
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.workout.WorkoutClockCheckpoint
import com.sinura.personaltrainer.workout.WorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutDraftRestore
import com.sinura.personaltrainer.workout.WorkoutEntryCheckpoint
import com.sinura.personaltrainer.workout.WorkoutNumberDraft
import com.sinura.personaltrainer.workout.WorkoutNumberField
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DurableWorkoutDraftsTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var deps: FakeAppDependencies
    private lateinit var store: DurableDraftStore
    private lateinit var files: FaultFiles
    private lateinit var directory: File
    private var restoreOpen = false
    private lateinit var command: WorkoutSetSave
    private lateinit var entry: WorkoutEntryCheckpoint
    private val repo get() = deps.workoutRepository

    @Before fun setUp() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        files = FaultFiles()
        directory = temporary.newFolder()
        store = DurableDraftStore(directory = directory, files = files)
        val fixture = seedTestWorkout(deps, notes = "Previous notes")
        repo.updateSessionNotes(fixture.session.id, "Previous notes")
        command = WorkoutSetSave(
            sessionId = fixture.session.id, exerciseId = fixture.exercise.id, setId = "frozen-set",
            completedAt = fixture.session.startedAt + 1000,
            values = WorkoutSetValues(weightKg = 41.5, reps = 13, rpe = 8, isWarmup = false, durationSeconds = 27),
        )
        entry = WorkoutEntryCheckpoint(
            sessionId = fixture.session.id, startedAt = fixture.session.startedAt, authoredRevision = 1,
            lifts = mapOf(fixture.exercise.id to draft()), selectedExerciseId = fixture.exercise.id,
            notes = "  Raw\n中文 🏋  ", notesBase = "Previous notes", editingOriginal = null,
            numbers = listOf(WorkoutNumberDraft(
                exerciseId = fixture.exercise.id, field = WorkoutNumberField.WEIGHT, text = "82.",
                weightUnit = WeightUnit.LBS, selectionStart = 3, selectionEnd = 3, open = true,
            )),
            clocks = WorkoutClockCheckpoint(
                boot = 5L, holdExerciseId = null, hold = null,
                stopwatches = mapOf(fixture.exercise.id to SetStopwatchUiState(
                    used = true, elapsedSeconds = 27, frozenElapsedSeconds = 27, exerciseId = fixture.exercise.id,
                )),
            ),
        )
    }
    @After fun tearDown() { deps.close() }
    private fun service() = DurableWorkoutDrafts(
        store = store, repository = repo, maintenance = deps.dbMaintenance, restoreOpen = { restoreOpen },
    )
    private fun draft() = WorkoutDraft(
        sessionId = command.sessionId, exerciseId = command.exerciseId,
        weightKg = command.values.weightKg, reps = command.values.reps, rpe = command.values.rpe,
        isWarmup = false, notes = "  Raw\n中文 🏋  ", durationSeconds = 27, dirty = true,
    )
    private suspend fun open(port: DurableWorkoutDrafts): WorkoutDraftRestore.ReadyEntry =
        port.open(command.sessionId) as WorkoutDraftRestore.ReadyEntry
    private suspend fun write(port: DurableWorkoutDrafts, value: WorkoutSetSave = command) = executeFrozenSet(port = port, command = value, write = { repo.saveSet(value) }, acknowledge = { repo.acknowledgeSavedSet(value) },
    )

    @Test fun authoredFieldsNumericUnitAndClockReopenWithoutLogging() = runBlocking {
        val first = service()
        assertFalse(open(first).resumed)
        first.checkpoint(entry)
        val restored = open(service())
        assertTrue(restored.resumed)
        assertEquals(entry, restored.entry)
        assertNull(restored.pending)
        assertTrue(repo.getSession(command.sessionId)!!.sets.isEmpty())
    }

    @Test fun admittedUncommittedSaveReopensAndRetriesTheExactIdentity() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry)
        assertTrue(runCatching { executeFrozenSet(port = first, command = command, write = { throw IOException() }, acknowledge = { error("No accepted row") }) }.isFailure)
        val reopened = service()
        assertEquals(command, open(reopened).pending)
        assertEquals(WorkoutSetSaveResolution.UNSAVED, reopened.inspectSet(command) { repo.inspectSetSave(command) })
        val saved = write(reopened)
        assertEquals(command.setId, saved.row.id)
        assertEquals(command.completedAt, saved.row.completedAt)
        assertEquals(27, saved.row.durationSeconds)
        assertEquals(listOf(command.setId), repo.getSession(command.sessionId)!!.sets.map { it.id })
    }

    @Test fun failedAdmissionBeforeAnyBusinessWriteCanRetryTheSameFrozenCommand() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry)
        files.beforeAdmission = true
        var writes = 0
        assertTrue(runCatching { executeFrozenSet(port = first, command = command, write = { writes++; repo.saveSet(command) }, acknowledge = { repo.acknowledgeSavedSet(command) },
        ) }.isFailure)
        assertEquals(0, writes)
        assertEquals(WorkoutSetSaveResolution.UNSAVED, first.inspectSet(command) { repo.inspectSetSave(command) })
        assertEquals(command, open(service()).pending)
        write(first)
        assertEquals(command.setId, repo.getSession(command.sessionId)!!.sets.single().id)
    }

    @Test fun failedAcknowledgementCheckpointCanKeepLaterFieldsAndThenAdmitTheNextSet() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry); write(first)
        files.beforeEntry = true
        assertTrue(runCatching { first.settleSet(command, entry.copy(authoredRevision = 2)) }.isFailure)
        val newer = entry.copy(authoredRevision = 3, lifts = mapOf(command.exerciseId to draft().copy(rpe = 9)))
        first.checkpoint(newer)
        val next = command.copy(setId = "next-after-preservation", completedAt = command.completedAt + 1, values = command.values.copy(rpe = 9))
        write(first, next)
        assertEquals(listOf(8, 9), repo.getSession(command.sessionId)!!.sets.map { it.rpe })
        assertEquals(newer.lifts, open(service()).entry!!.lifts)
    }

    @Test fun committedWithoutAcknowledgementInspectsAndNeverRepeatsMutation() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry)
        var writes = 0
        assertTrue(runCatching { executeFrozenSet(port = first, command = command, write = { writes++; repo.saveSet(command); throw IOException() },
            acknowledge = { repo.acknowledgeSavedSet(command) },
        ) }.isFailure)
        val reopened = service()
        assertEquals(command, open(reopened).pending)
        assertEquals(WorkoutSetSaveResolution.SAVED, reopened.inspectSet(command) { repo.inspectSetSave(command) })
        assertTrue(reopened.writeSet(
            command = command, write = { writes++; repo.saveSet(command) },
            acknowledge = { repo.acknowledgeSavedSet(command) },
        ).alreadySaved)
        assertEquals(1, writes)
        assertEquals(1, repo.getSession(command.sessionId)!!.sets.size)
    }

    @Test fun failedAcceptedCleanupCannotResetLaterAuthoredEntry() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry); write(first)
        files.failClear = true
        val reset = entry.copy(authoredRevision = 2, lifts = mapOf(command.exerciseId to draft().copy(rpe = null)))
        assertTrue(runCatching { first.settleSet(command, reset) }.isFailure)
        val newer = reset.copy(authoredRevision = 3, lifts = mapOf(command.exerciseId to draft().copy(weightKg = 45.5, rpe = 9)))
        first.checkpoint(newer)
        val reopened = open(service())
        assertNull(reopened.pending)
        assertEquals(newer.lifts, reopened.entry!!.lifts)
        assertEquals(3L, reopened.entry.authoredRevision)
        assertEquals(1, repo.getSession(command.sessionId)!!.sets.size)
    }

    @Test fun absentUnacknowledgedCommandAfterGenerationChangeCannotReplay() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry)
        assertTrue(runCatching { executeFrozenSet(port = first, command = command, write = { throw IOException() }, acknowledge = { error("Not saved") }) }.isFailure)
        assertTrue(store.invalidateOnce("synthetic-restore") is DraftGenerationChange.Changed)
        val reopened = service()
        open(reopened)
        assertEquals(WorkoutSetSaveResolution.CONFLICT, reopened.inspectSet(command) { repo.inspectSetSave(command) })
        assertTrue(runCatching { write(reopened) }.isFailure)
        assertTrue(repo.getSession(command.sessionId)!!.sets.isEmpty())
    }

    @Test fun knownAcceptedButDeletedSetCanNeverRunTheWriteCallbackAgain() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry); write(first)
        repo.deleteSet(command.setId)
        store.invalidateOnce("synthetic-delete")
        var replayed = false
        assertTrue(runCatching { executeFrozenSet(port = first, command = command, write = { replayed = true; repo.saveSet(command) },
            acknowledge = { repo.acknowledgeSavedSet(command) },
        ) }.isFailure)
        assertFalse(replayed)
        assertTrue(repo.getSession(command.sessionId)!!.sets.isEmpty())
    }

    @Test fun acceptedSetWithoutUiSettlementBlocksOnlyTheOriginalCommandUntilSettled() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry); write(first)
        val next = command.copy(setId = "second", completedAt = command.completedAt + 1)
        assertTrue(runCatching { write(first, next) }.isFailure)
        first.settleSet(command, entry.copy(authoredRevision = 2))
        write(first, next)
        assertEquals(listOf(command.setId, next.setId), repo.getSession(command.sessionId)!!.sets.map { it.id })
    }

    @Test fun eightSuccessiveSavesKeepEightExactIdentitiesThroughReopening() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry)
        val commands = (1..8).map { index -> command.copy(setId = "set-$index", completedAt = command.completedAt + index) }
        commands.forEachIndexed { index, value ->
            write(first, value)
            first.settleSet(value, entry.copy(authoredRevision = index + 2L))
        }
        val reopened = open(service())
        assertNull(reopened.pending)
        val rows = repo.getSession(command.sessionId)!!.sets
        assertEquals(commands.map { it.setId }, rows.map { it.id })
        assertEquals(commands.map { it.completedAt }, rows.map { it.completedAt })
        assertTrue(rows.all { it.weightKg == 41.5 && it.reps == 13 && it.rpe == 8 && it.durationSeconds == 27 })
    }

    @Test fun notesClearWithLostAcknowledgementSettlesWithoutAnotherBusinessWrite() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry.copy(notes = ""))
        var writes = 0
        assertTrue(runCatching { first.writeNotes(command.sessionId, "", entry.copy(notes = "")) { before, intended ->
            assertEquals("Previous notes", before); writes++; repo.updateSessionNotes(command.sessionId, intended); throw IOException()
        } }.isFailure)
        val reopened = service()
        assertTrue(open(reopened).notesNeedReview)
        reopened.writeNotes(command.sessionId, "", entry.copy(notes = "")) { _, intended -> writes++; repo.updateSessionNotes(command.sessionId, intended) }
        assertEquals(1, writes)
        assertEquals("", repo.getSession(command.sessionId)!!.notes)
        val final = open(service())
        assertEquals("", final.entry!!.notes)
        assertEquals("", final.entry.notesBase)
        assertFalse(final.notesNeedReview)
    }

    @Test fun failedRawAcknowledgementStillReconcilesLatestCheckpointOnNextFlush() = runBlocking {
        val first = service()
        open(first)
        files.afterEntry = true
        assertTrue(runCatching { first.checkpoint(entry) }.isFailure)
        val latest = entry.copy(authoredRevision = 2, notes = "Latest exact words")
        first.checkpoint(latest)
        // Per-lift notes are old copies; the session's explicit authored note is authoritative.
        val expected = latest.copy(lifts = latest.lifts.mapValues { (_, lift) -> lift.copy(notes = latest.notes.orEmpty()) })
        assertEquals(expected, open(service()).entry)
    }

    @Test fun restoreOpenRefusesSubmissionAndKeepsAllSavedData() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry)
        restoreOpen = true
        assertTrue(runCatching { write(first) }.isFailure)
        restoreOpen = false
        assertTrue(repo.getSession(command.sessionId)!!.sets.isEmpty())
        assertEquals(entry, open(service()).entry)
    }

    @Test fun finishFromAnotherSurfaceRefusesUnresolvedSaveOrUnsavedNotes() = runBlocking {
        val first = service()
        open(first); first.checkpoint(entry)
        assertTrue(first.inspectFinish(command.sessionId, null)!!.contains("notes"))
        assertTrue(runCatching { executeFrozenSet(port = first, command = command, write = { throw IOException() }, acknowledge = { error("Not saved") }) }.isFailure)
        assertTrue(service().inspectFinish(command.sessionId, null)!!.contains("pending set"))
        assertTrue(repo.getSession(command.sessionId)!!.finishedAt == null)
    }

    /** Named callbacks remain statically checked at this typed local adapter. */
    @Test fun newerOwnerRejectsOlderCallbacksBeforeAndAfterExplicitDiscard() = runBlocking {
        val root = service()
        val first = root.forEntry()
        first.open(command.sessionId); first.checkpoint(entry)
        val second = root.forEntry()
        second.open(command.sessionId)
        val newer = entry.copy(authoredRevision = 2, notes = "Newest owner")
        second.checkpoint(newer)
        assertTrue(runCatching { first.checkpoint(entry.copy(authoredRevision = 100)) }.isFailure)
        var replayed = false
        assertTrue(runCatching { first.writeSet(command, { replayed = true; repo.saveSet(command) }, { repo.acknowledgeSavedSet(command) }) }.isFailure)
        assertFalse(replayed)
        second.discardEntry(command.sessionId)
        val third = root.forEntry()
        third.open(command.sessionId); third.checkpoint(newer.copy(authoredRevision = 3))
        assertTrue(runCatching { second.checkpoint(entry.copy(authoredRevision = 101)) }.isFailure)
        val saved = service().open(command.sessionId) as WorkoutDraftRestore.ReadyEntry
        assertEquals("Newest owner", saved.entry!!.notes)
        assertTrue(repo.getSession(command.sessionId)!!.sets.isEmpty())
    }

    @Test fun acknowledgedNotesAreDurableEvenWhenTheRawWriterWasBehind() = runBlocking {
        val port = service(); open(port)
        port.checkpoint(entry.copy(notes = "Older raw"))
        val intended = entry.copy(authoredRevision = 2, notes = "Acknowledged notes")
        port.writeNotes(command.sessionId, intended.notes!!, intended) { before, value ->
            repo.updateLiveNotesIfUnchanged(command.sessionId, before, value)
        }
        val restored = open(service())
        assertEquals("Acknowledged notes", restored.entry!!.notes)
        assertEquals("Acknowledged notes", restored.entry.notesBase)
        assertFalse(restored.notesNeedReview)
        assertEquals("Acknowledged notes", repo.getSession(command.sessionId)!!.notes)
    }

    @Test fun settlingOlderNotesKeepsNewerAuthoredFields() = runBlocking {
        val port = service(); open(port)
        val older = entry.copy(authoredRevision = 2, notes = "Acknowledged B")
        val newer = entry.copy(authoredRevision = 3, notes = "Still authored C")
        port.checkpoint(newer)
        port.writeNotes(command.sessionId, older.notes!!, older) { before, value ->
            repo.updateLiveNotesIfUnchanged(command.sessionId, before, value)
        }
        val restored = open(service())
        assertEquals("Still authored C", restored.entry!!.notes)
        assertEquals("Acknowledged B", restored.entry.notesBase)
        assertEquals("Acknowledged B", repo.getSession(command.sessionId)!!.notes)
    }

    @Test fun finishCannotOverwriteChangedNotesEvenWithAnExplicitNotesArgument() = runBlocking {
        val port = service(); open(port); port.checkpoint(entry)
        repo.saveSet(command)
        repo.updateSessionNotes(command.sessionId, "Newer stored notes")
        var wrote = false
        val failure = runCatching { port.finishAtomically(command.sessionId, entry.notes) { value ->
            wrote = true; repo.finishSession(command.sessionId, value)
        } }.exceptionOrNull()
        assertTrue(failure is com.sinura.personaltrainer.workout.WorkoutFinishRefused)
        assertFalse(wrote)
        val current = repo.getSession(command.sessionId)!!
        assertFalse(current.isFinished); assertEquals("Newer stored notes", current.notes)
        assertEquals(command.setId, current.sets.single().id)
    }

    @Test fun barFinishKeepsAReopenedUnsubmittedCorrection() = runBlocking {
        val port = service(); open(port); repo.saveSet(command)
        port.checkpoint(entry.copy(notes = "Previous notes", editingOriginal = command))
        val reopened = service()
        assertTrue(reopened.inspectFinish(command.sessionId, null)!!.contains("correction"))
        assertTrue(runCatching { reopened.finishAtomically(command.sessionId, null) { repo.finishSession(command.sessionId, it) } }.isFailure)
        assertEquals(command, open(service()).entry!!.editingOriginal)
        assertFalse(repo.getSession(command.sessionId)!!.isFinished)
    }

    @Test fun unreadablePrivateEntryCanBeDiscardedWithoutDeletingSavedSets() = runBlocking {
        repo.saveSet(command)
        for (format in listOf("workout-entry", "workout-set-command", "workout-notes-command")) {
            val port = service(); open(port); port.checkpoint(entry); write(port)
            port.writeNotes(command.sessionId, entry.notes!!, entry) { before, intended -> repo.updateLiveNotesIfUnchanged(command.sessionId, before, intended) }
            val owner = directory.listFiles()!!.single { it.name.endsWith(".json") && it.readText().contains(format) }
            owner.writeText("{ broken checkpoint", Charsets.UTF_8)
            val reopened = service()
            assertEquals(format, WorkoutDraftRestore.ReadUnavailable, reopened.open(command.sessionId))
            reopened.discardEntry(command.sessionId)
            val recovered = open(service())
            assertNull(recovered.entry); assertNull(recovered.pending)
            assertTrue(directory.listFiles()!!.filter { it.name.contains(".quarantine-") }.all { it.readText() == "{ broken checkpoint" })
            val current = repo.getSession(command.sessionId)!!
            assertEquals(command.setId, current.sets.single().id); assertFalse(current.isFinished)
        }
    }

    @Test fun revokedEditorCannotDiscardTheNewOwnersFieldsOrFrozenSave() = runBlocking {
        val root = service(); val first = root.forEntry(); first.open(command.sessionId); first.checkpoint(entry)
        val second = root.forEntry(); second.open(command.sessionId)
        val newer = entry.copy(authoredRevision = 2, notes = "New owner's exact fields")
        second.checkpoint(newer)
        try { second.writeSet(command, { throw IOException("Uncommitted") }, { error("No accepted row") }) } catch (_: IOException) { }
        assertTrue(runCatching { first.discardEntry(command.sessionId) }.isFailure)
        assertTrue(second.ownsEntry(command.sessionId))
        val current = root.forEntry().open(command.sessionId) as WorkoutDraftRestore.ReadyEntry
        assertEquals(newer.notes, current.entry!!.notes); assertEquals(command, current.pending)
        assertTrue(repo.getSession(command.sessionId)!!.sets.isEmpty())
    }

    @Test fun failedPrivateDiscardCanRetryButCannotReviveEarlierQueuedCommands() = runBlocking {
        val port = service(); open(port); port.checkpoint(entry); write(port)
        val previous = port.mutationPermit(command.sessionId)
        files.failClear = true
        assertTrue(runCatching { port.discardEntry(command.sessionId) }.isFailure)
        assertTrue(runCatching { previous.requireAllowed() }.isFailure)
        assertTrue(port.ownsEntry(command.sessionId))
        assertTrue(runCatching { port.checkpoint(entry) }.isFailure)
        port.discardEntry(command.sessionId)
        assertNull(open(service()).entry)
        assertEquals(command.setId, repo.getSession(command.sessionId)!!.sets.single().id)
    }

    @Test fun finishHoldsInspectionAndCommitAgainstCompetingMaintenance() = runBlocking {
        withTimeout(5000) {
            val port = service(); open(port); repo.saveSet(command)
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val waiter = CompletableDeferred<Unit>(); val acquired = CompletableDeferred<Unit>()
            val finished = async(Dispatchers.IO) { port.finishAtomically(command.sessionId, "Previous notes") { value ->
                entered.complete(Unit); release.await(); repo.finishSession(command.sessionId, value)
            } }
            entered.await()
            val maintenance = launch(Dispatchers.IO) { waiter.complete(Unit); deps.dbMaintenance.withMaintenanceLock { acquired.complete(Unit) } }
            waiter.await()
            assertFalse(acquired.isCompleted)
            release.complete(Unit); finished.await(); maintenance.join()
            assertTrue(acquired.isCompleted)
            assertTrue(repo.getSession(command.sessionId)!!.isFinished)
            assertEquals("Previous notes", repo.getSession(command.sessionId)!!.notes)
        }
    }

    @Test fun queuedDestructiveEditorOperationCannotWriteAfterRestoreRetiresItsOwner() = runBlocking {
        withTimeout(5000) {
            val root = service(); val editor = root.forEntry(); editor.open(command.sessionId)
            repo.saveSet(command)
            val permit = editor.mutationPermit(command.sessionId)!!
            val held = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val queued = CompletableDeferred<Unit>()
            val holder = launch(Dispatchers.IO) { deps.dbMaintenance.withMaintenanceLock { held.complete(Unit); release.await() } }
            held.await()
            val deletion = async(Dispatchers.IO) { runCatching { withContext(permit) { queued.complete(Unit); repo.deleteSet(command.setId) } } }
            queued.await(); root.invalidateEntries(); release.complete(Unit)
            assertTrue(deletion.await().isFailure); holder.join()
            assertEquals(command.setId, repo.getSession(command.sessionId)!!.sets.single().id)
        }
    }

    @Test fun queuedHistoryCorrectionCannotHoldRoomAheadOfTheMaintenanceCoordinator() = runBlocking {
        withTimeout(10000) {
            repo.saveSet(command); repo.finishSession(command.sessionId, "Previous notes")
            val correction = command.copy(values = command.values.copy(weightKg = 52.5), original = command.values)
            val held = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val holder = launch(Dispatchers.IO) { deps.dbMaintenance.withMaintenanceLock { held.complete(Unit); release.await() } }
            held.await()
            val save = async(start = CoroutineStart.UNDISPATCHED) { repo.saveFinishedSet(correction) }
            try {
                // The coordinator's current owner can still inspect Room while the
                // correction waits. Room-first ordering deadlocks this inspection.
                withTimeout(5000) { deps.database.withTransaction {
                    assertEquals(41.5, repo.getSession(command.sessionId)!!.sets.single().weightKg, 0.0)
                } }
            } finally { release.complete(Unit) }
            holder.join(); save.await()
            assertEquals(52.5, repo.getSession(command.sessionId)!!.sets.single().weightKg, 0.0)
        }
    }

    private suspend fun <T> executeFrozenSet(
        port: DurableWorkoutDrafts, command: WorkoutSetSave,
        write: suspend () -> T, acknowledge: suspend () -> T,
    ): T = port.writeSet(command, write, acknowledge)

    private class FaultFiles : DraftFileIO {
        var failClear = false
        var afterEntry = false
        var beforeEntry = false
        var beforeAdmission = false
        override fun read(file: File): ByteArray? = AtomicDraftFiles.read(file)
        override fun replace(file: File, bytes: ByteArray) {
            val document = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            val format = document.get("payloadFormat")?.asString
            if (beforeEntry && format == "workout-entry") { beforeEntry = false; throw IOException() }
            if (beforeAdmission && format == "workout-set-command" && !document.get("pending").isJsonNull) {
                beforeAdmission = false; throw IOException()
            }
            if (failClear && format == "workout-set-command" && document.get("pending").isJsonNull) {
                failClear = false; throw IOException()
            }
            AtomicDraftFiles.replace(file, bytes)
            if (afterEntry && format == "workout-entry") { afterEntry = false; throw IOException() }
        }
    }
}
