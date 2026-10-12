package com.sinura.personaltrainer.data.draft

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.LoadType
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
class CustomExerciseDraftsTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var deps: FakeAppDependencies
    private lateinit var root: File
    private lateinit var store: DurableDraftStore
    private lateinit var io: FaultFiles
    private var restoreOpen = false

    @Before fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        root = temporary.newFolder()
        io = FaultFiles()
        store = DurableDraftStore(directory = root, files = io)
        deps.exerciseRepository.customWriteCoordinator = deps.dbMaintenance
        deps.exerciseRepository.customWriteBlocked = { restoreOpen }
        deps.exerciseRepository.beforeCustomDestruction = { event ->
            check(store.invalidateOnce(event) is DraftGenerationChange.Changed)
        }
    }
    @After fun tearDown() { deps.close() }
    private fun service() = CustomExerciseDrafts(
        store = store, repository = deps.exerciseRepository, maintenance = deps.dbMaintenance,
        restoreOpen = { restoreOpen }, nowMillis = { 1200L },
    )
    private fun fields(handle: CustomLiftHandle) = handle.authored.copy(
        name = "  Row á 🏋  ", muscle = "Back", notes = "  Preserve\n中文 notes  ", loadType = LoadType.STACK,
    )

    @Test fun repeatedAdmittedExecutionSettlesTheSameStableRecordWithoutAnotherWrite() = runBlocking {
        val first = service()
        val handle = first.open(null, "Back")
        first.admit(handle, fields(handle))
        assertTrue(first.execute(handle) is SaveExerciseResult.Saved)
        val once = deps.database.exerciseDao().getAllCustom()
        assertTrue(first.execute(handle) is SaveExerciseResult.Saved)
        assertEquals(once, deps.database.exerciseDao().getAllCustom())
        assertEquals(1, once.size)
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, first.inspect(handle))
    }

    @Test fun rawFieldsAndCreateIdentitySurviveReopeningWithoutBusinessWrites() = runBlocking {
        val first = service()
        val handle = first.open(null, "")
        val raw = fields(handle)
        first.checkpoint(handle, raw)
        val reopened = service().open(null, "Chest")
        assertTrue(reopened.resumed)
        assertEquals(raw, reopened.authored)
        assertEquals(handle.checkpoint.version.owner, reopened.checkpoint.version.owner)
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun pendingReopenProvesUncommittedAndRetriesTheExactStableCommandOnce() = runBlocking {
        val first = service()
        val handle = first.open(null, "Back")
        first.admit(handle, fields(handle))
        val command = handle.checkpoint.pending!!.value
        val reopenedService = service()
        val reopened = reopenedService.open(null, "Chest")
        assertEquals(command, reopened.checkpoint.pending!!.value)
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, reopenedService.inspect(reopened))
        reopenedService.retry(reopened)
        assertEquals(command, reopened.checkpoint.pending!!.value)
        val result = reopenedService.execute(reopened) as SaveExerciseResult.Saved
        assertEquals(command.intended.row.id, result.exercise.id)
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, reopenedService.inspect(reopened))
        reopenedService.finish(reopened)
        assertEquals(listOf(command.intended.row), deps.database.exerciseDao().getAllCustom())
        assertNotEquals(command.intended.row.id, reopenedService.open(null, "Back").authored.createId)
    }

    @Test fun committedButUnacknowledgedCreateSettlesOnReopenWithoutAnotherWrite() = runBlocking {
        val first = service()
        val handle = first.open(null, "Back")
        first.admit(handle, fields(handle))
        first.execute(handle)
        val rows = deps.database.exerciseDao().getAllCustom()
        val reopenedService = service()
        val reopened = reopenedService.open(null, "Chest")
        assertEquals(rows, deps.database.exerciseDao().getAllCustom())
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, reopenedService.inspect(reopened))
        reopenedService.finish(reopened)
        assertEquals(rows, deps.database.exerciseDao().getAllCustom())
    }

    @Test fun deletionAfterAnUnacknowledgedCommitCannotAuthorizeRecreation() = runBlocking {
        val first = service()
        val handle = first.open(null, "Back")
        first.admit(handle, fields(handle))
        val id = handle.checkpoint.pending!!.value.intended.row.id
        first.execute(handle)
        deps.exerciseRepository.deleteCustom(id)
        val reopenedService = service()
        val reopened = reopenedService.open(null, "Back")
        assertEquals(DraftRecoveryOutcome.CONFLICT, reopenedService.inspect(reopened))
        assertThrows(IllegalStateException::class.java) { runBlocking { reopenedService.retry(reopened) } }
        reopenedService.discard(reopened)
        assertNull(deps.exerciseRepository.getById(id))
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun aChangedEditOwnerCannotBeOverwrittenByItsOlderDraft() = runBlocking {
        val initial = (deps.exerciseRepository.createCustom("Initial", "Back") as SaveExerciseResult.Saved).exercise
        val first = service()
        val handle = first.open(initial.id, "")
        first.admit(handle, fields(handle))
        deps.exerciseRepository.updateCustom(id = initial.id, name = "Newer", muscleGroup = "Chest", notes = "Current")
        assertEquals(DraftRecoveryOutcome.CONFLICT, first.inspect(handle))
        assertThrows(IllegalStateException::class.java) { runBlocking { first.execute(handle) } }
        assertEquals("Newer", deps.exerciseRepository.getById(initial.id)!!.name)
        first.discard(handle)
        assertEquals("Current", deps.exerciseRepository.getById(initial.id)!!.notes)
    }

    @Test fun restoreRevertingToTheBeforeValuesStillCannotAuthorizeOldRetry() = runBlocking {
        val initial = (deps.exerciseRepository.createCustom("Initial", "Back") as SaveExerciseResult.Saved).exercise
        val first = service()
        val handle = first.open(initial.id, "")
        first.admit(handle, fields(handle))
        val before = handle.authored.before!!
        first.execute(handle)
        deps.dbMaintenance.withMaintenanceLock {
            assertTrue(store.invalidateOnce("restore:isolated-fixture") is DraftGenerationChange.Changed)
            deps.database.exerciseDao().update(before.row)
            deps.database.catalogDao().replaceCreditsFor(before.row.id, before.credits)
        }
        assertEquals(before, deps.exerciseRepository.customDraftRecord(initial.id))
        assertEquals(DraftRecoveryOutcome.CONFLICT, first.inspect(handle))
        assertEquals(before, deps.exerciseRepository.customDraftRecord(initial.id))
    }

    @Test fun failedAdmissionBeforeReplacementKeepsTheLatestUnflushedFieldsAndNeverWritesBusiness() = runBlocking {
        val first = service()
        val handle = first.open(null, "Back")
        val latest = fields(handle)
        io.before = true
        assertThrows(IllegalStateException::class.java) { runBlocking { first.admit(handle, latest) } }
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, first.inspect(handle))
        assertEquals(latest, service().open(null, "").authored)
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun admissionReplacementWithLostAcknowledgementRecoversItsExactCommand() = runBlocking {
        val first = service()
        val handle = first.open(null, "Back")
        io.after = true
        assertThrows(IllegalStateException::class.java) { runBlocking { first.admit(handle, fields(handle)) } }
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, first.inspect(handle))
        val exact = handle.checkpoint.pending!!.value
        first.retry(handle)
        first.execute(handle)
        assertEquals(exact.intended.row, deps.database.exerciseDao().getAllCustom().single())
    }

    @Test fun acceptedAndCleanupAcknowledgementFailuresNeverReplayBusinessWrites() = runBlocking {
        val first = service()
        val handle = first.open(null, "Back")
        first.admit(handle, fields(handle))
        first.execute(handle)
        val original = deps.database.exerciseDao().getAllCustom()
        io.after = true
        assertThrows(IllegalStateException::class.java) { runBlocking { first.inspect(handle) } }
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, first.inspect(handle))
        io.after = true
        assertThrows(IllegalStateException::class.java) { runBlocking { first.finish(handle) } }
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, first.inspect(handle))
        first.finish(handle)
        assertEquals(original, deps.database.exerciseDao().getAllCustom())
        assertNull(handle.checkpoint.pending)
    }

    @Test fun editInsteadAcknowledgementLossReturnsToTheKeptUncommittedFields() = runBlocking {
        val first = service()
        val handle = first.open(null, "Back")
        first.admit(handle, fields(handle))
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, first.inspect(handle))
        io.after = true
        assertThrows(IllegalStateException::class.java) { runBlocking { first.editInstead(handle) } }
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, first.inspect(handle))
        assertNull(handle.checkpoint.pending)
        assertEquals(fields(handle), handle.authored)
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun explicitDiscardAcknowledgementLossSettlesWithoutRestoringFieldsOrReportingSaved() = runBlocking {
        val first = service()
        val handle = first.open(null, "Back")
        first.checkpoint(handle, fields(handle))
        io.after = true
        assertThrows(IllegalStateException::class.java) { runBlocking { first.discard(handle) } }
        first.discard(handle)
        assertNull(handle.checkpoint.draft)
        assertNull(handle.checkpoint.pending)
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun mutatedCommandFieldsAreRejectedBeforeInspectionOrExecutionAndOriginalFileRemains() = runBlocking {
        val first = service()
        val handle = first.open(null, "Back")
        first.admit(handle, fields(handle))
        val file = ownerFile()
        val json = JsonParser.parseString(file.readText()).asJsonObject
        json.getAsJsonObject("pending").getAsJsonObject("command").getAsJsonObject("intended").addProperty("notes", "Different intent")
        file.writeText(json.toString())
        val original = file.readBytes()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { first.inspect(handle) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { first.execute(handle) } }
        assertTrue(original.contentEquals(file.readBytes()))
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun forgedDraftIdentityCannotRedirectTheOwningEditor() = runBlocking {
        val first = service()
        first.open(null, "Back")
        val file = ownerFile()
        val json = JsonParser.parseString(file.readText()).asJsonObject
        json.getAsJsonObject("draft").addProperty("createId", "ex-custom-00000000-0000-0000-0000-000000000001")
        file.writeText(json.toString())
        val original = file.readBytes()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { first.open(null, "Chest") } }
        assertTrue(original.contentEquals(file.readBytes()))
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun legacyCustomGuardIsOffMainAndInvalidationReplayIsIdempotent() = runBlocking {
        var called = false
        deps.exerciseRepository.customWriteBlocked = { called = true; assertFalse(Looper.myLooper() == Looper.getMainLooper()); false }
        deps.exerciseRepository.createCustom("Main-safe", "Back")
        assertTrue(called)
        val first = store.invalidateOnce("restore:same") as DraftGenerationChange.Changed
        val second = store.invalidateOnce("restore:same") as DraftGenerationChange.Changed
        assertEquals(first, second)
    }

    @Test fun aStaleReadPermitAndAnOpenRestoreBothRefuseBusinessMutation() = runBlocking {
        val first = service()
        val handle = first.open(null, "Back")
        first.admit(handle, fields(handle))
        assertThrows(IllegalStateException::class.java) { runBlocking { first.execute(handle) { false } } }
        restoreOpen = true
        assertThrows(IllegalStateException::class.java) { runBlocking { first.execute(handle) } }
        restoreOpen = false
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, first.inspect(handle))
        assertTrue(deps.database.exerciseDao().getAllCustom().isEmpty())
    }

    @Test fun actualAcceptedRestoreFencesAPendingEditEvenWhenItRestoresTheExactBeforeValues() = runBlocking {
        val initial = (deps.exerciseRepository.createCustom("Original row", "Back") as SaveExerciseResult.Saved).exercise
        // Backup deliberately omits the local sync revision. Zero is the restored revision;
        // choose it in this fixture so generation fencing, rather than a stamp mismatch, decides.
        deps.database.exerciseDao().update(deps.database.exerciseDao().getById(initial.id)!!.copy(updatedAtMs = 0L))
        val before = deps.exerciseRepository.customDraftRecord(initial.id)
        val exported = deps.backupService.exportJson()
        val first = service()
        val handle = first.open(initial.id, "")
        first.admit(handle, fields(handle))
        first.execute(handle)
        assertNotEquals(before, deps.exerciseRepository.customDraftRecord(initial.id))
        var events = 0
        deps.backupService.invalidatePrivateDrafts = { event ->
            events++
            check(store.invalidateOnce(event) is DraftGenerationChange.Changed)
        }
        val plan = deps.backupService.prepareRestore(exported, sourceName = "synthetic-before.json")
        deps.backupService.commitRestore(plan)
        assertTrue(events >= 1)
        assertFalse(deps.restoreJournal.isOpen())
        assertEquals(before, deps.exerciseRepository.customDraftRecord(initial.id))
        assertEquals(DraftRecoveryOutcome.CONFLICT, first.inspect(handle))
        assertEquals(before, deps.exerciseRepository.customDraftRecord(initial.id))
    }

    @Test fun failedPrivateRestoreFenceRefusesTheWipeAndKeepsTheCurrentBusinessRecords() = runBlocking {
        val initial = (deps.exerciseRepository.createCustom("Unchanged row", "Back") as SaveExerciseResult.Saved).exercise
        val before = deps.exerciseRepository.customDraftRecord(initial.id)
        val plan = deps.backupService.prepareRestore(deps.backupService.exportJson(), sourceName = "synthetic-same.json")
        deps.backupService.invalidatePrivateDrafts = { throw IOException("Synthetic private metadata unavailable") }
        assertThrows(com.sinura.personaltrainer.data.backup.BackupException::class.java) {
            runBlocking { deps.backupService.commitRestore(plan) }
        }
        assertFalse(deps.restoreJournal.isOpen())
        assertEquals(before, deps.exerciseRepository.customDraftRecord(initial.id))
    }

    private fun ownerFile(): File = root.listFiles()!!.single {
        it.name.endsWith(".json") && JsonParser.parseString(it.readText()).asJsonObject.get("payloadFormat")?.asString == "custom-lift"
    }
    private class FaultFiles : DraftFileIO {
        var before = false
        var after = false
        override fun read(file: File): ByteArray? = AtomicDraftFiles.read(file)
        override fun replace(file: File, bytes: ByteArray) {
            if (before) { before = false; throw IOException("Synthetic before-replacement failure") }
            AtomicDraftFiles.replace(file, bytes)
            if (after) { after = false; throw IOException("Synthetic acknowledgement failure") }
        }
    }
}
