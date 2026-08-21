package com.sinura.personaltrainer.data.mapper

import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.local.relation.RoutineWithExercises
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleCredit
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.domain.ScheduleSlot
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.logging.AppLog
import java.time.DayOfWeek

/**
 * [credits] is passed in rather than read here: the junction lives in its own table, and a
 * mapper that queried for it would turn one catalog read into one query per row.
 */
fun ExerciseEntity.toDomain(credits: List<MuscleCredit> = emptyList()): Exercise = Exercise(
    id = id,
    name = name,
    muscleGroup = muscleGroup,
    notes = notes,
    isCustom = isCustom,
    equipment = EquipmentType.fromStorage(equipment),
    loadType = LoadType.fromStorage(loadType),
    movementKey = movementKey,
    imageKey = imageKey,
    muscles = credits,
)

fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    muscleGroup = muscleGroup,
    notes = notes,
    isCustom = isCustom,
    equipment = equipment.name,
    loadType = loadType.name,
    movementKey = movementKey,
    imageKey = imageKey,
    // One function computes every nameKey in the app. See MuscleNormalizer.nameKeyOf.
    nameKey = MuscleNormalizer.nameKeyOf(name),
)

fun RoutineWithExercises.toDomain(): Routine = Routine(
    id = routine.id,
    name = routine.name,
    notes = routine.notes,
    createdAt = routine.createdAt,
    updatedAt = routine.updatedAt,
    exercises = items
        .sortedBy { it.item.sortOrder }
        .map { rel ->
            RoutineExercise(
                id = rel.item.id,
                routineId = rel.item.routineId,
                exercise = rel.exercise.toDomain(),
                sortOrder = rel.item.sortOrder,
                targetSets = rel.item.targetSets,
                targetReps = rel.item.targetReps,
                targetWeightKg = rel.item.targetWeightKg,
                restSeconds = rel.item.restSeconds,
            )
        },
)

fun Routine.toEntity(): RoutineEntity = RoutineEntity(
    id = id,
    name = name,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RoutineExercise.toEntity(): RoutineExerciseEntity = RoutineExerciseEntity(
    id = id,
    routineId = routineId,
    exerciseId = exercise.id,
    sortOrder = sortOrder,
    targetSets = targetSets,
    targetReps = targetReps,
    targetWeightKg = targetWeightKg,
    restSeconds = restSeconds,
)

fun SessionWithDetails.toDomain(): WorkoutSession = WorkoutSession(
    id = session.id,
    routineId = session.routineId,
    routineName = session.routineName,
    date = session.date,
    notes = session.notes,
    durationMinutes = session.durationMinutes,
    startedAt = session.startedAt,
    finishedAt = session.finishedAt,
    exercises = exercises
        .sortedBy { it.item.sortOrder }
        .map { rel ->
            SessionExercise(
                id = rel.item.id,
                sessionId = rel.item.sessionId,
                exercise = rel.exercise.toDomain(),
                sortOrder = rel.item.sortOrder,
                targetSets = rel.item.targetSets,
                targetReps = rel.item.targetReps,
                targetWeightKg = rel.item.targetWeightKg,
                restSeconds = rel.item.restSeconds,
            )
        },
    sets = sets
        .sortedWith(compareBy({ it.set.completedAt }, { it.set.setNumber }))
        .map { rel ->
            SetLog(
                id = rel.set.id,
                sessionId = rel.set.sessionId,
                exerciseId = rel.set.exerciseId,
                exerciseName = rel.exercise.name,
                setNumber = rel.set.setNumber,
                weightKg = rel.set.weightKg,
                reps = rel.set.reps,
                rpe = rel.set.rpe,
                isWarmup = rel.set.isWarmup,
                completedAt = rel.set.completedAt,
            )
        },
)

fun WorkoutSessionEntity.toSummary(): WorkoutSession = WorkoutSession(
    id = id,
    routineId = routineId,
    routineName = routineName,
    date = date,
    notes = notes,
    durationMinutes = durationMinutes,
    startedAt = startedAt,
    finishedAt = finishedAt,
    exercises = emptyList(),
    sets = emptyList(),
)

/**
 * A stored slot, or null when the row cannot be trusted.
 *
 * Two ways a row can be nonsense: an unrecognised `focusKind` string (written by a build that
 * knew a kind this one does not, or by a hand-edited backup), and an `anchorDay` outside 0-6.
 * Neither is worth crashing the week over, and neither is worth guessing at — the slot is
 * dropped with a log and the derived week simply has one fewer session in it.
 */
fun ScheduleSlotEntity.toDomain(): ScheduleSlot? {
    val kind = focusKind?.let { raw ->
        SessionFocusKind.entries.firstOrNull { it.name == raw }
            ?: run {
                AppLog.w(MAPPER_TAG, "Dropping schedule slot $id: unknown focus kind '$raw'")
                return null
            }
    }
    if (routineId == null && kind == null) {
        AppLog.w(MAPPER_TAG, "Dropping schedule slot $id: neither a routine nor a focus")
        return null
    }
    val anchor = anchorDay?.let { day ->
        DayOfWeek.entries.getOrNull(day)
            ?: run {
                AppLog.w(MAPPER_TAG, "Dropping schedule slot $id: anchor day $day is not 0-6")
                return null
            }
    }
    return ScheduleSlot(
        id = id,
        position = position,
        routineId = routineId,
        focusKind = kind,
        anchorDay = anchor,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private const val MAPPER_TAG = "PT/Mappers"
