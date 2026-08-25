package com.sinura.personaltrainer.domain

/**
 * Project an activity's strength work onto [WorkoutSession] so heat,
 * records, and block review can read it without inventing cardio kilograms.
 *
 * Cardio-only activities return null. Mixed activities contribute only
 * their strength sets.
 */
/**
 * Strength graphs for heat and coach. Workouts arrive already windowed;
 * activities must use the same cutoff or old mixed sessions overweight
 * the 30-day pass.
 */
fun windowedInsightHistory(
    sessions: List<WorkoutSession>,
    activities: List<ActivitySession>,
    minPerformedAtMs: Long,
): List<WorkoutSession> = sessions + activities.mapNotNull { activity ->
    activity.toInsightSession()?.takeIf { it.performedAtMs() >= minPerformedAtMs }
}

fun ActivitySession.toInsightSession(): WorkoutSession? {
    if (strengthBlocks.isEmpty()) return null
    val exercises = strengthBlocks.mapIndexed { index, block ->
        SessionExercise(
            id = block.id,
            sessionId = id,
            exercise = Exercise(
                id = block.exerciseId,
                name = block.exerciseName,
                muscleGroup = block.muscles.firstOrNull()?.muscleKey.orEmpty(),
                notes = "",
                isCustom = false,
                equipment = block.equipment,
                loadType = block.loadType,
                muscles = block.muscles,
            ),
            sortOrder = index,
            targetSets = block.sets.size,
            targetReps = block.sets.firstOrNull()?.reps ?: 0,
            targetWeightKg = null,
            restSeconds = 0,
        )
    }
    val sets = strengthBlocks.flatMap { block ->
        block.sets.map { set ->
            SetLog(
                id = set.id,
                sessionId = id,
                exerciseId = block.exerciseId,
                exerciseName = block.exerciseName,
                setNumber = set.setNumber,
                weightKg = set.weightKg,
                reps = set.reps,
                rpe = set.rpe,
                isWarmup = set.isWarmup,
                completedAt = set.completedAtMs,
            )
        }
    }
    return WorkoutSession(
        id = id,
        routineId = null,
        routineName = title,
        date = performedStart.instantMillis,
        notes = notes,
        durationMinutes = 0,
        startedAt = performedStart.instantMillis,
        finishedAt = performedEnd?.instantMillis ?: performedStart.instantMillis,
        exercises = exercises,
        sets = sets,
    )
}

fun ActivitySession.strengthWork(): SetWork {
    var volumeKg = 0.0
    var bodyweightReps = 0
    strengthBlocks.forEach { block ->
        val loadClass = LoadClass.of(block.loadType)
        block.sets.forEach { set ->
            if (set.isWarmup) return@forEach
            val work = SetWork.of(set.weightKg, set.reps, loadClass)
            volumeKg += work.volumeKg
            bodyweightReps += work.bodyweightReps
        }
    }
    return SetWork(volumeKg = volumeKg, bodyweightReps = bodyweightReps)
}

fun ActivitySession.cardioMinutes(): Int {
    val seconds = cardioBlocks.sumOf { it.elapsedSeconds }
    return ((seconds + 30) / 60).toInt()
}
