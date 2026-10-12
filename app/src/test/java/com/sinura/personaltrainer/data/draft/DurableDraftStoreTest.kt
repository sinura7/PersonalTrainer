package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DurableDraftStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val owner = DraftOwner(DraftForm.CUSTOM_EXERCISE, "new-fixture")
    private val fields = listOf("Bench variation", "chest", "Raw notes", "62,5", "lb", "2026-10-12")

    private object Codec : DraftCodec<List<String>, String> {
        override val format = "fixture-authored-fields"
        override val version = 1
        override fun encodeDraft(value: List<String>): JsonElement = JsonArray().apply { value.forEach(::add) }
        override fun decodeDraft(value: JsonElement): List<String> {
            require(value.isJsonArray)
            return value.asJsonArray.map { require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString }
        }
        override fun encodeCommand(value: String): JsonElement = JsonPrimitive(value)
        override fun decodeCommand(value: JsonElement): String {
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            return value.asString
        }
    }

    private fun binding(root: File, io: DraftFileIO = AtomicDraftFiles, key: DraftOwner = owner) =
        DurableDraftStore(directory = root, files = io).bind(key, Codec)
    private suspend fun read(form: DraftBinding<List<String>, String>): DraftCheckpoint<List<String>, String> =
        (form.read() as DraftRead.Available).checkpoint
    private fun written(change: DraftChange<List<String>, String>): DraftCheckpoint<List<String>, String> {
        assertTrue("Expected a written checkpoint: $change", change is DraftChange.Written)
        return (change as DraftChange.Written).checkpoint
    }
    private fun rejected(reason: DraftRejection, change: DraftChange<List<String>, String>) {
        assertEquals(DraftChange.Rejected(reason), change)
    }
    private fun ownerFile(root: File): File = root.listFiles()!!.single { it.name.endsWith(".json") && it.name != "generation.json" }

    @Test fun reopeningRecoversExactRawAuthoredFieldsWithoutACommand() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        val first = read(form)
        val saved = written(form.checkpoint(first.version, fields))
        val reopened = read(binding(root))
        assertEquals(saved.version, reopened.version)
        assertEquals(fields, reopened.draft)
        assertNull(reopened.pending)
        assertFalse(reopened.needsReconciliation)
    }

    @Test fun differentOwnersDoNotRestoreOrClearEachOthersFields() = runTest {
        val root = temporary.newFolder()
        val one = binding(root)
        val saved = written(one.checkpoint(read(one).version, fields))
        val other = binding(root, key = DraftOwner(DraftForm.ROUTINE, "routine-other"))
        assertNull(read(other).draft)
        rejected(DraftRejection.WRONG_OWNER, other.clear(saved.version))
        assertEquals(fields, read(one).draft)
    }

    @Test fun twoBindingsCannotAcceptDuplicateSaveWithTheSameRevision() = runTest {
        val root = temporary.newFolder()
        val one = binding(root)
        val two = binding(root)
        val version = read(one).version
        val first = async { one.beginSave(version, fields, "command-one", "stable-create-id") }
        val second = async { two.beginSave(version, fields, "command-two", "another-create-id") }
        val outcomes = listOf(first.await(), second.await())
        assertEquals(1, outcomes.count { it is DraftChange.Written })
        assertEquals(1, outcomes.count { it == DraftChange.Rejected(DraftRejection.STALE_VERSION) })
        assertEquals(DraftCommandPhase.PENDING, read(one).pending!!.phase)
    }

    @Test fun saveFreezesFieldsAndCommandAndBlocksEditingOrDiscardUntilInspection() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        val mutableFields = fields.toMutableList()
        val pending = written(form.beginSave(read(form).version, mutableFields, "command", "stable-id"))
        mutableFields[0] = "Changed after admission"
        assertEquals(fields, pending.draft)
        val reopened = read(binding(root))
        assertEquals(fields, reopened.draft)
        assertEquals("stable-id", reopened.pending!!.value)
        rejected(DraftRejection.PENDING_COMMAND, form.checkpoint(reopened.version, mutableFields))
        rejected(DraftRejection.INSPECTION_REQUIRED, form.clear(reopened.version))
        rejected(DraftRejection.INSPECTION_REQUIRED, form.retry(reopened.version, "command"))
    }

    @Test fun conflictingAndUnavailableBusinessReadsKeepFrozenIntentAndCannotAuthorizeRetry() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        val pending = written(form.beginSave(read(form).version, fields, "command", "stable-id"))
        for (outcome in listOf(DraftRecoveryOutcome.CONFLICT, DraftRecoveryOutcome.UNAVAILABLE)) {
            rejected(DraftRejection.INSPECTION_REQUIRED, form.inspect(pending.version, "command", outcome))
            assertEquals(pending, read(form))
        }
        rejected(DraftRejection.WRONG_COMMAND, form.inspect(pending.version, "other", DraftRecoveryOutcome.EXACT_SAVED))
    }

    @Test fun uncommittedInspectionRetriesTheSameCommandOnceOrResumesEditingWithoutLosingFields() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        val pending = written(form.beginSave(read(form).version, fields, "command", "stable-id"))
        val retryable = written(form.inspect(pending.version, "command", DraftRecoveryOutcome.UNCOMMITTED))
        val retry = written(form.retry(retryable.version, "command"))
        assertEquals(pending.pending, retry.pending)
        rejected(DraftRejection.STALE_VERSION, form.retry(retryable.version, "command"))
        val noWrite = written(form.inspect(retry.version, "command", DraftRecoveryOutcome.UNCOMMITTED))
        val editable = written(form.resumeEditing(noWrite.version, "command"))
        assertEquals(fields, editable.draft)
        assertNull(editable.pending)
    }

    @Test fun acceptedSaveCannotReplayWhenCleanupFails() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        val pending = written(form.beginSave(read(form).version, fields, "command", "stable-id"))
        val accepted = written(form.inspect(pending.version, "command", DraftRecoveryOutcome.EXACT_SAVED))
        val broken = binding(root, FailingFiles(afterReplacement = false))
        assertEquals(DraftChange.Unavailable(DraftReadProblem.STORAGE), broken.clear(accepted.version))
        val reopened = read(binding(root))
        assertEquals(DraftCommandPhase.ACCEPTED, reopened.pending!!.phase)
        rejected(DraftRejection.INSPECTION_REQUIRED, form.retry(reopened.version, "command"))
        rejected(DraftRejection.PENDING_COMMAND, form.beginSave(reopened.version, fields, "again", "new-id"))
        written(form.clear(reopened.version))
        assertNull(read(binding(root)).pending)
    }

    @Test fun clearKeepsItsFenceAcrossReopeningAndEightSuccessiveWrites() = runTest {
        val root = temporary.newFolder()
        var form = binding(root)
        val old = written(form.checkpoint(read(form).version, fields))
        val cleared = written(form.clear(old.version))
        form = binding(root)
        assertEquals(cleared.version, read(form).version)
        assertNull(read(form).draft)
        rejected(DraftRejection.STALE_VERSION, form.checkpoint(old.version, listOf("queued old value")))
        var version = cleared.version
        repeat(8) { index ->
            val value = fields + index.toString()
            val saved = written(form.checkpoint(version, value))
            assertEquals(value, read(binding(root)).draft)
            version = saved.version
        }
        assertEquals(old.version.revision + 9, version.revision)
    }

    @Test fun failureBeforeReplacementLeavesPreviousCheckpointAndFailureAfterReplacementIsUncertain() = runTest {
        for (after in listOf(false, true)) {
            val root = temporary.newFolder()
            val form = binding(root)
            val original = written(form.checkpoint(read(form).version, fields))
            val broken = binding(root, FailingFiles(after))
            assertEquals(DraftChange.Unavailable(DraftReadProblem.STORAGE), broken.beginSave(original.version, fields, "command", "stable-id"))
            val recovered = read(binding(root))
            if (after) {
                assertEquals(DraftCommandPhase.PENDING, recovered.pending!!.phase)
                rejected(DraftRejection.STALE_VERSION, form.checkpoint(original.version, listOf("overwrite")))
            } else {
                assertEquals(original, recovered)
            }
        }
    }

    @Test fun cancelledAcknowledgementStillLeavesAdmittedCommandDurable() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        val version = read(form).version
        lateinit var job: Job
        val io = object : DraftFileIO by AtomicDraftFiles {
            override fun replace(file: File, bytes: ByteArray) {
                AtomicDraftFiles.replace(file, bytes)
                job.cancel()
            }
        }
        job = launch { binding(root, io).beginSave(version, fields, "command", "stable-id") }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals("stable-id", read(binding(root)).pending!!.value)
    }

    @Test fun restoreGenerationFencesOldWritesAndRequiresOwnershipThenFreshCommandInspection() = runTest {
        val root = temporary.newFolder()
        val store = DurableDraftStore(root)
        val form = store.bind(owner, Codec)
        val pending = written(form.beginSave(read(form).version, fields, "command", "stable-id"))
        val retryable = written(form.inspect(pending.version, "command", DraftRecoveryOutcome.UNCOMMITTED))
        val next = store.advanceGeneration(retryable.currentGeneration) as DraftGenerationChange.Changed
        assertEquals(DraftGenerationChange.Conflict, store.advanceGeneration(retryable.currentGeneration))
        val stale = read(binding(root))
        assertTrue(stale.needsReconciliation)
        assertEquals(fields, stale.draft)
        rejected(DraftRejection.RECONCILIATION_REQUIRED, form.retry(stale.version, "command"))
        rejected(DraftRejection.STALE_VERSION, form.reconcileOwner(stale.version, retryable.currentGeneration))
        val reconciled = written(form.reconcileOwner(stale.version, next.generation))
        assertFalse(reconciled.needsReconciliation)
        assertEquals(DraftCommandPhase.PENDING, reconciled.pending!!.phase)
        rejected(DraftRejection.INSPECTION_REQUIRED, form.retry(reconciled.version, "command"))
    }

    @Test fun corruptUnsupportedAndWrongOwnerFilesAreRetainedAndNeverTreatedAsEmpty() = runTest {
        for (mutation in listOf<(String) -> String>(
            { "{" },
            { it.replace("\"storageVersion\":1", "\"storageVersion\":99") },
            { it.replace("\"owner\":\"new-fixture\"", "\"owner\":\"other-owner\"") },
            { it.replace("\"revision\":1", "\"revision\":1.2") },
            { it.replace("\"payloadVersion\":1", "\"payloadVersion\":99") },
        )) {
            val root = temporary.newFolder()
            val form = binding(root)
            val saved = written(form.checkpoint(read(form).version, fields))
            val file = ownerFile(root)
            val corrupted = mutation(file.readText())
            file.writeText(corrupted)
            assertTrue(form.read() is DraftRead.Unavailable)
            assertTrue(form.checkpoint(saved.version, listOf("erase old data")) is DraftChange.Unavailable)
            assertEquals(corrupted, file.readText())
        }
    }

    @Test fun oversizedFileDoesNotAllocateUnboundedDataOrEraseAuthoredFields() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        written(form.checkpoint(read(form).version, fields))
        val file = ownerFile(root)
        file.outputStream().use { output -> repeat(MAX_DRAFT_BYTES / 1024 + 1) { output.write(ByteArray(1024)) } }
        assertEquals(DraftRead.Unavailable(DraftReadProblem.TOO_LARGE), form.read())
        assertEquals(MAX_DRAFT_BYTES.toLong() + 1024, file.length())
    }

    @Test fun malformedUtf8CannotSilentlyChangeAuthoredFields() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        val saved = written(form.checkpoint(read(form).version, fields))
        val file = ownerFile(root)
        val bytes = file.readBytes()
        val index = bytes.indexOf('B'.code.toByte())
        assertTrue(index >= 0)
        bytes[index] = 0xff.toByte()
        file.writeBytes(bytes)
        assertEquals(DraftRead.Unavailable(DraftReadProblem.CORRUPT), form.read())
        assertEquals(DraftChange.Unavailable(DraftReadProblem.CORRUPT), form.checkpoint(saved.version, fields))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun validUnicodeNamesAndMultilineNotesSurviveExactly() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        val authored = listOf("Élévation 🏋️", "中文", "Notes\nQuoted: \"100%\"\t", "العربية")
        val saved = written(form.beginSave(read(form).version, authored, "command", "Élévation · stable-id"))
        assertEquals(saved, read(binding(root)))
        assertEquals(authored, saved.draft)
    }

    @Test fun malformedDraftAndCommandCharactersAreRejectedWithoutSubstitution() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        val saved = written(form.checkpoint(read(form).version, fields))
        for (bad in listOf("\uD800", "\uDC00", "ok\uD800x", "a\uDC00b")) {
            rejected(DraftRejection.INVALID_DATA, form.beginSave(saved.version, listOf(bad), "command", "stable-id"))
            rejected(DraftRejection.INVALID_DATA, form.beginSave(saved.version, fields, "command", bad))
            rejected(DraftRejection.INVALID_DATA, form.beginSave(saved.version, fields, bad, "stable-id"))
            assertEquals(saved, read(form))
        }
    }

    @Test fun escapedMalformedJsonStringsAndKeysRemainUnreadableWithoutErasure() = runTest {
        for (mutation in listOf<(String) -> String>(
            { it.replace("Bench variation", "\\ud800") },
            { it.replace("stable-id", "\\udc00") },
            { it.replaceFirst("{", "{\"\\ud800\":0,") },
        )) {
            val root = temporary.newFolder()
            val form = binding(root)
            val pending = written(form.beginSave(read(form).version, fields, "command", "stable-id"))
            val file = ownerFile(root)
            val malformed = mutation(file.readText())
            file.writeText(malformed)
            assertEquals(DraftRead.Unavailable(DraftReadProblem.CORRUPT), form.read())
            assertEquals(DraftChange.Unavailable(DraftReadProblem.CORRUPT), form.inspect(pending.version, "command", DraftRecoveryOutcome.EXACT_SAVED))
            assertEquals(malformed, file.readText())
        }
    }

    @Test fun malformedOwnerIdentitiesCannotAliasAValidOwner() = runTest {
        val root = temporary.newFolder()
        val form = binding(root, key = DraftOwner(DraftForm.CUSTOM_EXERCISE, "?"))
        val saved = written(form.checkpoint(read(form).version, fields))
        assertThrows(IllegalArgumentException::class.java) { DraftOwner(DraftForm.CUSTOM_EXERCISE, "\uD800") }
        assertEquals(saved, read(form))
    }

    @Test fun missingGenerationDoesNotInventAReplacementForExistingCheckpoints() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        val saved = written(form.checkpoint(read(form).version, fields))
        assertTrue(File(root, "generation.json").delete())
        assertEquals(DraftRead.Unavailable(DraftReadProblem.CORRUPT), form.read())
        assertFalse(File(root, "generation.json").exists())
        assertEquals(DraftChange.Unavailable(DraftReadProblem.CORRUPT), form.clear(saved.version))
    }

    @Test fun payloadsAndRawPathsDoNotAppearInReturnedStateDescriptions() = runTest {
        val root = temporary.newFolder()
        val form = binding(root)
        val result = form.beginSave(read(form).version, fields, "command", "SECRET-COMMAND")
        val text = result.toString() + form.read().toString()
        for (secret in fields + listOf("SECRET-COMMAND", root.absolutePath)) assertFalse(text.contains(secret))
    }

    private class FailingFiles(private val afterReplacement: Boolean) : DraftFileIO by AtomicDraftFiles {
        override fun replace(file: File, bytes: ByteArray) {
            if (afterReplacement) AtomicDraftFiles.replace(file, bytes)
            throw IOException("synthetic unavailable storage")
        }
    }
}
