package com.sinura.personaltrainer.data.draft

import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.MuscleNormalizer
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CustomLiftHandle(
    val binding: DraftBinding<CustomLiftDraft, CustomLiftCommand>,
    var checkpoint: DraftCheckpoint<CustomLiftDraft, CustomLiftCommand>,
    val resumed: Boolean,
    val ownerConflict: Boolean,
) {
    var authored: CustomLiftDraft = checkNotNull(checkpoint.draft)
    var businessIssued: Boolean = false
}

/** Lock order is business maintenance -> private store. Never replay on open. */
internal class CustomExerciseDrafts(
    private val store: DurableDraftStore,
    private val repository: ExerciseRepository,
    private val maintenance: DbMaintenance,
    private val restoreOpen: () -> Boolean,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val index = store.bind(DraftOwner(DraftForm.CUSTOM_EXERCISE, "index:new-lift"), CustomLiftIndexCodec)

    suspend fun open(editId: String?, muscle: String): CustomLiftHandle = openOwned(editId, muscle, index)

    /** Picker drafts share admission/reconciliation, but never the Library's new-lift pointer. */
    suspend fun openForPicker(owner: DraftOwner, muscle: String): CustomLiftHandle {
        require(owner.form in setOf(DraftForm.ROUTINE, DraftForm.WORKOUT, DraftForm.ACTIVITY, DraftForm.ONBOARDING))
        val scope = MessageDigest.getInstance("SHA-256")
            .digest((owner.form.name + "\u0000" + owner.identity).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val scopedIndex = store.bind(DraftOwner(DraftForm.CUSTOM_EXERCISE, "picker-index:$scope"), CustomLiftIndexCodec)
        return openOwned(null, muscle, scopedIndex)
    }

    private suspend fun openOwned(editId: String?, muscle: String,
        draftIndex: DraftBinding<String, String>): CustomLiftHandle = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            val identity = editId ?: newIdentity(draftIndex)
            val key = if (editId == null) "create:$identity" else editOwner(identity)
            val binding = store.bind(DraftOwner(DraftForm.CUSTOM_EXERCISE, key), CustomLiftCodec)
            var current = load(binding)
            val resumed = current.draft != null
            if (current.draft == null) {
                if (current.needsReconciliation) current = written(binding.reconcileOwner(current.version, current.currentGeneration))
                val before = editId?.let { repository.customDraftRecord(it) }
                check(editId == null || before?.row?.isCustom == true)
                val initial = CustomLiftDraft(
                    createId = "ex-custom-${if (editId == null) identity else UUID.randomUUID()}", editId = editId,
                    name = before?.row?.name.orEmpty(), muscle = before?.row?.muscleGroup ?: muscle,
                    notes = before?.row?.notes.orEmpty(),
                    loadType = before?.row?.loadType?.let(LoadType::fromStorage) ?: LoadType.EXTERNAL,
                    before = before,
                )
                current = written(binding.checkpoint(current.version, initial))
            }
            val draft = checkNotNull(current.draft)
            val actual = repository.customDraftRecord(draft.editId ?: draft.createId)
            val conflict = if (current.pending != null) false else actual != draft.before
            if (current.needsReconciliation && current.pending == null && !conflict) {
                current = written(binding.reconcileOwner(current.version, current.currentGeneration))
            }
            CustomLiftHandle(binding, current, resumed, conflict)
        }
    }

    private suspend fun newIdentity(draftIndex: DraftBinding<String, String>): String {
        var pointer = available(draftIndex.read())
        if (pointer.needsReconciliation) pointer = written(draftIndex.reconcileOwner(pointer.version, pointer.currentGeneration))
        pointer.draft?.let { id ->
            val prior = load(store.bind(DraftOwner(DraftForm.CUSTOM_EXERCISE, "create:$id"), CustomLiftCodec))
            if (prior.draft != null) return id
        }
        val id = UUID.randomUUID().toString()
        written(draftIndex.checkpoint(pointer.version, id))
        return id
    }

    suspend fun checkpoint(handle: CustomLiftHandle, fields: CustomLiftDraft) {
        require(fields.createId == handle.checkpoint.draft?.createId && fields.editId == handle.checkpoint.draft?.editId)
        handle.authored = fields
        handle.checkpoint = written(handle.binding.checkpoint(handle.checkpoint.version, fields))
    }

    suspend fun admit(handle: CustomLiftHandle, fields: CustomLiftDraft) {
        require(fields.createId == handle.authored.createId && fields.editId == handle.authored.editId && fields.before == handle.authored.before)
        handle.authored = fields
        val stamp = maxOf(nowMillis(), fields.before?.row?.updatedAtMs?.let { Math.addExact(it, 1) } ?: 0L)
        handle.checkpoint = written(handle.binding.beginSave(
            expected = handle.checkpoint.version, draft = fields, commandIdentity = UUID.randomUUID().toString(),
            command = commandFor(fields, stamp),
        ))
    }

    private fun commandFor(fields: CustomLiftDraft, stamp: Long): CustomLiftCommand {
        val group = checkNotNull(MuscleGroups.resolved(fields.muscle))
        val name = fields.name.trim().also { require(it.isNotBlank()) }
        val id = fields.editId ?: fields.createId
        val before = fields.before
        val equipment = before?.row?.equipment?.let { fields.loadType.kitFor(EquipmentType.fromStorage(it)) }
            ?: fields.loadType.defaultEquipment
        val row = before?.row?.copy(
            name = name, muscleGroup = group, notes = fields.notes.trim(),
            loadType = fields.loadType.name, equipment = equipment.name,
            nameKey = MuscleNormalizer.nameKeyOf(name), updatedAtMs = stamp,
        ) ?: ExerciseEntity(
            id = id, name = name, muscleGroup = group, notes = fields.notes.trim(), isCustom = true,
            loadType = fields.loadType.name, equipment = equipment.name,
            nameKey = MuscleNormalizer.nameKeyOf(name), updatedAtMs = stamp,
        )
        val credits = MuscleNormalizer.deriveCredits(group).map {
            ExerciseMuscleEntity(exerciseId = id, muscleKey = it.muscleKey, weight = it.weight)
        }.sortedBy { it.muscleKey }
        return CustomLiftCommand(CustomLiftRecord(row, credits), before)
    }

    /** Re-read admitted identity inside the same coordinator as restore and custom mutations. */
    suspend fun execute(handle: CustomLiftHandle, canCommit: () -> Boolean = { true }): SaveExerciseResult? = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            val current = load(handle.binding)
            check(current.version == handle.checkpoint.version && !current.needsReconciliation)
            val pending = checkNotNull(current.pending)
            check(pending.phase == DraftCommandPhase.PENDING)
            check(canCommit())
            handle.businessIssued = true
            repository.commitCustomDraft(pending.value, canCommit)
        }
    }

    suspend fun inspect(handle: CustomLiftHandle): DraftRecoveryOutcome = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            val current = load(handle.binding)
            if (current.pending == null) {
                if (current.draft == null && handle.checkpoint.pending?.phase == DraftCommandPhase.ACCEPTED) {
                    handle.checkpoint = current
                    return@withMaintenanceLock DraftRecoveryOutcome.EXACT_SAVED
                }
                if (!handle.businessIssued && !current.needsReconciliation && (current.version == handle.checkpoint.version || current.draft == handle.authored)) {
                    handle.checkpoint = current
                    if (current.draft != handle.authored) handle.checkpoint = written(handle.binding.checkpoint(current.version, handle.authored))
                    return@withMaintenanceLock DraftRecoveryOutcome.UNCOMMITTED
                }
                return@withMaintenanceLock DraftRecoveryOutcome.CONFLICT
            }
            if (current.draft != handle.authored) return@withMaintenanceLock DraftRecoveryOutcome.CONFLICT
            handle.checkpoint = current
            val pending = current.pending
            val command = pending.value
            val actual = repository.customDraftRecord(command.intended.row.id)
            val outcome = when {
                pending.phase == DraftCommandPhase.ACCEPTED -> DraftRecoveryOutcome.EXACT_SAVED
                actual == command.intended -> DraftRecoveryOutcome.EXACT_SAVED
                current.needsReconciliation -> DraftRecoveryOutcome.CONFLICT
                actual == command.before -> DraftRecoveryOutcome.UNCOMMITTED
                else -> DraftRecoveryOutcome.CONFLICT
            }
            if (outcome == DraftRecoveryOutcome.EXACT_SAVED || outcome == DraftRecoveryOutcome.UNCOMMITTED) {
                var next = current
                if (next.needsReconciliation) next = written(handle.binding.reconcileOwner(next.version, next.currentGeneration))
                if (next.pending?.phase != DraftCommandPhase.ACCEPTED) {
                    next = written(handle.binding.inspect(next.version, pending.identity, outcome))
                }
                handle.checkpoint = next
            }
            outcome
        }
    }

    suspend fun retry(handle: CustomLiftHandle) {
        val pending = checkNotNull(handle.checkpoint.pending)
        handle.checkpoint = written(handle.binding.retry(handle.checkpoint.version, pending.identity))
    }

    suspend fun editInstead(handle: CustomLiftHandle) {
        val pending = checkNotNull(handle.checkpoint.pending)
        check(pending.phase == DraftCommandPhase.RETRYABLE)
        handle.businessIssued = false
        handle.checkpoint = written(handle.binding.resumeEditing(handle.checkpoint.version, pending.identity))
    }

    suspend fun refreshCheckpoint(handle: CustomLiftHandle) {
        val current = load(handle.binding)
        check(current.pending == null && !current.needsReconciliation)
        check(current.version == handle.checkpoint.version || current.draft == handle.authored)
        handle.checkpoint = current
    }

    suspend fun keepBeforeClose(handle: CustomLiftHandle, fields: CustomLiftDraft) {
        val current = load(handle.binding)
        if (current.draft == fields) { handle.checkpoint = current; return }
        check(!handle.businessIssued && current.pending == null && !current.needsReconciliation && current.version == handle.checkpoint.version)
        checkpoint(handle, fields)
    }

    suspend fun finish(handle: CustomLiftHandle) {
        if (handle.checkpoint.draft == null && handle.checkpoint.pending == null) return
        handle.checkpoint = written(handle.binding.clear(handle.checkpoint.version))
    }

    suspend fun discard(handle: CustomLiftHandle) = withContext(dispatcher) {
        maintenance.withMaintenanceLock {
            check(!restoreOpen())
            val current = load(handle.binding)
            if (current.draft == null && current.pending == null) { handle.checkpoint = current; return@withMaintenanceLock }
            check(current.draft == handle.authored || current.version == handle.checkpoint.version)
            handle.checkpoint = written(handle.binding.discard(current.version, current.currentGeneration))
        }
    }

    private fun <D, C> available(value: DraftRead<D, C>): DraftCheckpoint<D, C> =
        (value as? DraftRead.PresentCheckpoint)?.checkpoint ?: error("Private draft unavailable")
    private fun <D, C> written(value: DraftChange<D, C>): DraftCheckpoint<D, C> =
        (value as? DraftChange.Written)?.checkpoint ?: error("Private checkpoint not confirmed")

    private suspend fun load(binding: DraftBinding<CustomLiftDraft, CustomLiftCommand>): DraftCheckpoint<CustomLiftDraft, CustomLiftCommand> =
        available(binding.read()).also { current ->
            val fields = current.draft ?: return@also
            val owner = current.version.owner.identity
            require(if (fields.editId == null) owner == "create:${fields.createId.removePrefix("ex-custom-")}" else owner == editOwner(fields.editId))
            require(fields.before == null || fields.before.row.isCustom)
            current.pending?.let { pending ->
                require(pending.value.before == fields.before)
                val intended = pending.value.intended
                require(intended.row.updatedAtMs >= 0 && (fields.before == null || intended.row.updatedAtMs > fields.before.row.updatedAtMs))
                require(pending.value == commandFor(fields, intended.row.updatedAtMs))
            }
        }

    private fun editOwner(id: String): String = "edit:" + MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
