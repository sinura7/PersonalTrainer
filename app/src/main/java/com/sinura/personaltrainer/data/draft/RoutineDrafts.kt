package com.sinura.personaltrainer.data.draft

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.RoutineEditCommand
import com.sinura.personaltrainer.data.repository.RoutineEditRecord
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.PastedUnmatched
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class RoutineTargetDraft(
    val itemId: String, val exerciseId: String,
    val sets: Int?, val reps: Int?, val weightKg: Double?, val restSeconds: Int?,
    val invalidReason: String? = null, val seconds: Int? = null, val secondsMax: Int? = null,
    val before: RoutineExerciseEntity? = null,
) { override fun toString(): String = "RoutineTargetDraft(redacted)" }
internal enum class RoutineNumberField { SETS, REPS, TIME, REST, WEIGHT }
internal data class RoutineNumberDraft(
    val itemId: String, val exerciseId: String, val field: RoutineNumberField,
    val text: String, val selectionStart: Int, val selectionEnd: Int,
    val unit: WeightUnit? = null, val open: Boolean = true,
) { override fun toString(): String = "RoutineNumberDraft(redacted)" }
internal data class RoutineImportedOwner(val name: String, val id: String, val createdAt: Long)
internal data class RoutineImportDetails(
    val owners: List<RoutineImportedOwner>,
    val unmatched: List<PastedUnmatched>,
    val duplicates: List<String>,
    val scheduleScope: String,
) {
    fun validate() {
        require(owners.isNotEmpty() && owners.map { it.id }.distinct().size == owners.size)
        owners.forEach { require(it.id.isNotBlank() && wellFormedDraftText(it.id) && it.name.isNotBlank() && wellFormedDraftText(it.name) && it.createdAt >= 0) }
        unmatched.forEach { issue ->
            require(wellFormedDraftText(issue.raw) && wellFormedDraftText(issue.reason))
            require(issue.names.all { wellFormedDraftText(it) })
            require(issue.sessionName == null || wellFormedDraftText(issue.sessionName))
        }
        require(duplicates.all { wellFormedDraftText(it) } && wellFormedDraftText(scheduleScope))
    }
    override fun toString(): String = "RoutineImportDetails(redacted)"
}
internal data class RoutineDraftFields(
    val routineId: String,
    val createdHere: Boolean,
    val initialExerciseId: String?,
    val initialExerciseAdded: Boolean,
    val before: RoutineEditRecord?,
    val name: String,
    val notes: String,
    val nameEdited: Boolean = false,
    val notesEdited: Boolean = false,
    val needsReview: Boolean = false,
    val targets: List<RoutineTargetDraft> = emptyList(),
    val numbers: List<RoutineNumberDraft> = emptyList(),
    val pasteText: String = "",
    val importPreview: RoutineEditCommand? = null,
    val importDetails: RoutineImportDetails? = null,
    val importVisible: Boolean = false,
    val importApplied: Boolean = false,
) {
    fun validate() {
        require(routineId.isNotBlank() && wellFormedDraftText(routineId))
        require(wellFormedDraftText(name) && wellFormedDraftText(notes) && wellFormedDraftText(pasteText))
        before?.let { require(it.routine.id == routineId); it.validate() }
        require(createdHere || before != null)
        require(initialExerciseId == null || (initialExerciseId.isNotBlank() && wellFormedDraftText(initialExerciseId)))
        require(targets.map { it.itemId }.distinct().size == targets.size)
        targets.forEach { t ->
            require(t.itemId.isNotBlank() && t.exerciseId.isNotBlank())
            require(t.weightKg == null || t.weightKg.isFinite())
            require(t.invalidReason == null || wellFormedDraftText(t.invalidReason))
            require(t.before == null || (t.before.id == t.itemId && t.before.exerciseId == t.exerciseId && t.before.routineId == routineId))
        }
        require(numbers.map { it.itemId to it.field }.distinct().size == numbers.size)
        numbers.forEach { n ->
            require(n.itemId.isNotBlank() && n.exerciseId.isNotBlank() && wellFormedDraftText(n.text))
            require(n.selectionStart in 0..n.text.length && n.selectionEnd in 0..n.text.length)
        }
        importPreview?.validate()
        importDetails?.validate()
        require(importPreview == null || importDetails != null && importPreview.changes.first().id == routineId)
    }
    override fun toString(): String = "RoutineDraftFields(redacted)"
}

internal class RoutineDraftHandle internal constructor(
    internal val key: String, internal val claim: Long,
    internal val fieldsBinding: DraftBinding<RoutineDraftFields, Unit>,
    internal val commandBinding: DraftBinding<RoutineEditCommand, RoutineEditCommand>,
    internal var fieldsCheckpoint: DraftCheckpoint<RoutineDraftFields, Unit>,
    internal var commandCheckpoint: DraftCheckpoint<RoutineEditCommand, RoutineEditCommand>,
    val resumed: Boolean,
    val problem: Boolean,
) {
    val fields: RoutineDraftFields get() = checkNotNull(fieldsCheckpoint.draft)
    val pending: Boolean get() = commandCheckpoint.pending != null
}

/** One editor's raw fields and frozen write-through commands have separate ordered checkpoints. */
internal class RoutineDrafts(
    private val store: DurableDraftStore,
    private val maintenance: DbMaintenance,
    private val restoreOpen: () -> Boolean,
    private val readRecord: suspend (String) -> RoutineEditRecord?,
    private val inspectSaved: suspend (RoutineEditCommand) -> DraftRecoveryOutcome,
    private val commitLocked: suspend (RoutineEditCommand) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val serial = Mutex()
    private var latestClaim = 0L
    private val claims = mutableMapOf<String, Long>()

    suspend fun open(id: String?, initialExerciseId: String?): RoutineDraftHandle = ordered {
        val newFields = fieldsBinding("new")
        val retainedNew = runCatchingCancellable { read(newFields) }.getOrNull()
        val key = if (id == null || retainedNew?.draft?.routineId == id) "new" else "existing:$id"
        val fieldsBinding = if (key == "new") newFields else fieldsBinding(key)
        var fields = if (key == "new") checkNotNull(retainedNew) { "The unfinished new routine could not be read" } else read(fieldsBinding)
        val commands = commandBinding(key)
        var command = read(commands)
        val resumed = fields.draft != null || command.pending != null
        if (fields.draft == null) {
            check(command.pending == null) { "The unfinished command has no owning fields" }
            val record = id?.let { readRecord(it) }
            check(id == null || record != null) { "The routine is no longer available" }
            val fresh = RoutineDraftFields(id ?: UUID.randomUUID().toString(), id == null,
                initialExerciseId, false, record, record?.routine?.name.orEmpty(), record?.routine?.notes.orEmpty())
            if (fields.needsReconciliation) fields = written(fieldsBinding.reconcileOwner(fields.version, fields.currentGeneration))
            fields = written(fieldsBinding.checkpoint(fields.version, fresh))
        }
        val draft = checkNotNull(fields.draft)
        check(id == null || draft.routineId == id)
        val actual = readRecord(draft.routineId)
        val problem = (draft.before != null && actual?.routine?.createdAt != draft.before.routine.createdAt) ||
            (actual == null && !(draft.createdHere && draft.before == null))
        if (fields.needsReconciliation) fields = written(fieldsBinding.reconcileOwner(fields.version, fields.currentGeneration))
        if (!problem && command.needsReconciliation && command.pending == null) command = written(commands.reconcileOwner(command.version, command.currentGeneration))
        // A durable visit lease also fences handles held by an older graph/store instance.
        fields = confirmFields(fieldsBinding, fields, checkNotNull(fields.draft))
        val claim = ++latestClaim
        claims[key] = claim
        RoutineDraftHandle(key, claim, fieldsBinding, commands, fields, command, resumed, problem)
    }

    suspend fun keep(handle: RoutineDraftHandle, fields: RoutineDraftFields) = ordered {
        active(handle); fields.validate()
        require(fields.routineId == handle.fields.routineId)
        var current = read(handle.fieldsBinding)
        check(current.version == handle.fieldsCheckpoint.version)
        // The adapter, not an older queued UI snapshot, owns the latest accepted baseline.
        val retained = checkNotNull(current.draft)
        val wanted = fields.copy(before = retained.before,
            createdHere = fields.createdHere && retained.createdHere,
            importApplied = fields.importApplied || retained.importApplied && fields.importPreview == retained.importPreview,
            name = if (!fields.nameEdited) retained.before?.routine?.name ?: fields.name else fields.name,
            notes = if (!fields.notesEdited) retained.before?.routine?.notes ?: fields.notes else fields.notes,
            targets = fields.targets.map { target -> target.copy(before =
                retained.targets.firstOrNull { it.itemId == target.itemId }?.before ?: target.before) })
        if (current.needsReconciliation) {
            // Raw retention is private only; it never authorizes overwriting a restored owner.
            current = written(handle.fieldsBinding.reconcileOwner(current.version, current.currentGeneration))
        }
        if (wanted != current.draft) {
            current = confirmFields(handle.fieldsBinding, current, wanted)
        }
        handle.fieldsCheckpoint = current
    }

    /** Inspection never submits. Unknown/conflicting data cannot enable a retry. */
    suspend fun inspect(handle: RoutineDraftHandle): DraftRecoveryOutcome = ordered {
        active(handle)
        if (handle.problem) DraftRecoveryOutcome.CONFLICT else inspectLocked(handle)
    }

    /** Explicit edit/Save admission. The old command is reconciled before a new intent is frozen. */
    suspend fun execute(handle: RoutineDraftHandle, prepare: suspend (RoutineDraftFields) -> RoutineEditCommand): DraftRecoveryOutcome = ordered {
        active(handle)
        if (handle.problem) return@ordered DraftRecoveryOutcome.CONFLICT
        if (handle.pending) {
            val old = submitPendingLocked(handle)
            if (old != DraftRecoveryOutcome.EXACT_SAVED) return@ordered old
        }
        val owningFields = handle.fields
        if (owningFields.needsReview) return@ordered DraftRecoveryOutcome.CONFLICT
        val actualOwner = runCatchingCancellable { readRecord(owningFields.routineId) }
            .getOrElse { return@ordered DraftRecoveryOutcome.UNAVAILABLE }
        val originalOwner = owningFields.before
        if (actualOwner != originalOwner) return@ordered DraftRecoveryOutcome.CONFLICT
        val command = runCatchingCancellable { prepare(handle.fields).also {
            it.validate(); authorised(it, handle.fields)
        } }
            .getOrElse { return@ordered if (it is IllegalStateException || it is IllegalArgumentException)
                DraftRecoveryOutcome.CONFLICT else DraftRecoveryOutcome.UNAVAILABLE }
        var current = read(handle.commandBinding)
        check(current.version == handle.commandCheckpoint.version && current.pending == null)
        if (current.needsReconciliation) current = written(handle.commandBinding.reconcileOwner(current.version, current.currentGeneration))
        val identity = UUID.randomUUID().toString()
        val admitted = handle.commandBinding.beginSave(current.version, command, identity, command)
        current = if (admitted is DraftChange.Written) admitted.checkpoint else read(handle.commandBinding).also {
            check(it.pending?.identity == identity && it.pending?.value == command && !it.needsReconciliation) { "The write command was not durably kept" }
        }
        handle.commandCheckpoint = current
        submitPendingLocked(handle)
    }

    suspend fun retry(handle: RoutineDraftHandle): DraftRecoveryOutcome = ordered {
        active(handle)
        if (handle.problem) DraftRecoveryOutcome.CONFLICT else submitPendingLocked(handle)
    }

    private suspend fun submitPendingLocked(handle: RoutineDraftHandle): DraftRecoveryOutcome {
        val frozen = handle.commandCheckpoint.pending?.value
        if (frozen != null && runCatching { authorised(frozen, handle.fields) }.isFailure) return DraftRecoveryOutcome.CONFLICT
        val observed = inspectLocked(handle)
        if (observed != DraftRecoveryOutcome.UNCOMMITTED) return observed
        val current = handle.commandCheckpoint
        val pending = checkNotNull(current.pending)
        check(pending.phase == DraftCommandPhase.RETRYABLE)
        handle.commandCheckpoint = written(handle.commandBinding.retry(current.version, pending.identity))
        runCatchingCancellable { commitLocked(pending.value) }
        return inspectLocked(handle)
    }

    private fun authorised(command: RoutineEditCommand, fields: RoutineDraftFields) {
        val owners = fields.importDetails?.owners.orEmpty().map { it.id }.toSet() + fields.routineId
        require(command.changes.all { it.id in owners })
        command.changes.forEach { change ->
            if (change.after == null) require(change.id == fields.routineId && fields.createdHere)
            if (change.before == null && change.after != null)
                require(change.id == fields.routineId && fields.createdHere || command == fields.importPreview)
        }
    }

    private suspend fun inspectLocked(handle: RoutineDraftHandle): DraftRecoveryOutcome {
        var current = read(handle.commandBinding)
        check(current.version == handle.commandCheckpoint.version) { "Another visit owns the unfinished command" }
        handle.commandCheckpoint = current
        val pending = current.pending ?: return if (handle.fields.needsReview) DraftRecoveryOutcome.CONFLICT else runCatchingCancellable { readRecord(handle.fields.routineId) }
            .fold(onSuccess = { if (it == handle.fields.before) DraftRecoveryOutcome.EXACT_SAVED else DraftRecoveryOutcome.CONFLICT },
                onFailure = { DraftRecoveryOutcome.UNAVAILABLE })
        val observed = runCatchingCancellable { inspectSaved(pending.value) }.getOrElse { return DraftRecoveryOutcome.UNAVAILABLE }
        if (observed != DraftRecoveryOutcome.EXACT_SAVED && observed != DraftRecoveryOutcome.UNCOMMITTED) return observed
        if (pending.phase == DraftCommandPhase.ACCEPTED && observed != DraftRecoveryOutcome.EXACT_SAVED) return DraftRecoveryOutcome.CONFLICT
        if (current.needsReconciliation) current = written(handle.commandBinding.reconcileOwner(current.version, current.currentGeneration))
        if (current.pending?.phase != DraftCommandPhase.ACCEPTED) current = written(handle.commandBinding.inspect(current.version, pending.identity, observed))
        handle.commandCheckpoint = current
        if (observed == DraftRecoveryOutcome.EXACT_SAVED) {
            // Keep newer raw fields. A cleanup failure leaves an accepted command; it cannot replay.
            val fields = read(handle.fieldsBinding)
            check(fields.version == handle.fieldsCheckpoint.version)
            val draft = checkNotNull(fields.draft)
            val actual = readRecord(draft.routineId)
            val own = pending.value.changes.firstOrNull { it.id == draft.routineId }
            val keptBaseline = actual?.let { record -> record.copy(routine = record.routine.copy(
                name = if (draft.nameEdited && own?.before?.routine?.name == own?.after?.routine?.name)
                    draft.before?.routine?.name ?: record.routine.name else record.routine.name,
                notes = if (draft.notesEdited && own?.before?.routine?.notes == own?.after?.routine?.notes)
                    draft.before?.routine?.notes ?: record.routine.notes else record.routine.notes)) }
            val acceptedImport = draft.importPreview == pending.value
            val kept = draft.copy(before = keptBaseline,
                name = if (draft.nameEdited) draft.name else keptBaseline?.routine?.name ?: draft.name,
                notes = if (draft.notesEdited) draft.notes else keptBaseline?.routine?.notes ?: draft.notes,
                createdHere = draft.createdHere && !acceptedImport,
                importApplied = draft.importApplied || acceptedImport,
                targets = draft.targets.map { target ->
                val prior = own?.before?.items?.firstOrNull { it.id == target.itemId }
                val after = own?.after?.items?.firstOrNull { it.id == target.itemId }
                if (prior != null && after != null && prior.copy(sortOrder = after.sortOrder) != after)
                    target.copy(before = after) else target
            })
            if (kept != draft) handle.fieldsCheckpoint = confirmFields(handle.fieldsBinding, fields, kept)
            val clear = handle.commandBinding.clear(current.version)
            handle.commandCheckpoint = if (clear is DraftChange.Written) clear.checkpoint else read(handle.commandBinding).also {
                check(it.pending == null && it.draft == null && it.version.revision > current.version.revision && !it.needsReconciliation)
            }
        }
        return observed
    }

    /** Explicit private discard; saved write-through edits are untouched. */
    suspend fun abandonPending(handle: RoutineDraftHandle) = ordered {
        active(handle)
        // Readback may settle a saved command, but abandonment never submits one.
        if (handle.pending) inspectLocked(handle)
        val commands = read(handle.commandBinding)
        check(commands.version == handle.commandCheckpoint.version)
        val change = handle.commandBinding.discard(commands.version, commands.currentGeneration)
        handle.commandCheckpoint = if (change is DraftChange.Written) change.checkpoint else read(handle.commandBinding).also {
            check(it.pending == null && it.draft == null && it.version.revision > commands.version.revision && !it.needsReconciliation)
        }
    }

    /** Explicit abandonment remains available even when no typed handle could be opened. */
    suspend fun discardOwner(id: String?) = ordered {
        val retainedNew = runCatchingCancellable { read(fieldsBinding("new")) }.getOrNull()
        val key = if (id == null || retainedNew?.draft?.routineId == id) "new" else "existing:$id"
        val fieldBinding = fieldsBinding(key)
        val commands = commandBinding(key)
        val fields = fieldBinding.read()
        val command = commands.read()
        check(fields is DraftRead.UnreadableCheckpoint || command is DraftRead.UnreadableCheckpoint) {
            "This draft is readable now. Reopen it before deciding what to discard."
        }
        check((fields as? DraftRead.UnreadableCheckpoint)?.problem != DraftReadProblem.STORAGE &&
            (command as? DraftRead.UnreadableCheckpoint)?.problem != DraftReadProblem.STORAGE) {
            "The checkpoint could not be inspected. Retry its read before discarding."
        }
        // Capture versions, then clear readable counterparts before quarantine changes the
        // generation. An older failed visit never reads and clears a newer readable draft.
        if (command is DraftRead.PresentCheckpoint) written(commands.discard(command.checkpoint.version, command.checkpoint.currentGeneration))
        if (fields is DraftRead.PresentCheckpoint) written(fieldBinding.discard(fields.checkpoint.version, fields.checkpoint.currentGeneration))
        if (command is DraftRead.UnreadableCheckpoint) written(commands.discardUnreadableIfStillUnreadable())
        if (fields is DraftRead.UnreadableCheckpoint) written(fieldBinding.discardUnreadableIfStillUnreadable())
        claims[key] = ++latestClaim
    }

    suspend fun discard(handle: RoutineDraftHandle) = ordered {
        active(handle)
        val fields = read(handle.fieldsBinding)
        val commands = read(handle.commandBinding)
        check(fields.version == handle.fieldsCheckpoint.version && commands.version == handle.commandCheckpoint.version)
        val commandChange = handle.commandBinding.discard(commands.version, commands.currentGeneration)
        handle.commandCheckpoint = if (commandChange is DraftChange.Written) commandChange.checkpoint else read(handle.commandBinding).also {
            check(it.pending == null && it.draft == null && it.version.revision > commands.version.revision && !it.needsReconciliation)
        }
        val fieldChange = handle.fieldsBinding.discard(fields.version, fields.currentGeneration)
        handle.fieldsCheckpoint = if (fieldChange is DraftChange.Written) fieldChange.checkpoint else read(handle.fieldsBinding).also {
            check(it.pending == null && it.draft == null && it.version.revision > fields.version.revision && !it.needsReconciliation)
        }
    }

    /** Explicit review retains authored values and only rebases against the same original owner. */
    suspend fun reviewCurrent(handle: RoutineDraftHandle) = ordered {
        active(handle)
        val fields = handle.fields
        val actual = checkNotNull(readRecord(fields.routineId)) { "The original routine is missing" }
        check(actual.routine.createdAt == fields.before?.routine?.createdAt) { "The original routine identity changed" }
        fields.targets.forEach { target ->
            check(actual.items.any { it.id == target.itemId && it.exerciseId == target.exerciseId }) { "An unfinished target's lift is missing or changed" }
        }
        var command = read(handle.commandBinding)
        command.pending?.let { pending ->
            check(pending.value.changes.size == 1 && pending.value.pinsAfter == null) { "Resolve the imported routines individually before replacing this preview" }
            val outcome = inspectSaved(pending.value)
            check(outcome != DraftRecoveryOutcome.UNAVAILABLE) { "The accepted result could not be read" }
            if (outcome == DraftRecoveryOutcome.EXACT_SAVED) { inspectLocked(handle); return@ordered }
            command = written(handle.commandBinding.releaseForReview(command.version, pending.identity, command.currentGeneration))
            handle.commandCheckpoint = command
        }
        val current = read(handle.fieldsBinding)
        val reviewed = fields.copy(before = actual, needsReview = false, targets = fields.targets.map { target ->
            target.copy(before = actual.items.first { it.id == target.itemId }) })
        handle.fieldsCheckpoint = confirmFields(handle.fieldsBinding, current, reviewed)
    }

    private suspend fun confirmFields(binding: DraftBinding<RoutineDraftFields, Unit>,
        prior: DraftCheckpoint<RoutineDraftFields, Unit>, fields: RoutineDraftFields): DraftCheckpoint<RoutineDraftFields, Unit> {
        val change = binding.checkpoint(prior.version, fields)
        return if (change is DraftChange.Written) change.checkpoint else read(binding).also {
            check(it.draft == fields && !it.needsReconciliation && it.version.revision > prior.version.revision) { "The latest fields were not durably kept" }
        }
    }

    private suspend fun active(handle: RoutineDraftHandle) {
        check(claims[handle.key] == handle.claim) { "A newer editor owns these fields" }
        check(read(handle.fieldsBinding).version == handle.fieldsCheckpoint.version) { "A newer durable visit owns these fields" }
    }
    private fun fieldsBinding(key: String) = store.bind(DraftOwner(DraftForm.ROUTINE, "fields:$key"), RoutineFieldsCodec)
    private fun commandBinding(key: String) = store.bind(DraftOwner(DraftForm.ROUTINE, "command:$key"), RoutineCommandCodec)
    private suspend fun <D, C> read(binding: DraftBinding<D, C>): DraftCheckpoint<D, C> =
        (binding.read() as? DraftRead.PresentCheckpoint)?.checkpoint ?: error("The unfinished routine could not be read")
    private fun <D, C> written(change: DraftChange<D, C>): DraftCheckpoint<D, C> =
        (change as? DraftChange.Written)?.checkpoint ?: error("The private routine operation was not confirmed")
    private suspend fun <T> ordered(block: suspend () -> T): T = withContext(dispatcher) {
        maintenance.withMaintenanceLock { serial.withLock { check(!restoreOpen()) { "Restore is still being reconciled" }; block() } }
    }
}

internal object RoutineFieldsCodec : DraftCodec<RoutineDraftFields, Unit> {
    private val gson = Gson()
    override val format = "routine-fields"
    override val version = 1
    override fun encodeDraft(value: RoutineDraftFields): JsonElement = gson.toJsonTree(value)
    override fun decodeDraft(value: JsonElement): RoutineDraftFields =
        decodeRoutineValue(value, RoutineDraftFields::class.java, gson).also { it.validate() }
    override fun encodeCommand(value: Unit): JsonElement = error("Fields cannot admit a business write")
    override fun decodeCommand(value: JsonElement): Unit = error("Fields cannot admit a business write")
}
internal object RoutineCommandCodec : DraftCodec<RoutineEditCommand, RoutineEditCommand> {
    private val gson = Gson()
    override val format = "routine-command"
    override val version = 1
    override fun encodeDraft(value: RoutineEditCommand): JsonElement = gson.toJsonTree(value)
    override fun decodeDraft(value: JsonElement): RoutineEditCommand =
        decodeRoutineValue(value, RoutineEditCommand::class.java, gson).also { it.validate() }
    override fun encodeCommand(value: RoutineEditCommand) = encodeDraft(value)
    override fun decodeCommand(value: JsonElement) = decodeDraft(value)
}
private fun <T> decodeRoutineValue(value: JsonElement, type: Class<T>, gson: Gson): T {
    require(value.isJsonObject)
    checkRoutineNumbers(value)
    val result = checkNotNull(gson.fromJson(value, type))
    require(gson.toJsonTree(result) == value) { "Unknown, absent or coerced checkpoint fields" }
    return result
}
private fun checkRoutineNumbers(value: JsonElement, field: String = "") {
    when {
        value.isJsonObject -> value.asJsonObject.entrySet().forEach { checkRoutineNumbers(it.value, it.key) }
        value.isJsonArray -> value.asJsonArray.forEach { checkRoutineNumbers(it, field) }
        value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> {
            if (field == "weightKg" || field == "targetWeightKg") require(value.asDouble.isFinite())
            else require(value.asString.matches(Regex("-?[0-9]+")) && value.asString.toLongOrNull() != null)
        }
    }
}
