package com.sinura.personaltrainer.domain

/**
 * A lift.
 *
 * [muscleGroup] is display text and stays free-form — the library filter, the planner and every
 * v1 backup read it. [muscles] is what the body map reads: explicit junction credits, one
 * primary at 1.0 and its secondaries. Every new field is defaulted so the dozens of existing
 * construction sites that only care about a name keep compiling and keep meaning the same thing.
 */
data class Exercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val notes: String,
    val isCustom: Boolean,
    val equipment: EquipmentType = EquipmentType.OTHER,
    val loadType: LoadType = LoadType.EXTERNAL,
    val movementKey: String? = null,
    val imageKey: String? = null,
    val muscles: List<MuscleCredit> = emptyList(),
)

data class RoutineExercise(
    val id: String,
    val routineId: String,
    val exercise: Exercise,
    val sortOrder: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val restSeconds: Int,
)

data class Routine(
    val id: String,
    val name: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val exercises: List<RoutineExercise>,
)

data class SessionExercise(
    val id: String,
    val sessionId: String,
    val exercise: Exercise,
    val sortOrder: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val restSeconds: Int,
)

data class SetLog(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val isWarmup: Boolean,
    val completedAt: Long,
)

data class WorkoutSession(
    val id: String,
    val routineId: String?,
    val routineName: String?,
    val date: Long,
    val notes: String,
    val durationMinutes: Int,
    val startedAt: Long,
    val finishedAt: Long?,
    val exercises: List<SessionExercise>,
    val sets: List<SetLog>,
) {
    val isFinished: Boolean get() = finishedAt != null

    /**
     * Working volume, using the same per-set rule as the heat map and the exercise history.
     *
     * This used to be a plain `weightKg * reps`, which scored every bodyweight set at zero
     * while [MuscleLoadCalculator] credited the same set at its bodyweight equivalent. A pull-up
     * session therefore read "0 kg" on History and lit up the body map — one app, two answers
     * to "how much did I lift".
     */
    /**
     * @param bodyweightKg see [MuscleLoadCalculator.setVolumeKg]. Defaulted rather than
     * required so every existing caller keeps exactly the number it showed before; the
     * surfaces that know the lifter's weight opt in by passing it.
     */
    fun workingVolumeKg(bodyweightKg: Double? = null): Double = sets
        .filterNot { it.isWarmup }
        .sumOf { MuscleLoadCalculator.setVolumeKg(it.weightKg, it.reps, bodyweightKg) }

    fun setsFor(exerciseId: String): List<SetLog> =
        sets.filter { it.exerciseId == exerciseId }.sortedBy { it.setNumber }

    fun hasLifts(): Boolean = exercises.isNotEmpty() || sets.isNotEmpty()

    /**
     * Resume must never treat a stale selected id as an empty workout.
     * Prefer the last selected lift when it is still in the session, otherwise
     * the first lift that already has sets, otherwise the first lift.
     */
    fun resolveSelectedExerciseId(preferredId: String?): String? {
        val exerciseIds = exercises.map { it.exercise.id }
        if (preferredId != null && preferredId in exerciseIds) return preferredId
        val withSets = exercises.firstOrNull { item ->
            sets.any { it.exerciseId == item.exercise.id }
        }?.exercise?.id
        if (withSets != null) return withSets
        exerciseIds.firstOrNull()?.let { return it }
        return sets.maxByOrNull { it.completedAt }?.exerciseId
    }
}

/**
 * A suggestion for the next time this exercise is trained.
 *
 * [lastWeightKg] and [lastReps] are the TOP set of the last finished session (see
 * [ProgressionBasis]), not the last set logged — the UI shows them as the basis for the
 * suggestion, so they must be the numbers the decision was actually made on.
 */
data class ProgressionHint(
    val exerciseId: String,
    val exerciseName: String,
    val lastWeightKg: Double,
    val lastReps: Int,
    val targetReps: Int,
    val suggestedWeightKg: Double,
    val action: ProgressionAction,
    /**
     * True when the suggestion was downgraded to HOLD because the last two top sets were at
     * RPE 9 or above. Carried so the in-workout strip can say WHY it is not adding weight —
     * an unexplained hold reads as the app having forgotten how to count.
     */
    val rpeHold: Boolean = false,
    /**
     * How this lift is loaded, so the copy can match it.
     *
     * A domain field, not a column — the repository knows the exercise when it builds the hint.
     * It is here because the coach and the in-workout strip both have to be able to say "add a
     * rep" instead of "+2.5 kg" for a push-up, and neither of them has the exercise to hand.
     */
    val loadType: LoadType? = null,
)

enum class ProgressionAction {
    INCREASE,
    HOLD,
    DECREASE,
}
