package com.sinura.personaltrainer.data.mapper

import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.local.relation.RoutineWithExercises
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WorkoutSession

fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    muscleGroup = muscleGroup,
    notes = notes,
    isCustom = isCustom,
)

fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    muscleGroup = muscleGroup,
    notes = notes,
    isCustom = isCustom,
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
