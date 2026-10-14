package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.WorkoutPickerSwap
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PickerSwapHandle(
    val binding: DraftBinding<WorkoutPickerSwap, WorkoutPickerSwap>,
    var checkpoint: DraftCheckpoint<WorkoutPickerSwap, WorkoutPickerSwap>,
    val sessionId: String,
    val itemId: String,
)

/** Private swap admission. Open/inspect never repeats a business mutation. */
internal class PickerSwapDrafts(
    private val store: DurableDraftStore,
    private val repository: WorkoutRepository,
    private val maintenance: DbMaintenance,
    private val restoreOpen: () -> Boolean,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun open(sessionId: String, itemId: String): PickerSwapHandle = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            openLocked(sessionId, itemId)
        }
    }

    /** The session pointer restores only private intent; it never executes the replacement. */
    suspend fun openPending(sessionId: String): PickerSwapHandle? = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            val pointer = readIndex(sessionId)
            val itemId = pointer.draft ?: return@withMaintenanceLock null
            val opened = openLocked(sessionId, itemId)
            if (opened.checkpoint.pending == null) {
                check(opened.checkpoint.draft == null)
                clearIndex(sessionId, itemId)
                null
            } else opened
        }
    }

    private suspend fun openLocked(sessionId: String, itemId: String): PickerSwapHandle {
        val binding = store.bind(swapOwner(sessionId, itemId), PickerSwapCodec)
        return PickerSwapHandle(binding, read(binding), sessionId, itemId).also { read(it) }
    }

    suspend fun admit(handle: PickerSwapHandle, sessionId: String, itemId: String, replacement: Exercise) = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            check(sessionId == handle.sessionId && itemId == handle.itemId)
            val current = read(handle)
            check(current.version == handle.checkpoint.version && current.version.owner == swapOwner(sessionId, itemId))
            current.pending?.let {
                check(it.value.before.sessionId == sessionId && it.value.before.id == itemId)
                check(it.value.after.exerciseId == replacement.id)
                handle.checkpoint = current
                return@withMaintenanceLock
            }
            val command = repository.preparePickerSwapLocked(sessionId = sessionId, itemId = itemId,
                replacement = replacement, resultId = UUID.randomUUID().toString())
            check(command.before.sessionId == handle.sessionId && command.before.id == handle.itemId)
            keepIndex(sessionId, itemId)
            var ready = current
            if (ready.needsReconciliation) ready = written(handle.binding.reconcileOwner(ready.version, ready.currentGeneration))
            val identity = swapIdentity(command)
            val changed = handle.binding.beginSave(expected = ready.version, draft = command,
                commandIdentity = identity, command = command)
            handle.checkpoint = if (changed is DraftChange.Written) changed.checkpoint else {
                val inspected = read(handle)
                check(!inspected.needsReconciliation && inspected.draft == command &&
                    inspected.pending?.identity == identity && inspected.pending.value == command)
                inspected
            }
        }
    }

    /** Confirmed private reset does not reverse a replacement already saved to the workout. */
    suspend fun discard(sessionId: String, itemId: String) = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            val pointer = readIndex(sessionId)
            check(pointer.draft == null || pointer.draft == itemId)
            val binding = store.bind(swapOwner(sessionId, itemId), PickerSwapCodec)
            clearPrivate(binding)
            clearIndex(sessionId, itemId)
        }
    }

    /** Explicit reset can quarantine an unreadable session link without touching Room. */
    suspend fun reset(sessionId: String, itemId: String?) = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            val binding = index(sessionId)
            val pointer = binding.read()
            val current = (pointer as? DraftRead.PresentCheckpoint)?.checkpoint
            current?.let { check(it.pending == null && (itemId == null || it.draft == null || it.draft == itemId)) }
            // An opaque link cannot identify its old child. Enumerate current source IDs
            // from this session only; no business row is deleted or recreated. Quarantine
            // also fences any opaque child whose source no longer exists.
            val sources = containerSources(sessionId) + listOfNotNull(itemId, current?.draft)
            sources.distinct().forEach { source -> clearPrivate(store.bind(swapOwner(sessionId, source), PickerSwapCodec)) }
            clearPrivate(binding)
        }
    }

    private suspend fun containerSources(sessionId: String): List<String> =
        repository.getSession(sessionId)?.exercises.orEmpty().map { it.id }

    private suspend fun <D, C> clearPrivate(binding: DraftBinding<D, C>) {
        val before = binding.read()
        val current = (before as? DraftRead.PresentCheckpoint)?.checkpoint
        if (current != null && current.draft == null && current.pending == null) return
        val changed = if (current == null) binding.discardUnreadableIfStillUnreadable()
            else binding.discard(current.version, current.currentGeneration)
        if (changed !is DraftChange.Written) {
            val after = (binding.read() as? DraftRead.PresentCheckpoint)?.checkpoint ?: error("Private reset was not confirmed")
            check(after.draft == null && after.pending == null && (current == null || after.version != current.version))
        }
    }

    private fun index(sessionId: String): DraftBinding<String, Unit> {
        require(sessionId.isNotBlank() && sessionId.length <= 256 && wellFormedDraftText(sessionId))
        return store.bind(DraftOwner(DraftForm.WORKOUT, "picker-swap-index:" + swapDigest(sessionId)), PickerSwapIndexCodec)
    }
    private suspend fun readIndex(sessionId: String): DraftCheckpoint<String, Unit> =
        (index(sessionId).read() as? DraftRead.PresentCheckpoint)?.checkpoint?.also { check(it.pending == null) }
            ?: error("Swap recovery link is unavailable")
    private suspend fun keepIndex(sessionId: String, itemId: String) {
        val binding = index(sessionId)
        var current = readIndex(sessionId)
        check(current.draft == null || current.draft == itemId)
        if (current.needsReconciliation) current = written(binding.reconcileOwner(current.version, current.currentGeneration))
        if (current.draft == itemId) return
        val changed = binding.checkpoint(current.version, itemId)
        if (changed !is DraftChange.Written) check(readIndex(sessionId).draft == itemId)
    }
    private suspend fun clearIndex(sessionId: String, itemId: String) {
        val binding = index(sessionId)
        val current = readIndex(sessionId)
        if (current.draft == null) return
        check(current.draft == itemId)
        val changed = binding.discard(current.version, current.currentGeneration)
        if (changed !is DraftChange.Written) check(readIndex(sessionId).draft == null)
    }

    suspend fun execute(handle: PickerSwapHandle, canCommit: () -> Boolean): SessionExerciseEntity = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            val current = read(handle)
            check(current.version == handle.checkpoint.version && !current.needsReconciliation)
            val pointer = readIndex(handle.sessionId)
            check(pointer.draft == handle.itemId && !pointer.needsReconciliation)
            val pending = checkNotNull(current.pending)
            check(pending.phase == DraftCommandPhase.PENDING && canCommit())
            repository.commitPickerSwapLocked(pending.value, canCommit)
        }
    }

    suspend fun inspect(handle: PickerSwapHandle): DraftRecoveryOutcome = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            var current = read(handle)
            val pending = current.pending ?: return@withMaintenanceLock DraftRecoveryOutcome.CONFLICT
            if (handle.checkpoint.pending?.value != pending.value) return@withMaintenanceLock DraftRecoveryOutcome.CONFLICT
            val outcome = repository.inspectPickerSwap(pending.value)
            if (outcome == DraftRecoveryOutcome.UNCOMMITTED && current.needsReconciliation)
                return@withMaintenanceLock DraftRecoveryOutcome.CONFLICT
            if (outcome == DraftRecoveryOutcome.EXACT_SAVED || outcome == DraftRecoveryOutcome.UNCOMMITTED) {
                if (current.needsReconciliation) current = written(handle.binding.reconcileOwner(current.version, current.currentGeneration))
                if (current.pending?.phase != DraftCommandPhase.ACCEPTED)
                    current = written(handle.binding.inspect(current.version, pending.identity, outcome))
            }
            handle.checkpoint = current
            outcome
        }
    }

    suspend fun retry(handle: PickerSwapHandle) = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            val current = read(handle)
            check(current.version == handle.checkpoint.version && !current.needsReconciliation)
            val pending = checkNotNull(current.pending)
            handle.checkpoint = written(handle.binding.retry(current.version, pending.identity))
        }
    }

    suspend fun finish(handle: PickerSwapHandle) = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            val current = read(handle)
            val pending = checkNotNull(current.pending)
            check(current.version == handle.checkpoint.version && pending.value == handle.checkpoint.pending?.value)
            check(pending.phase == DraftCommandPhase.ACCEPTED && repository.inspectPickerSwap(pending.value) == DraftRecoveryOutcome.EXACT_SAVED)
            val changed = handle.binding.clear(current.version)
            handle.checkpoint = if (changed is DraftChange.Written) changed.checkpoint else {
                val inspected = read(handle)
                check(inspected.draft == null && inspected.pending == null && inspected.version != current.version)
                inspected
            }
            clearIndex(handle.sessionId, handle.itemId)
        }
    }

    private suspend fun read(handle: PickerSwapHandle): DraftCheckpoint<WorkoutPickerSwap, WorkoutPickerSwap> = read(handle.binding).also { current ->
        check(current.version.owner == swapOwner(handle.sessionId, handle.itemId))
        current.draft?.let { check(it.before.sessionId == handle.sessionId && it.before.id == handle.itemId) }
    }
    private suspend fun read(binding: DraftBinding<WorkoutPickerSwap, WorkoutPickerSwap>): DraftCheckpoint<WorkoutPickerSwap, WorkoutPickerSwap> {
        val current = (binding.read() as? DraftRead.PresentCheckpoint)?.checkpoint ?: error("Swap checkpoint is unavailable")
        current.draft?.let { check(current.version.owner == swapOwner(it.before.sessionId, it.before.id)) }
        current.pending?.let { check(it.value == current.draft && it.identity == swapIdentity(it.value)) }
        return current
    }
    private fun <D, C> written(value: DraftChange<D, C>): DraftCheckpoint<D, C> =
        (value as? DraftChange.Written)?.checkpoint ?: error("Swap checkpoint was not confirmed")
}

private object PickerSwapIndexCodec : DraftCodec<String, Unit> {
    override val format = "picker-swap-link"
    override val version = 1
    override fun encodeDraft(value: String): JsonElement = JsonPrimitive(value).also { validate(value) }
    override fun decodeDraft(value: JsonElement): String = value.also {
        check(it.isJsonPrimitive && it.asJsonPrimitive.isString)
    }.asString.also(::validate)
    override fun encodeCommand(value: Unit): JsonElement = JsonNull.INSTANCE
    override fun decodeCommand(value: JsonElement) { check(value.isJsonNull) }
    private fun validate(value: String) { require(value.isNotBlank() && value.length <= 256 && wellFormedDraftText(value)) }
}

private fun swapOwner(sessionId: String, itemId: String): DraftOwner {
    listOf(sessionId, itemId).forEach { require(it.isNotBlank() && it.length <= 256 && wellFormedDraftText(it)) }
    return DraftOwner(DraftForm.WORKOUT, "picker-swap:" + swapDigest("${sessionId.length}:$sessionId${itemId.length}:$itemId"))
}
private fun swapIdentity(command: WorkoutPickerSwap) = swapDigest(PickerSwapCodec.encodeCommand(command).toString())
private fun swapDigest(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal object PickerSwapCodec : DraftCodec<WorkoutPickerSwap, WorkoutPickerSwap> {
    override val format = "picker-swap-command"
    override val version = 1
    override fun encodeDraft(value: WorkoutPickerSwap): JsonElement = JsonObject().apply {
        value.validate()
        addProperty("started", value.sessionStartedAt)
        add("before", row(value.before)); add("after", row(value.after))
        addProperty("load", value.replacementLoadType.name); addProperty("hold", value.replacementIsHold)
    }
    override fun encodeCommand(value: WorkoutPickerSwap): JsonElement = encodeDraft(value)
    override fun decodeCommand(value: JsonElement): WorkoutPickerSwap = decodeDraft(value)
    override fun decodeDraft(value: JsonElement): WorkoutPickerSwap {
        val item = value.asJsonObject
        check(item.keySet() == setOf("started", "before", "after", "load", "hold"))
        val started = number(item, "started").toLong()
        val load = item.get("load").also { check(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
        val hold = item.get("hold").also { check(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
        return WorkoutPickerSwap(sessionStartedAt = started, before = readRow(item.get("before")),
            after = readRow(item.get("after")), replacementLoadType = LoadType.valueOf(load), replacementIsHold = hold).also { it.validate() }
    }
    private fun row(value: SessionExerciseEntity) = JsonObject().apply {
        addProperty("id", value.id); addProperty("session", value.sessionId); addProperty("exercise", value.exerciseId)
        addProperty("order", value.sortOrder); addProperty("sets", value.targetSets); addProperty("reps", value.targetReps)
        addProperty("weight", value.targetWeightKg); addProperty("rest", value.restSeconds)
        addProperty("seconds", value.targetSeconds); addProperty("maximum", value.targetSecondsMax)
    }
    private fun readRow(value: JsonElement): SessionExerciseEntity {
        val item = value.asJsonObject
        check(item.keySet() == setOf("id", "session", "exercise", "order", "sets", "reps", "weight", "rest", "seconds", "maximum"))
        fun text(key: String) = item.get(key).also { check(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
        fun optional(key: String) = item.get(key).takeUnless { it.isJsonNull }?.let { number(item, key).toInt() }
        val weight = item.get("weight").takeUnless { it.isJsonNull }?.also {
            check(it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
        }?.asDouble?.also { check(it.isFinite()) }
        return SessionExerciseEntity(id = text("id"), sessionId = text("session"), exerciseId = text("exercise"),
            sortOrder = number(item, "order").toInt(), targetSets = number(item, "sets").toInt(), targetReps = number(item, "reps").toInt(),
            targetWeightKg = weight, restSeconds = number(item, "rest").toInt(), targetSeconds = optional("seconds"), targetSecondsMax = optional("maximum"))
    }
    private fun number(item: JsonObject, key: String): String = item.get(key).also {
        check(it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.asString.matches(Regex("-?[0-9]+")))
    }.asString
}
