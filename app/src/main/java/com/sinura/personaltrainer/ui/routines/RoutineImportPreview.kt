package com.sinura.personaltrainer.ui.routines

import com.sinura.personaltrainer.data.draft.RoutineImportDetails
import com.sinura.personaltrainer.data.draft.RoutineImportedOwner
import com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import com.sinura.personaltrainer.data.repository.RoutineEditChange
import com.sinura.personaltrainer.data.repository.RoutineEditCommand
import com.sinura.personaltrainer.data.repository.RoutineEditRecord
import com.sinura.personaltrainer.domain.PastedSessionKind
import com.sinura.personaltrainer.domain.ScheduleSlot
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WorkoutPastePlan
import java.util.UUID

internal data class RoutineImportPreview(val command: RoutineEditCommand, val details: RoutineImportDetails)
internal class RoutineImportValidation(val reason: String) : IllegalArgumentException(reason)
internal data class RoutineImportState(
    val preview: RoutineEditCommand? = null,
    val details: RoutineImportDetails? = null,
    val visible: Boolean = false,
    val applying: Boolean = false,
    val keeping: Boolean = false,
    val applied: Boolean = false,
    val problem: String? = null,
    val outcome: DraftRecoveryOutcome? = null,
)

/** No write, routine creation or schedule mutation occurs while preparing this exact preview. */
internal fun prepareRoutineImport(
    primaryId: String,
    current: RoutineEditRecord?,
    plan: WorkoutPastePlan,
    existingPins: List<ScheduleSlot>,
    atMs: Long,
): RoutineImportPreview {
    require(plan.sessions.isNotEmpty() && atMs >= 0)
    if (current != null && current.items.isNotEmpty()) throw RoutineImportValidation("Paste into an empty routine; existing lifts will not be replaced.")
    val names = plan.sessions.map { it.name.trim().lowercase() }
    if (names.distinct().size != names.size) throw RoutineImportValidation("Two sessions have the same name. Give each one a distinct name before importing.")
    val owners = plan.sessions.mapIndexed { index, session ->
        RoutineImportedOwner(name = session.name, id = if (index == 0) primaryId else UUID.randomUUID().toString(),
            createdAt = if (index == 0) current?.routine?.createdAt ?: atMs else atMs)
    }
    val duplicates = mutableListOf<String>()
    val changes = plan.sessions.mapIndexed { index, session ->
        val id = owners[index].id
        val seen = mutableSetOf<String>()
        val lifts = session.lifts.filter { lift ->
            if (seen.add(lift.exercise.id)) true else { duplicates += "${session.name}: ${lift.raw}"; false }
        }
        val items = lifts.mapIndexed { order, lift -> RoutineExerciseEntity(
            id = UUID.randomUUID().toString(), routineId = id, exerciseId = lift.exercise.id, sortOrder = order,
            targetSets = lift.targetSets, targetReps = lift.targetReps, targetWeightKg = null,
            restSeconds = lift.restSeconds, targetSeconds = lift.targetSeconds, targetSecondsMax = lift.targetSecondsMax) }
        val before = if (index == 0) current else null
        val routine = RoutineEntity(id = id, name = session.name.trim(), notes = plan.combinedNotes(session).trim(),
            createdAt = before?.routine?.createdAt ?: atMs, updatedAt = maxOf(atMs, before?.routine?.updatedAt ?: atMs))
        RoutineEditChange(id = id, before = before, after = RoutineEditRecord(routine = routine, items = items))
    }
    val pinDays = setOf(Weekday.MONDAY, Weekday.TUESDAY, Weekday.THURSDAY, Weekday.FRIDAY, Weekday.SATURDAY)
    val eligible = plan.weekPins.flatMap { pin ->
        if (pin.weekday !in pinDays) emptyList() else pin.sessionNames.mapNotNull { name ->
            owners.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { pin.weekday to it.id }
        }
    }
    val pins = if (existingPins.isEmpty()) eligible.mapIndexed { index, (day, id) -> ScheduleSlotEntity(
        id = UUID.randomUUID().toString(), position = index, routineId = id, focusKind = null,
        anchorDay = day.ordinal, createdAt = atMs, updatedAt = atMs) } else emptyList()
    val command = RoutineEditCommand(changes = changes,
        pinsBefore = if (pins.isNotEmpty()) emptyList() else null,
        pinsAfter = pins.takeIf { it.isNotEmpty() })
    command.validate()
    val strength = plan.sessions.filter { it.kind == PastedSessionKind.STRENGTH }
    val unmatched = strength.flatMap { session -> session.unmatched.map { issue ->
        issue.copy(sessionName = session.name.takeIf { strength.size > 1 })
    } }
    val scope = when {
        existingPins.isNotEmpty() -> "Your existing training cycle is kept. No weekly pins are added."
        pins.isNotEmpty() -> "Adds ${pins.size} weekly cycle pins in the shown order."
        else -> "No weekly pins are added."
    }
    return RoutineImportPreview(command = command, details = RoutineImportDetails(
        owners = owners, unmatched = unmatched, duplicates = duplicates, scheduleScope = scope))
}
