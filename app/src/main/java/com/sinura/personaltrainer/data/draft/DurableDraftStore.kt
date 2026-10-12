package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * One process-owned, private checkpoint service (ADR-033). All file IO is ordered off-main.
 * Callers await Written before promising preservation or issuing a business-data write.
 * Unavailable can mean replacement landed before the acknowledgement failed: read/inspect,
 * never infer that business work is uncommitted. This service never writes business data.
 */
internal class DurableDraftStore(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val files: DraftFileIO = AtomicDraftFiles,
) {
    fun <D, C> bind(owner: DraftOwner, codec: DraftCodec<D, C>): DraftBinding<D, C> {
        require(codec.format.isNotBlank() && codec.version > 0)
        return DraftBinding(this, owner, codec)
    }

    /** The restore coordinator must exclude business writes while changing generations. */
    suspend fun advanceGeneration(expected: String): DraftGenerationChange = ordered {
        try {
            if (generation() != expected) return@ordered DraftGenerationChange.Conflict
            val next = UUID.randomUUID().toString()
            writeGeneration(next)
            DraftGenerationChange.Changed(next)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: DraftFormatFailure) {
            DraftGenerationChange.Unavailable(failed.problem)
        } catch (_: Exception) {
            DraftGenerationChange.Unavailable(DraftReadProblem.STORAGE)
        }
    }

    internal suspend fun <D, C> read(owner: DraftOwner, codec: DraftCodec<D, C>): DraftRead<D, C> = ordered {
        try {
            DraftRead.Available(load(owner, codec, generation()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: DraftFormatFailure) {
            DraftRead.Unavailable(failed.problem)
        } catch (_: DraftTooLarge) {
            DraftRead.Unavailable(DraftReadProblem.TOO_LARGE)
        } catch (_: IOException) {
            DraftRead.Unavailable(DraftReadProblem.STORAGE)
        } catch (_: SecurityException) {
            DraftRead.Unavailable(DraftReadProblem.STORAGE)
        } catch (_: Exception) {
            DraftRead.Unavailable(DraftReadProblem.CORRUPT)
        }
    }

    internal suspend fun <D, C> change(
        owner: DraftOwner,
        codec: DraftCodec<D, C>,
        expected: DraftVersion,
        reconcileGeneration: String? = null,
        transform: (DraftCheckpoint<D, C>) -> DraftCheckpoint<D, C>,
    ): DraftChange<D, C> = ordered {
        try {
            rejectUnless(expected.owner == owner, DraftRejection.WRONG_OWNER)
            val current = load(owner, codec, generation())
            rejectUnless(current.version == expected, DraftRejection.STALE_VERSION)
            if (reconcileGeneration == null) {
                rejectUnless(!current.needsReconciliation, DraftRejection.RECONCILIATION_REQUIRED)
            } else {
                rejectUnless(current.needsReconciliation && current.currentGeneration == reconcileGeneration,
                    DraftRejection.STALE_VERSION)
            }
            val proposed = transform(current).copy(
                version = DraftVersion(owner, current.currentGeneration, Math.addExact(expected.revision, 1)),
            )
            val encoded = encode(proposed, codec)
            // Decode before replacement: a faulty codec cannot accept an unreadable checkpoint.
            val frozen = decode(encoded, owner, codec, current.currentGeneration)
            files.replace(ownerFile(owner), encoded)
            DraftChange.Written(frozen)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: DraftWriteRefused) {
            DraftChange.Rejected(refused.reason)
        } catch (failed: DraftFormatFailure) {
            DraftChange.Unavailable(failed.problem)
        } catch (_: DraftTooLarge) {
            DraftChange.Unavailable(DraftReadProblem.TOO_LARGE)
        } catch (_: IOException) {
            DraftChange.Unavailable(DraftReadProblem.STORAGE)
        } catch (_: SecurityException) {
            DraftChange.Unavailable(DraftReadProblem.STORAGE)
        } catch (_: Exception) {
            DraftChange.Rejected(DraftRejection.INVALID_DATA)
        }
    }

    private suspend fun <T> ordered(action: () -> T): T = withContext(dispatcher) {
        // Shared across bindings/store instances; no unbounded per-owner lock cache.
        ioLock.withLock {
            coroutineContext.ensureActive()
            // Complete an admitted atomic operation, even if its screen closes mid-replacement.
            withContext(NonCancellable) { action() }
        }
    }

    private fun generation(): String {
        val encoded = files.read(File(directory, GENERATION_FILE))
        if (encoded != null) {
            val record = objectFrom(encoded)
            supported(record.long("storageVersion") == STORAGE_VERSION)
            return record.text("generation").also(::validateGeneration)
        }
        if (directory.exists()) {
            val entries = directory.listFiles() ?: throw IOException()
            if (entries.any { it.name.endsWith(".json") }) corrupt()
        }
        return UUID.randomUUID().toString().also(::writeGeneration)
    }

    private fun writeGeneration(value: String) {
        val record = JsonObject().apply {
            addProperty("storageVersion", STORAGE_VERSION)
            addProperty("generation", value)
        }
        files.replace(File(directory, GENERATION_FILE), record.toString().toByteArray(Charsets.UTF_8))
    }

    private fun ownerFile(owner: DraftOwner): File {
        val input = owner.form.name + ":" + owner.identity
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(directory, "$name.json")
    }

    private fun <D, C> load(owner: DraftOwner, codec: DraftCodec<D, C>, generation: String): DraftCheckpoint<D, C> {
        val bytes = files.read(ownerFile(owner))
        return if (bytes == null) DraftCheckpoint(DraftVersion(owner, generation, 0), generation, null, null)
        else decode(bytes, owner, codec, generation)
    }

    private fun <D, C> encode(value: DraftCheckpoint<D, C>, codec: DraftCodec<D, C>): ByteArray {
        val record = JsonObject().apply {
            addProperty("storageVersion", STORAGE_VERSION)
            addProperty("payloadFormat", codec.format)
            addProperty("payloadVersion", codec.version)
            addProperty("form", value.version.owner.form.name)
            addProperty("owner", value.version.owner.identity)
            addProperty("generation", value.version.generation)
            addProperty("revision", value.version.revision)
            add("draft", value.draft?.let(codec::encodeDraft) ?: JsonNull.INSTANCE)
            add("pending", value.pending?.let { pending ->
                JsonObject().apply {
                    addProperty("identity", pending.identity)
                    addProperty("phase", pending.phase.name)
                    add("command", codec.encodeCommand(pending.value))
                }
            } ?: JsonNull.INSTANCE)
        }
        return record.toString().toByteArray(Charsets.UTF_8).also {
            if (it.size > MAX_DRAFT_BYTES) throw DraftTooLarge()
        }
    }

    private fun <D, C> decode(bytes: ByteArray, owner: DraftOwner, codec: DraftCodec<D, C>, generation: String): DraftCheckpoint<D, C> {
        try {
            val record = objectFrom(bytes)
            supported(record.long("storageVersion") == STORAGE_VERSION)
            supported(record.text("payloadFormat") == codec.format && record.long("payloadVersion") == codec.version.toLong())
            if (record.text("form") != owner.form.name || record.text("owner") != owner.identity) corrupt()
            val savedGeneration = record.text("generation").also(::validateGeneration)
            val revision = record.long("revision").also { if (it < 1) corrupt() }
            val draft = record.field("draft").takeUnless { it.isJsonNull }?.let(codec::decodeDraft)
            val pending = record.field("pending").takeUnless { it.isJsonNull }?.let { value ->
                if (!value.isJsonObject || draft == null) corrupt()
                val item = value.asJsonObject
                val identity = item.text("identity").also { if (it.isBlank() || it.length > 256) corrupt() }
                PendingDraftCommand(identity, codec.decodeCommand(item.field("command")),
                    DraftCommandPhase.valueOf(item.text("phase")))
            }
            return DraftCheckpoint(DraftVersion(owner, savedGeneration, revision), generation, draft, pending)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: DraftFormatFailure) {
            throw failed
        } catch (_: Exception) {
            corrupt()
        }
    }

    private fun objectFrom(bytes: ByteArray): JsonObject {
        try {
            val value = JsonParser.parseString(bytes.toString(Charsets.UTF_8))
            if (!value.isJsonObject) corrupt()
            return value.asJsonObject
        } catch (failed: DraftFormatFailure) {
            throw failed
        } catch (_: Exception) {
            corrupt()
        }
    }

    private fun JsonObject.field(name: String): JsonElement = get(name) ?: corrupt()
    private fun JsonObject.text(name: String): String = field(name).let {
        if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) corrupt()
        it.asString
    }
    private fun JsonObject.long(name: String): Long = field(name).let {
        if (!it.isJsonPrimitive || !it.asJsonPrimitive.isNumber) corrupt()
        it.asString.toLongOrNull() ?: corrupt()
    }
    private fun validateGeneration(value: String) {
        val parsed = try { UUID.fromString(value) } catch (_: IllegalArgumentException) { corrupt() }
        if (parsed.toString() != value) corrupt()
    }
    private fun supported(value: Boolean) { if (!value) throw DraftFormatFailure(DraftReadProblem.UNSUPPORTED) }
    private fun corrupt(): Nothing = throw DraftFormatFailure(DraftReadProblem.CORRUPT)

    private companion object {
        val ioLock = Mutex()
        const val STORAGE_VERSION = 1L
        const val GENERATION_FILE = "generation.json"
    }
}

/** Typed form entry point. No read, restore or Retry automatically mutates business records. */
internal class DraftBinding<D, C> internal constructor(
    private val store: DurableDraftStore,
    private val owner: DraftOwner,
    private val codec: DraftCodec<D, C>,
) {
    suspend fun read(): DraftRead<D, C> = store.read(owner, codec)

    suspend fun checkpoint(expected: DraftVersion, draft: D): DraftChange<D, C> = store.change(owner, codec, expected) {
        rejectUnless(it.pending == null, DraftRejection.PENDING_COMMAND)
        it.copy(draft = draft)
    }

    suspend fun beginSave(expected: DraftVersion, draft: D, commandIdentity: String, command: C): DraftChange<D, C> = store.change(owner, codec, expected) {
        rejectUnless(it.pending == null, DraftRejection.PENDING_COMMAND)
        rejectUnless(commandIdentity.isNotBlank() && commandIdentity.length <= 256, DraftRejection.INVALID_DATA)
        it.copy(draft = draft, pending = PendingDraftCommand(commandIdentity, command, DraftCommandPhase.PENDING))
    }

    /** Inspect the owning repository first; unavailable/conflicting reads cannot authorize Retry. */
    suspend fun inspect(expected: DraftVersion, commandIdentity: String, outcome: DraftRecoveryOutcome): DraftChange<D, C> = store.change(owner, codec, expected) {
        val pending = it.pending ?: throw DraftWriteRefused(DraftRejection.NO_COMMAND)
        rejectUnless(pending.identity == commandIdentity, DraftRejection.WRONG_COMMAND)
        rejectUnless(pending.phase != DraftCommandPhase.ACCEPTED, DraftRejection.PENDING_COMMAND)
        val phase = when (outcome) {
            DraftRecoveryOutcome.EXACT_SAVED -> DraftCommandPhase.ACCEPTED
            DraftRecoveryOutcome.UNCOMMITTED -> DraftCommandPhase.RETRYABLE
            DraftRecoveryOutcome.CONFLICT, DraftRecoveryOutcome.UNAVAILABLE -> throw DraftWriteRefused(DraftRejection.INSPECTION_REQUIRED)
        }
        it.copy(pending = pending.copy(phase = phase))
    }

    suspend fun retry(expected: DraftVersion, commandIdentity: String): DraftChange<D, C> = store.change(owner, codec, expected) {
        val pending = it.pending ?: throw DraftWriteRefused(DraftRejection.NO_COMMAND)
        rejectUnless(pending.identity == commandIdentity, DraftRejection.WRONG_COMMAND)
        rejectUnless(pending.phase == DraftCommandPhase.RETRYABLE, DraftRejection.INSPECTION_REQUIRED)
        it.copy(pending = pending.copy(phase = DraftCommandPhase.PENDING))
    }

    /** Preserve authored fields when inspection proved no write landed and the user edits instead. */
    suspend fun resumeEditing(expected: DraftVersion, commandIdentity: String): DraftChange<D, C> = store.change(owner, codec, expected) {
        val pending = it.pending ?: throw DraftWriteRefused(DraftRejection.NO_COMMAND)
        rejectUnless(pending.identity == commandIdentity, DraftRejection.WRONG_COMMAND)
        rejectUnless(pending.phase == DraftCommandPhase.RETRYABLE, DraftRejection.INSPECTION_REQUIRED)
        it.copy(pending = null)
    }

    /** A tombstone retains fencing after discard/acceptance; no queued old write can resurrect it. */
    suspend fun clear(expected: DraftVersion): DraftChange<D, C> = store.change(owner, codec, expected) {
        rejectUnless(it.pending?.phase != DraftCommandPhase.PENDING, DraftRejection.INSPECTION_REQUIRED)
        it.copy(draft = null, pending = null)
    }

    /** Called only after the area verifies restored ownership. Unaccepted commands need fresh inspection. */
    suspend fun reconcileOwner(expected: DraftVersion, currentGeneration: String): DraftChange<D, C> =
        store.change(owner = owner, codec = codec, expected = expected, reconcileGeneration = currentGeneration) {
            val pending = it.pending?.let { command ->
                if (command.phase == DraftCommandPhase.ACCEPTED) command
                else command.copy(phase = DraftCommandPhase.PENDING)
            }
            it.copy(pending = pending)
        }
}

private class DraftFormatFailure(val problem: DraftReadProblem) : Exception()
private class DraftWriteRefused(val reason: DraftRejection) : Exception()
private fun rejectUnless(value: Boolean, reason: DraftRejection) { if (!value) throw DraftWriteRefused(reason) }
