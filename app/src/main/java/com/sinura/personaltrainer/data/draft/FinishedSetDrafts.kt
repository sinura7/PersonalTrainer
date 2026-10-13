package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetSaveResolution
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.workout.WorkoutNumberDraft
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One retained editor per finished session. Opening another row requires keeping or discarding this one. */
internal data class FinishedSetDraft(
    val ownerStamp: String,
    val original: WorkoutSetSave,
    val values: WorkoutSetValues,
    val numbers: List<WorkoutNumberDraft> = emptyList(),
) {
    val command: WorkoutSetSave get() = original.copy(values = values)
    override fun toString(): String = "FinishedSetDraft(redacted)"
}

internal class FinishedSetHandle internal constructor(
    val sessionId: String,
    internal val claim: Long,
    internal val binding: DraftBinding<FinishedSetDraft, WorkoutSetSave>,
    internal var checkpoint: DraftCheckpoint<FinishedSetDraft, WorkoutSetSave>?,
    val problem: Boolean,
    val ownerStamp: String?,
) {
    val draft: FinishedSetDraft? get() = checkpoint?.draft
    val pending: WorkoutSetSave? get() = checkpoint?.pending?.value
}

/** Private admission precedes every historical write. Reconstruction and inspection never replay it. */
internal class FinishedSetDrafts(
    private val store: DurableDraftStore,
    private val maintenance: DbMaintenance,
    private val restoreOpen: () -> Boolean,
    private val readSession: suspend (String) -> WorkoutSession?,
    private val inspectSaved: suspend (WorkoutSetSave) -> WorkoutSetSaveResolution,
    private val readSaved: suspend (WorkoutSetSave) -> WorkoutRepository.SavedWorkoutSet,
    /** Caller holds maintenance. The repository still checks the original row transactionally. */
    private val writeLocked: suspend (WorkoutSetSave) -> WorkoutRepository.SavedWorkoutSet,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val serial = Mutex()
    private val claims = mutableMapOf<String, Long>()
    private var nextClaim = 0L

    suspend fun open(sessionId: String): FinishedSetHandle = ordered {
        require(sessionId.isNotBlank() && wellFormedDraftText(sessionId))
        val claim = ++nextClaim
        claims[sessionId] = claim
        val binding = store.bind(DraftOwner(DraftForm.FINISHED_SET, sessionId), FinishedSetDraftCodec)
        var checkpoint = runCatchingCancellable { read(binding) }.getOrNull()
        val actual = runCatchingCancellable { readSession(sessionId) }.getOrNull()
        val draft = checkpoint?.draft
        val problem = checkpoint == null || actual == null || !actual.isFinished ||
            (draft != null && (draft.original.sessionId != sessionId || draft.ownerStamp != stamp(actual) ||
                (checkpoint.pending == null && !sameCapturedRow(draft, actual))))
        if (!problem && checkNotNull(checkpoint).needsReconciliation) {
            checkpoint = confirmed(binding.reconcileOwner(checkNotNull(checkpoint).version, checkNotNull(checkpoint).currentGeneration))
        }
        FinishedSetHandle(sessionId, claim, binding, checkpoint, problem, actual?.takeIf { it.isFinished }?.let(::stamp))
    }

    /** An orderly Close confirms the latest authored fields actually reached private storage. */
    suspend fun keepForExit(handle: FinishedSetHandle, draft: FinishedSetDraft) = ordered {
        check(claims[handle.sessionId] == handle.claim)
        val current = current(handle, allowReconciliation = true)
        if (current.draft != draft) keepLocked(handle, draft)
        check(current(handle, allowReconciliation = true).draft == draft) { "The latest set edit has not been kept" }
    }

    suspend fun keepFrozenForExit(handle: FinishedSetHandle, draft: FinishedSetDraft, command: WorkoutSetSave) = ordered {
        check(claims[handle.sessionId] == handle.claim)
        val current = current(handle, allowReconciliation = true)
        check(current.draft == draft) { "The latest set edit has not been kept" }
        check(draft.command == command && (current.pending == null || current.pending?.value == command))
    }

    suspend fun keep(handle: FinishedSetHandle, draft: FinishedSetDraft) = ordered { keepLocked(handle, draft) }
    private suspend fun keepLocked(handle: FinishedSetHandle, draft: FinishedSetDraft) {
        active(handle)
        require(draft.original.sessionId == handle.sessionId)
        val actual = checkNotNull(readSession(handle.sessionId))
        check(actual.isFinished && draft.ownerStamp == stamp(actual))
        val current = current(handle)
        check(current.pending == null)
        // A retained different editor cannot be overwritten by a late click or a new row.
        check(current.draft == null || current.draft?.original == draft.original)
        if (current.draft == draft) return
        val result = handle.binding.checkpoint(current.version, draft)
        handle.checkpoint = if (result is DraftChange.Written) result.checkpoint else {
            val observed = read(handle.binding)
            check(!observed.needsReconciliation && observed.draft == draft && observed.pending == null)
            observed
        }
    }

    suspend fun write(handle: FinishedSetHandle, command: WorkoutSetSave): WorkoutRepository.SavedWorkoutSet = ordered {
        active(handle)
        var current = current(handle)
        val fields = checkNotNull(current.draft) { "Keep this set editor before saving" }
        check(fields.command == command)
        val actual = checkNotNull(readSession(handle.sessionId))
        check(actual.isFinished && fields.ownerStamp == stamp(actual))
        val pending = current.pending
        if (pending == null) {
            val identity = command.setId
            val result = handle.binding.beginSave(current.version, fields, identity, command)
            current = if (result is DraftChange.Written) result.checkpoint else {
                val observed = read(handle.binding)
                check(!observed.needsReconciliation && observed.draft == fields &&
                    observed.pending?.identity == identity && observed.pending?.value == command)
                observed
            }
            handle.checkpoint = current
        } else {
            check(pending.value == command)
            val outcome = inspectLocked(handle, command)
            if (outcome == WorkoutSetSaveResolution.SAVED) return@ordered readSaved(command)
            check(outcome == WorkoutSetSaveResolution.UNSAVED) { "Check this saved set before retrying" }
            val retry = current(handle)
            handle.checkpoint = confirmed(handle.binding.retry(retry.version, checkNotNull(retry.pending).identity))
        }
        active(handle)
        // Even the first submission inspects its stable identity; no uncertain private acknowledgement grants a second row.
        when (inspectLocked(handle, command)) {
            WorkoutSetSaveResolution.SAVED -> return@ordered readSaved(command)
            WorkoutSetSaveResolution.CONFLICT -> throw WorkoutRepository.SetSaveConflict()
            WorkoutSetSaveResolution.UNSAVED -> {
                val next = current(handle)
                handle.checkpoint = confirmed(handle.binding.retry(next.version, checkNotNull(next.pending).identity))
            }
        }
        writeLocked(command)
    }

    suspend fun inspect(handle: FinishedSetHandle, command: WorkoutSetSave): WorkoutSetSaveResolution = ordered {
        active(handle)
        inspectLocked(handle, command)
    }

    private suspend fun inspectLocked(handle: FinishedSetHandle, command: WorkoutSetSave): WorkoutSetSaveResolution {
        var current = current(handle, allowReconciliation = true)
        val fields = checkNotNull(current.draft)
        check(fields.command == command)
        val actual = readSession(handle.sessionId)
        if (actual == null || !actual.isFinished || fields.ownerStamp != stamp(actual)) return WorkoutSetSaveResolution.CONFLICT
        val pending = current.pending
        check(pending == null || pending.value == command)
        val outcome = inspectSaved(command)
        if (current.needsReconciliation) {
            // A successful repository correction fences the shared private generation itself.
            // Only exact business readback may acknowledge that in-flight handle; absence in
            // a changed generation does not authorize Retry.
            if (outcome != WorkoutSetSaveResolution.SAVED) return WorkoutSetSaveResolution.CONFLICT
            current = confirmed(handle.binding.reconcileOwner(current.version, current.currentGeneration))
            handle.checkpoint = current
        }
        // An accepted command is never demoted, even if another owner subsequently corrects it.
        if (pending?.phase == DraftCommandPhase.ACCEPTED && outcome != WorkoutSetSaveResolution.SAVED)
            return WorkoutSetSaveResolution.CONFLICT
        if (pending != null && pending.phase != DraftCommandPhase.ACCEPTED && outcome != WorkoutSetSaveResolution.CONFLICT) {
            handle.checkpoint = confirmed(handle.binding.inspect(current.version, pending.identity,
                if (outcome == WorkoutSetSaveResolution.SAVED) DraftRecoveryOutcome.EXACT_SAVED else DraftRecoveryOutcome.UNCOMMITTED))
        }
        return outcome
    }

    suspend fun settle(handle: FinishedSetHandle, command: WorkoutSetSave) = ordered {
        active(handle)
        check(inspectLocked(handle, command) == WorkoutSetSaveResolution.SAVED)
        val current = current(handle)
        handle.checkpoint = confirmed(handle.binding.clear(current.version))
    }

    /** Edit values may release only a proven uncommitted command. Conflict review uses explicit private Discard. */
    suspend fun release(handle: FinishedSetHandle, command: WorkoutSetSave) = ordered {
        check(claims[handle.sessionId] == handle.claim)
        val before = current(handle, allowReconciliation = true)
        check(before.draft?.command == command && (before.pending == null || before.pending?.value == command))
        when (inspectSaved(command)) {
            WorkoutSetSaveResolution.SAVED -> error("Confirm the saved result before closing its command")
            WorkoutSetSaveResolution.UNSAVED -> {
                if (before.needsReconciliation) {
                    // A changed generation cannot make absence a retry permission. Private
                    // release for review keeps the old owner/baseline attached to the text.
                    before.pending?.let { handle.checkpoint = confirmed(handle.binding.releaseForReview(before.version, it.identity, before.currentGeneration)) }
                } else {
                    inspectLocked(handle, command)
                    val current = current(handle)
                    current.pending?.let { handle.checkpoint = confirmed(handle.binding.resumeEditing(current.version, it.identity)) }
                }
            }
            WorkoutSetSaveResolution.CONFLICT -> before.pending?.let {
                handle.checkpoint = confirmed(handle.binding.releaseForReview(before.version, it.identity, before.currentGeneration))
            }
        }
    }

    /** Explicit private-only discard may remove an orphan; it never removes a saved set. */
    suspend fun discard(handle: FinishedSetHandle) = ordered {
        check(claims[handle.sessionId] == handle.claim)
        val observed = handle.binding.read()
        handle.checkpoint = if (observed is DraftRead.PresentCheckpoint) {
            val current = observed.checkpoint
            check(current.version == handle.checkpoint?.version)
            confirmed(handle.binding.discard(current.version, current.currentGeneration))
        } else confirmed(handle.binding.discardUnreadable())
    }

    private suspend fun current(handle: FinishedSetHandle, allowReconciliation: Boolean = false): DraftCheckpoint<FinishedSetDraft, WorkoutSetSave> {
        val current = read(handle.binding)
        check(current.version == handle.checkpoint?.version && (allowReconciliation || !current.needsReconciliation))
        return current
    }
    private fun active(handle: FinishedSetHandle) {
        check(claims[handle.sessionId] == handle.claim && !handle.problem && !restoreOpen())
    }
    private fun sameCapturedRow(draft: FinishedSetDraft, session: WorkoutSession): Boolean {
        val original = draft.original
        if (!original.editing) return session.exercises.any { it.exercise.id == original.exerciseId } ||
            session.sets.any { it.exerciseId == original.exerciseId }
        val row = session.sets.firstOrNull { it.id == original.setId } ?: return false
        return row.sessionId == original.sessionId && row.exerciseId == original.exerciseId && row.completedAt == original.completedAt &&
            (WorkoutSetValues.from(row) == original.values || WorkoutSetValues.from(row) == draft.values)
    }
    private suspend fun <T> ordered(block: suspend () -> T): T = withContext(dispatcher) {
        maintenance.withMaintenanceLock { serial.withLock { check(!restoreOpen()); block() } }
    }
    private suspend fun read(binding: DraftBinding<FinishedSetDraft, WorkoutSetSave>) =
        (binding.read() as? DraftRead.PresentCheckpoint)?.checkpoint ?: error("Unfinished set is unavailable")
    private fun confirmed(change: DraftChange<FinishedSetDraft, WorkoutSetSave>) =
        (change as? DraftChange.Written)?.checkpoint ?: error("Unfinished set operation was not confirmed")

    companion object {
        fun stamp(session: WorkoutSession): String = CompletedNoteRecord.strengthIdentity(session.id, session.date, session.startedAt, session.finishedAt)
    }
}

internal object FinishedSetDraftCodec : DraftCodec<FinishedSetDraft, WorkoutSetSave> {
    override val format = "finished-set-editor"
    override val version = 1
    override fun encodeDraft(value: FinishedSetDraft) = JsonObject().apply {
        addProperty("ownerStamp", value.ownerStamp)
        add("original", WorkoutSetCheckpointCodec.encodeDraft(value.original))
        add("authored", WorkoutSetCheckpointCodec.encodeDraft(value.command))
        add("numbers", JsonArray().apply { value.numbers.forEach { add(encodeWorkoutNumber(it)) } })
    }
    override fun decodeDraft(value: JsonElement): FinishedSetDraft {
        require(value.isJsonObject)
        val obj = value.asJsonObject
        require(obj.keySet() == setOf("ownerStamp", "original", "authored", "numbers"))
        val stamp = obj["ownerStamp"].also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
        require(stamp.matches(Regex("[0-9a-f]{64}")))
        val original = WorkoutSetCheckpointCodec.decodeFinishedDraft(obj["original"])
        val authored = WorkoutSetCheckpointCodec.decodeFinishedDraft(obj["authored"])
        require(authored.copy(values = original.values) == original)
        val numbers = obj.getAsJsonArray("numbers").map(::decodeWorkoutNumber)
        require(numbers.all { it.exerciseId == original.exerciseId } && numbers.map { it.field }.distinct().size == numbers.size && numbers.count { it.open } <= 1)
        return FinishedSetDraft(stamp, original, authored.values, numbers)
    }
    override fun encodeCommand(value: WorkoutSetSave) = WorkoutSetCheckpointCodec.encodeCommand(value)
    override fun decodeCommand(value: JsonElement) = WorkoutSetCheckpointCodec.decodeFinishedDraft(value)
}
