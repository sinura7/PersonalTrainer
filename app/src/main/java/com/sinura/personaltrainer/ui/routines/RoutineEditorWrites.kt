package com.sinura.personaltrainer.ui.routines

import com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome
import com.sinura.personaltrainer.data.draft.RoutineDraftFields
import com.sinura.personaltrainer.data.draft.RoutineDraftHandle
import com.sinura.personaltrainer.data.draft.RoutineDrafts
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.repository.RoutineEditChange
import com.sinura.personaltrainer.data.repository.RoutineEditCommand
import com.sinura.personaltrainer.data.repository.RoutineEditRecord
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.Routine
import java.util.UUID

/** The public repository contract stays intact; shipping editor mutations use private admission. */
internal class RoutineEditorWrites(
    private val repository: RoutineRepository,
    private val drafts: RoutineDrafts?,
    private val readyHandle: suspend () -> RoutineDraftHandle?,
    private val checkpoint: suspend () -> Unit,
    private val refused: (DraftRecoveryOutcome) -> Unit,
) {
    fun observeById(id: String) = repository.observeById(id)
    suspend fun getById(id: String) = repository.getById(id)

    private suspend fun apply(prepare: suspend (RoutineDraftFields) -> RoutineEditCommand) {
        val port = checkNotNull(drafts)
        val handle = checkNotNull(readyHandle())
        checkpoint()
        val outcome = port.execute(handle, prepare)
        if (outcome != DraftRecoveryOutcome.EXACT_SAVED) {
            refused(outcome)
            error("The routine write needs reconciliation")
        }
    }
    private suspend fun record(id: String) = checkNotNull(repository.editorRecord(id)) { "The routine is no longer available" }
    private fun changed(before: RoutineEditRecord, items: List<RoutineExerciseEntity> = before.items,
        name: String = before.routine.name, notes: String = before.routine.notes): RoutineEditCommand {
        val ordered = items.sortedWith(compareBy<RoutineExerciseEntity> { it.sortOrder }.thenBy { it.id })
        if (ordered == before.items && name == before.routine.name && notes == before.routine.notes)
            return RoutineEditCommand(changes = listOf(RoutineEditChange(id = before.routine.id, before = before, after = before)))
        val after = RoutineEditRecord(before.routine.copy(name = name, notes = notes,
            updatedAt = maxOf(System.currentTimeMillis(), before.routine.updatedAt)),
            ordered)
        return RoutineEditCommand(listOf(RoutineEditChange(before.routine.id, before, after)))
    }

    suspend fun create(name: String, notes: String = ""): Routine {
        if (drafts == null) return repository.create(name, notes)
        val handle = checkNotNull(readyHandle())
        val id = handle.fields.routineId
        apply { fields ->
            check(fields.before == null && repository.editorRecord(id) == null) { "The original routine is already present" }
            val now = System.currentTimeMillis()
            val after = RoutineEditRecord(RoutineEntity(id, name.trim().ifBlank { "Untitled routine" }, notes.trim(), now, now), emptyList())
            RoutineEditCommand(listOf(RoutineEditChange(id, null, after)))
        }
        return checkNotNull(repository.getById(id))
    }

    suspend fun updateDetails(id: String, name: String, notes: String) {
        if (drafts == null) return repository.updateDetails(id, name, notes)
        apply { fields ->
            val before = record(id)
            val original = checkNotNull(fields.before)
            check(before.routine.createdAt == original.routine.createdAt)
            val nextName = if (fields.nameEdited) name.trim().ifBlank { before.routine.name } else before.routine.name
            val nextNotes = if (fields.notesEdited) notes.trim() else before.routine.notes
            check(!fields.nameEdited || before.routine.name == original.routine.name || before.routine.name == nextName) { "The saved name changed" }
            check(!fields.notesEdited || before.routine.notes == original.routine.notes || before.routine.notes == nextNotes) { "The saved notes changed" }
            changed(before, name = nextName, notes = nextNotes)
        }
    }

    suspend fun addExercise(routineId: String, exercise: Exercise, targetSets: Int, targetReps: Int,
        targetWeightKg: Double?, restSeconds: Int, targetSeconds: Int? = null, targetSecondsMax: Int? = null) {
        if (drafts == null) return repository.addExercise(routineId, exercise, targetSets, targetReps,
            targetWeightKg, restSeconds, targetSeconds, targetSecondsMax)
        apply { fields ->
            val before = record(routineId)
            if (routineId != fields.routineId) {
                val imported = checkNotNull(fields.importDetails?.owners?.firstOrNull { it.id == routineId })
                check(before.routine.createdAt == imported.createdAt) { "The imported routine identity changed" }
            }
            if (before.items.any { it.exerciseId == exercise.id }) return@apply RoutineEditCommand(listOf(RoutineEditChange(routineId, before, before)))
            val hold = HoldWork.isHold(exercise)
            val item = RoutineExerciseEntity(UUID.randomUUID().toString(), routineId, exercise.id,
                (before.items.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                targetSets.coerceAtLeast(1), if (hold) HoldWork.HOLD_REPS_PLACEHOLDER else targetReps.coerceAtLeast(1),
                targetWeightKg?.takeIf { it > 0 }, restSeconds.coerceAtLeast(0),
                if (hold) HoldWork.countdownSeconds(targetSeconds) else null, if (hold) targetSecondsMax else null)
            changed(before, items = before.items + item)
        }
    }

    suspend fun updateExercise(itemId: String, routineId: String, targetSets: Int, targetReps: Int,
        targetWeightKg: Double?, restSeconds: Int, targetSeconds: Int? = null, targetSecondsMax: Int? = null) {
        if (drafts == null) return repository.updateExercise(itemId, routineId, targetSets, targetReps,
            targetWeightKg, restSeconds, targetSeconds, targetSecondsMax)
        apply { fields ->
            val before = record(routineId)
            val item = checkNotNull(before.items.firstOrNull { it.id == itemId }) { "The target lift is no longer available" }
            val original = checkNotNull(fields.targets.firstOrNull { it.itemId == itemId }?.before
                ?: fields.before?.items?.firstOrNull { it.id == itemId })
            check(item.exerciseId == original.exerciseId)
            val hold = item.targetSeconds != null || targetSeconds != null
            val next = item.copy(targetSets = targetSets.coerceAtLeast(1),
                targetReps = if (hold) HoldWork.HOLD_REPS_PLACEHOLDER else targetReps.coerceAtLeast(1),
                targetWeightKg = targetWeightKg?.takeIf { it > 0 }, restSeconds = restSeconds.coerceAtLeast(0),
                targetSeconds = if (hold) HoldWork.countdownSeconds(targetSeconds ?: item.targetSeconds) else null,
                targetSecondsMax = if (hold) targetSecondsMax else null)
            fun sameTargets(a: RoutineExerciseEntity, b: RoutineExerciseEntity) =
                a.copy(sortOrder = b.sortOrder) == b
            check(sameTargets(item, original) || sameTargets(item, next)) { "The saved targets changed" }
            changed(before, items = before.items.map { if (it.id == itemId) next else it })
        }
    }

    suspend fun removeExercise(itemId: String, routineId: String) {
        if (drafts == null) return repository.removeExercise(itemId, routineId)
        apply { val before = record(routineId); changed(before, items = before.items.filterNot { it.id == itemId }) }
    }

    suspend fun moveExercise(routineId: String, itemId: String, direction: Int) {
        if (drafts == null) return repository.moveExercise(routineId, itemId, direction)
        require(direction == -1 || direction == 1)
        apply {
            val before = record(routineId)
            val items = before.items.toMutableList()
            val index = items.indexOfFirst { it.id == itemId }
            val target = index + direction
            if (index >= 0 && target in items.indices) {
                val a = items[index]; val b = items[target]
                items[index] = a.copy(sortOrder = b.sortOrder); items[target] = b.copy(sortOrder = a.sortOrder)
            }
            changed(before, items = items)
        }
    }

    suspend fun swapExercise(routineId: String, itemId: String, replacement: Exercise): String? {
        if (drafts == null) return repository.swapExercise(routineId, itemId, replacement)
        val current = record(routineId)
        if (current.items.none { it.id == itemId }) return "That lift is no longer in this routine."
        if (current.items.any { it.exerciseId == replacement.id }) return "${replacement.name} is already in this routine."
        apply {
            val before = record(routineId)
            check(before == current) { "The routine changed before the swap" }
            changed(before, items = before.items.map { if (it.id == itemId) it.copy(exerciseId = replacement.id, targetWeightKg = null) else it })
        }
        return null
    }

    suspend fun delete(id: String) {
        if (drafts == null) return repository.delete(id)
        apply { val before = record(id); check(before.items.isEmpty()) { "Only an empty new editor stub may be removed here" }
            RoutineEditCommand(listOf(RoutineEditChange(id, before, null))) }
    }
}
