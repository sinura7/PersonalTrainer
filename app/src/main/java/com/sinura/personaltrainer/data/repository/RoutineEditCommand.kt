package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import com.sinura.personaltrainer.domain.HoldWork

/** Private immutable editor intent; never a new backup or database representation. */
internal data class RoutineEditRecord(val routine: RoutineEntity, val items: List<RoutineExerciseEntity>) {
    fun validate() {
        require(routine.id.isNotBlank() && routine.name.isNotBlank() && routine.createdAt >= 0 && routine.updatedAt >= 0)
        require(items.map { it.id }.distinct().size == items.size)
        require(items.map { it.exerciseId }.distinct().size == items.size)
        require(items == items.sortedWith(compareBy<RoutineExerciseEntity> { it.sortOrder }.thenBy { it.id }))
        items.forEach { item ->
            require(item.id.isNotBlank() && item.routineId == routine.id && item.exerciseId.isNotBlank())
            require(item.sortOrder >= 0 && item.targetSets >= 1 && item.targetReps >= 1 && item.restSeconds >= 0)
            require(item.targetWeightKg == null || (item.targetWeightKg.isFinite() && item.targetWeightKg > 0))
            require(item.targetSeconds == null || (item.targetSeconds >= HoldWork.MIN_SECONDS && item.targetReps == HoldWork.HOLD_REPS_PLACEHOLDER))
            require(item.targetSecondsMax == null || (item.targetSeconds != null && item.targetSecondsMax >= item.targetSeconds))
        }
    }
    override fun toString(): String = "RoutineEditRecord(redacted)"
}

internal data class RoutineEditChange(val id: String, val before: RoutineEditRecord?, val after: RoutineEditRecord?) {
    override fun toString(): String = "RoutineEditChange(redacted)"
}

internal data class RoutineEditCommand(
    val changes: List<RoutineEditChange>,
    /** Null leaves pins alone. Import may accept a previewed empty-cycle fill. */
    val pinsBefore: List<ScheduleSlotEntity>? = null,
    val pinsAfter: List<ScheduleSlotEntity>? = null,
) {
    fun validate() {
        require(changes.isNotEmpty() && changes.map { it.id }.distinct().size == changes.size)
        changes.forEach { change ->
            require(change.id.isNotBlank() && (change.before != null || change.after != null))
            listOfNotNull(change.before, change.after).forEach { record ->
                require(record.routine.id == change.id); record.validate()
            }
            if (change.before != null && change.after != null) require(change.before.routine.createdAt == change.after.routine.createdAt)
            if (change.after == null) require(change.before?.items?.isEmpty() == true)
        }
        require((pinsBefore == null) == (pinsAfter == null))
        pinsAfter?.let { pins ->
            require(pinsBefore!!.isEmpty() && pins.map { it.id }.distinct().size == pins.size)
            require(pins.map { it.position } == pins.indices.toList())
            pins.forEach { pin ->
                require(pin.id.isNotBlank() && pin.routineId != null && pin.focusKind == null)
                require(pin.anchorDay in 0..6 && pin.createdAt >= 0 && pin.updatedAt >= pin.createdAt)
                require(changes.any { it.id == pin.routineId && it.after != null })
            }
        }
    }
    override fun toString(): String = "RoutineEditCommand(redacted)"
}
