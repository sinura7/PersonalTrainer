package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class CompletedNoteKind { STRENGTH, ACTIVITY }
internal data class CompletedNoteOwner(val kind: CompletedNoteKind, val id: String) {
    init { require(wellFormedDraftText(id)) }
    val key: String get() = kind.name + ":" + notesIdentity(id)
}
internal data class CompletedNoteRecord(val identity: String, val notes: String) {
    companion object {
        fun strength(session: WorkoutSession): CompletedNoteRecord? = session.takeIf { it.isFinished }?.let {
            CompletedNoteRecord(strengthIdentity(it.id, it.date, it.startedAt, it.finishedAt), it.notes.trim())
        }
        fun strengthIdentity(id: String, date: Long, start: Long, finish: Long?): String =
            notesIdentity(id, date.toString(), start.toString(), finish.toString())
        fun activity(session: ActivitySession): CompletedNoteRecord? = session.takeIf { it.isCompleted }?.let {
            CompletedNoteRecord(notesIdentity(it.id, it.origin.name, it.createdAtMs.toString(),
                it.performedStart.toString(), it.performedEnd.toString()), it.notes.trim())
        }
    }
}
private fun notesIdentity(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
    .digest(parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

internal data class CompletedNoteFields(val raw: String, val before: String, val ownerStamp: String) {
    override fun toString(): String = "CompletedNoteFields(redacted)"
}
internal data class CompletedNoteCommand(val before: String, val intended: String, val ownerStamp: String, val atMs: Long) {
    override fun toString(): String = "CompletedNoteCommand(redacted)"
}
internal class CompletedNotesHandle internal constructor(
    val owner: CompletedNoteOwner,
    internal val claim: Long,
    internal val fieldsBinding: DraftBinding<CompletedNoteFields, Unit>,
    internal val commandBinding: DraftBinding<CompletedNoteCommand, CompletedNoteCommand>,
    internal var fields: DraftCheckpoint<CompletedNoteFields, Unit>?,
    internal var command: DraftCheckpoint<CompletedNoteCommand, CompletedNoteCommand>?,
    internal var record: CompletedNoteRecord?,
    val resumed: Boolean,
    val problem: Boolean,
) {
    val raw: String? get() = fields?.draft?.raw ?: command?.pending?.value?.intended
    val pending: Boolean get() = command?.pending != null
}

/** Separate raw fields and frozen commands let typing continue during an older admitted write. */
internal class CompletedNotesDrafts(
    private val store: DurableDraftStore,
    private val maintenance: DbMaintenance,
    private val restoreOpen: () -> Boolean,
    private val readRecord: suspend (CompletedNoteOwner) -> CompletedNoteRecord?,
    /** Caller holds maintenance; implementations compare identity/notes inside their Room transaction. */
    private val writeLocked: suspend (CompletedNoteOwner, CompletedNoteRecord, String, Long) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val serial = Mutex()
    private val claims = mutableMapOf<CompletedNoteOwner, Long>()
    private var nextClaim = 0L

    suspend fun open(owner: CompletedNoteOwner): CompletedNotesHandle = ordered {
        val claim = ++nextClaim
        claims[owner] = claim
        val fields = store.bind(DraftOwner(DraftForm.COMPLETED_NOTES, "fields:" + owner.key), CompletedNoteFieldsCodec)
        val commands = store.bind(DraftOwner(DraftForm.COMPLETED_NOTES, "command:" + owner.key), CompletedNoteCommandCodec)
        var f = runCatchingCancellable { read(fields) }.getOrNull()
        var c = runCatchingCancellable { read(commands) }.getOrNull()
        val record = runCatchingCancellable { readRecord(owner) }.getOrNull()
        val resumed = f?.draft != null || c?.pending != null
        var problem = f == null || c == null || record == null
        if (!problem) {
            val actual = checkNotNull(record)
            val raw = f?.draft
            val pending = c?.pending?.value
            problem = raw?.ownerStamp?.let { it != actual.identity } == true ||
                pending?.ownerStamp?.let { it != actual.identity } == true ||
                (pending != null && actual.notes != pending.before && actual.notes != pending.intended) ||
                (pending == null && raw != null && actual.notes != raw.before && actual.notes != raw.raw.trim())
            if (!problem) {
                val reconciled = runCatchingCancellable {
                    if (checkNotNull(f).needsReconciliation) f = written(fields.reconcileOwner(f!!.version, f!!.currentGeneration))
                    if (checkNotNull(c).needsReconciliation) c = written(commands.reconcileOwner(c!!.version, c!!.currentGeneration))
                }
                problem = reconciled.isFailure
            }
        }
        CompletedNotesHandle(owner, claim, fields, commands, f, c, record, resumed, problem)
    }

    /** No application start redirect or business mutation occurs when raw notes are reconstructed. */
    suspend fun keep(handle: CompletedNotesHandle, raw: String) = ordered { keepLocked(handle, raw) }

    /** Preserve an already identified orphan/conflict privately; this never grants business admission. */
    suspend fun keepRetained(handle: CompletedNotesHandle, raw: String) = ordered {
        check(claims[handle.owner] == handle.claim)
        require(wellFormedDraftText(raw))
        val prior = checkNotNull(handle.fields)
        var current = read(handle.fieldsBinding)
        check(current.version == prior.version)
        val pending = handle.command?.pending?.value
        val base = current.draft ?: pending?.let { CompletedNoteFields(raw, it.before, it.ownerStamp) }
            ?: handle.record?.let { CompletedNoteFields(raw, it.notes, it.identity) }
            ?: error("The unfinished notes' original owner is unavailable")
        // Retention is private-only. The original identity/baseline remains attached to the
        // text, and the command stays in its old generation until a real owner is verified.
        if (current.needsReconciliation) {
            current = written(handle.fieldsBinding.reconcileOwner(current.version, current.currentGeneration))
            handle.fields = current
        }
        val wanted = base.copy(raw = raw)
        if (wanted == current.draft) return@ordered
        val change = handle.fieldsBinding.checkpoint(current.version, wanted)
        if (change is DraftChange.Written) handle.fields = change.checkpoint
        else {
            val observed = read(handle.fieldsBinding)
            check(observed.draft == wanted && !observed.needsReconciliation)
            handle.fields = observed
        }
    }

    private suspend fun keepLocked(handle: CompletedNotesHandle, raw: String) {
        requireActive(handle)
        require(wellFormedDraftText(raw))
        val record = checkNotNull(handle.record) { "Completed notes need owner reconciliation" }
        val before = checkNotNull(handle.fields) { "Unfinished notes are unavailable" }
        val current = read(handle.fieldsBinding)
        val wanted = CompletedNoteFields(raw, record.notes, record.identity)
        if (current.draft == wanted && !current.needsReconciliation) {
            handle.fields = current
            return
        }
        check(current.version == before.version && !current.needsReconciliation) { "Unfinished notes changed" }
        val change = handle.fieldsBinding.checkpoint(current.version, wanted)
        if (change is DraftChange.Written) handle.fields = change.checkpoint
        else {
            val observed = read(handle.fieldsBinding)
            check(observed.draft == wanted && !observed.needsReconciliation) { "Unfinished notes were not kept" }
            handle.fields = observed
        }
    }

    /** Every Save inspects uncertain work; only a confirmed private command may reach business IO. */
    suspend fun save(handle: CompletedNotesHandle, raw: String) = ordered {
        requireActive(handle)
        if (handle.fields?.draft == null) keepLocked(handle, raw)
        val retry = settlePrior(handle, raw)
        val actual = checkNotNull(readRecord(handle.owner)) { "Completed session is unavailable" }
        val base = checkNotNull(handle.record)
        check(actual.identity == base.identity && actual.notes == base.notes) { "Saved notes changed" }
        val intended = raw.trim()
        if (retry != null) {
            val command = retry.value
            check(actual.identity == command.ownerStamp && actual.notes == command.before)
            writeLocked(handle.owner, actual, command.intended, command.atMs)
            check(inspectLocked(handle) == DraftRecoveryOutcome.EXACT_SAVED)
            settleExact(handle)
            return@ordered
        }
        if (actual.notes == intended) {
            cleanupFields(handle, intended)
            return@ordered
        }
        val before = checkNotNull(handle.command)
        val current = read(handle.commandBinding)
        check(current.version == before.version && !current.needsReconciliation && current.pending == null)
        val command = CompletedNoteCommand(actual.notes, intended, actual.identity, nowMillis())
        val identity = UUID.randomUUID().toString()
        val admitted = handle.commandBinding.beginSave(current.version, command, identity, command)
        handle.command = if (admitted is DraftChange.Written) admitted.checkpoint else {
            val inspected = read(handle.commandBinding)
            check(!inspected.needsReconciliation && inspected.pending?.identity == identity &&
                inspected.pending?.value == command && inspected.pending?.phase == DraftCommandPhase.PENDING) {
                "Notes command was not durably admitted"
            }
            inspected
        }
        requireActive(handle)
        check(!restoreOpen())
        // A thrown/lost business acknowledgement keeps this exact command for readback on Retry.
        writeLocked(handle.owner, actual, intended, command.atMs)
        val outcome = inspectLocked(handle)
        check(outcome == DraftRecoveryOutcome.EXACT_SAVED) { "Notes save was not confirmed" }
        settleExact(handle)
    }

    private suspend fun settlePrior(handle: CompletedNotesHandle, raw: String): PendingDraftCommand<CompletedNoteCommand>? {
        val prior = checkNotNull(handle.command)
        val current = read(handle.commandBinding)
        check(current.version == prior.version && !current.needsReconciliation) { "Notes command changed" }
        handle.command = current
        if (current.pending == null) return null
        when (inspectLocked(handle)) {
            DraftRecoveryOutcome.EXACT_SAVED -> settleExact(handle)
            DraftRecoveryOutcome.UNCOMMITTED -> {
                val next = checkNotNull(handle.command)
                val pending = checkNotNull(next.pending)
                if (pending.value.intended == raw.trim()) {
                    handle.command = written(handle.commandBinding.retry(next.version, pending.identity))
                    return checkNotNull(handle.command).pending
                }
                handle.command = written(handle.commandBinding.resumeEditing(next.version, pending.identity))
                handle.command = written(handle.commandBinding.clear(checkNotNull(handle.command).version))
            }
            DraftRecoveryOutcome.CONFLICT, DraftRecoveryOutcome.UNAVAILABLE -> error("Notes need reconciliation")
        }
        return null
    }

    /** Readback only: neither reconstruction nor inspection silently replays a save. */
    suspend fun inspect(handle: CompletedNotesHandle): DraftRecoveryOutcome = ordered {
        requireActive(handle)
        val prior = checkNotNull(handle.command)
        val current = read(handle.commandBinding)
        check(current.version == prior.version && !current.needsReconciliation)
        handle.command = current
        val outcome = inspectLocked(handle)
        if (outcome == DraftRecoveryOutcome.EXACT_SAVED && current.pending != null) settleExact(handle)
        outcome
    }

    private suspend fun inspectLocked(handle: CompletedNotesHandle): DraftRecoveryOutcome {
        val pending = handle.command?.pending ?: return DraftRecoveryOutcome.UNCOMMITTED
        val command = pending.value
        val actual = runCatchingCancellable { readRecord(handle.owner) }.getOrElse { return DraftRecoveryOutcome.UNAVAILABLE }
            ?: return DraftRecoveryOutcome.CONFLICT
        val outcome = when {
            actual.identity != command.ownerStamp -> DraftRecoveryOutcome.CONFLICT
            actual.notes == command.intended -> DraftRecoveryOutcome.EXACT_SAVED
            actual.notes == command.before && pending.phase != DraftCommandPhase.ACCEPTED -> DraftRecoveryOutcome.UNCOMMITTED
            else -> DraftRecoveryOutcome.CONFLICT
        }
        if ((outcome == DraftRecoveryOutcome.EXACT_SAVED || outcome == DraftRecoveryOutcome.UNCOMMITTED) &&
            pending.phase != DraftCommandPhase.ACCEPTED) {
            val current = checkNotNull(handle.command)
            handle.command = written(handle.commandBinding.inspect(current.version, pending.identity, outcome))
        }
        return outcome
    }

    private suspend fun settleExact(handle: CompletedNotesHandle) {
        val current = checkNotNull(handle.command)
        val command = checkNotNull(current.pending).value
        val actual = checkNotNull(readRecord(handle.owner))
        check(actual.identity == command.ownerStamp && actual.notes == command.intended)
        handle.record = actual
        // Rebase a newer authored field; only equal confirmed text may be cleared.
        val fields = checkNotNull(handle.fields)
        val stored = read(handle.fieldsBinding)
        check(stored.version == fields.version && !stored.needsReconciliation)
        stored.draft?.let { raw ->
            check(raw.ownerStamp == actual.identity && (raw.before == command.before || raw.before == actual.notes))
            handle.fields = written(handle.fieldsBinding.checkpoint(stored.version, raw.copy(before = actual.notes)))
        }
        handle.command = written(handle.commandBinding.clear(current.version))
        cleanupFields(handle, actual.notes)
    }

    private suspend fun cleanupFields(handle: CompletedNotesHandle, confirmed: String) {
        val fields = checkNotNull(handle.fields)
        if (fields.draft?.raw?.trim() == confirmed) {
            handle.fields = written(handle.fieldsBinding.clear(fields.version))
        }
    }

    /** Explicit private-only discard preserves every already accepted business write. */
    suspend fun discard(handle: CompletedNotesHandle) = ordered {
        check(claims[handle.owner] == handle.claim) { "Notes belong to a newer visit" }
        discardBinding(handle.commandBinding)
        discardBinding(handle.fieldsBinding)
        claims.remove(handle.owner)
    }

    private suspend fun <D, C> discardBinding(binding: DraftBinding<D, C>) {
        val current = binding.read()
        val change = if (current is DraftRead.PresentCheckpoint)
            binding.discard(current.checkpoint.version, current.checkpoint.currentGeneration)
        else binding.discardUnreadable()
        written(change)
    }

    private fun requireActive(handle: CompletedNotesHandle) {
        check(claims[handle.owner] == handle.claim && !handle.problem && !restoreOpen()) { "Notes need owner reconciliation" }
    }
    private suspend fun <T> ordered(block: suspend () -> T): T = withContext(dispatcher) {
        maintenance.withMaintenanceLock { serial.withLock { check(!restoreOpen()); block() } }
    }
    private suspend fun <D, C> read(binding: DraftBinding<D, C>): DraftCheckpoint<D, C> =
        (binding.read() as? DraftRead.PresentCheckpoint)?.checkpoint ?: error("Private notes are unavailable")
    private fun <D, C> written(change: DraftChange<D, C>): DraftCheckpoint<D, C> =
        (change as? DraftChange.Written)?.checkpoint ?: error("Private notes operation was not confirmed")
}

internal object CompletedNoteFieldsCodec : DraftCodec<CompletedNoteFields, Unit> {
    override val format = "completed-note-fields"
    override val version = 1
    override fun encodeDraft(value: CompletedNoteFields) = JsonObject().apply {
        addProperty("raw", value.raw); addProperty("before", value.before); addProperty("ownerStamp", value.ownerStamp)
    }
    override fun decodeDraft(value: JsonElement): CompletedNoteFields {
        val obj = notesObject(value, setOf("raw", "before", "ownerStamp"))
        return CompletedNoteFields(obj.noteText("raw"), obj.noteText("before"), obj.noteStamp())
    }
    override fun encodeCommand(value: Unit): JsonElement = error("Fields do not admit business commands")
    override fun decodeCommand(value: JsonElement): Unit = error("Fields do not admit business commands")
}
internal object CompletedNoteCommandCodec : DraftCodec<CompletedNoteCommand, CompletedNoteCommand> {
    override val format = "completed-note-command"
    override val version = 1
    override fun encodeDraft(value: CompletedNoteCommand) = JsonObject().apply {
        addProperty("before", value.before); addProperty("intended", value.intended)
        addProperty("ownerStamp", value.ownerStamp); addProperty("atMs", value.atMs)
    }
    override fun decodeDraft(value: JsonElement): CompletedNoteCommand {
        val obj = notesObject(value, setOf("before", "intended", "ownerStamp", "atMs"))
        val time = obj["atMs"]
        require(time.isJsonPrimitive && time.asJsonPrimitive.isNumber)
        val atMs = checkNotNull(time.asString.toLongOrNull())
        require(atMs >= 0)
        val intended = obj.noteText("intended")
        require(intended == intended.trim())
        return CompletedNoteCommand(obj.noteText("before"), intended, obj.noteStamp(), atMs)
    }
    override fun encodeCommand(value: CompletedNoteCommand) = encodeDraft(value)
    override fun decodeCommand(value: JsonElement) = decodeDraft(value)
}
private fun notesObject(value: JsonElement, names: Set<String>): JsonObject {
    require(value.isJsonObject)
    return value.asJsonObject.also { require(it.keySet() == names) }
}
private fun JsonObject.noteText(name: String): String {
    val value = get(name)
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString && wellFormedDraftText(value.asString))
    return value.asString
}
private fun JsonObject.noteStamp(): String = noteText("ownerStamp").also { require(it.matches(Regex("[0-9a-f]{64}"))) }
