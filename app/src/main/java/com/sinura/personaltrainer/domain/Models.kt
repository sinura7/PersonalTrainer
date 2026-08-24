package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime

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
     * How a lift in this session is measured.
     *
     * Read from the session's own copy of the exercise, not from the library. A lift deleted or
     * edited after the session was logged must not retroactively change what its history means
     * — a pull-up reclassified as loaded would turn a rep count into kilograms across months of
     * past workouts. Unknown lifts fall back to loaded; see [LoadClass.of].
     */
    fun loadClassOf(exerciseId: String): LoadClass =
        LoadClass.of(exercises.firstOrNull { it.exercise.id == exerciseId }?.exercise?.loadType)

    /**
     * What this session was worth, in both of the units training is actually measured in.
     *
     * Replaces a single `workingVolumeKg()`, which had to answer "how many kilograms" even for
     * sessions that contained none. It did so by pricing every bodyweight rep at a flat 40 kg,
     * so a set of ten push-ups reported four hundred kilograms lifted. The number agreed with
     * the body map, which is why it survived — both were quoting the same invention.
     */
    fun work(): SetWork {
        // The class map is built once, not looked up per set. `loadClassOf` scans the exercise
        // list, so calling it inside the loop made this O(sets × lifts) — and it is called from
        // the active workout header, which recomposes every second as the elapsed timer ticks.
        val classes = exercises.associate { it.exercise.id to LoadClass.of(it.exercise.loadType) }
        var volumeKg = 0.0
        var bodyweightReps = 0
        sets.forEach { set ->
            if (set.isWarmup) return@forEach
            val work = SetWork.of(
                weightKg = set.weightKg,
                reps = set.reps,
                loadClass = classes[set.exerciseId] ?: LoadClass.LOADED,
            )
            volumeKg += work.volumeKg
            bodyweightReps += work.bodyweightReps
        }
        return SetWork(volumeKg = volumeKg, bodyweightReps = bodyweightReps)
    }

    /** Just the kilograms — the bar and the vest, never the body. */
    fun workingVolumeKg(): Double = work().volumeKg

    /**
     * When this session happened, in milliseconds.
     *
     * `date` is the ordering key history uses everywhere, with two fallbacks for rows old
     * enough to have been written before it was. Promoted out of ExerciseHistory because the
     * block review needs the same rule, and two copies of "which of these three timestamps is
     * the real one" is how two surfaces end up disagreeing about what week a workout was in.
     */
    fun performedAtMs(): Long =
        listOf(date, finishedAt ?: 0L, startedAt).firstOrNull { it > 0L } ?: 0L

    fun performedEpochDay(
        time: TimePort = JvmTime,
        zoneId: String = time.defaultZoneId(),
    ): Long = time.civilDate(performedAtMs(), zoneId).epochDay

    /**
     * Working sets, warm-ups excluded.
     *
     * The one measure every lift shares. Anything that has to compare a squat day with a
     * calisthenics day — the calendar's intensity shading, the deload signal's three-week
     * rise — counts these rather than kilograms.
     */
    fun workingSetCount(): Int = sets.count { !it.isWarmup }

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
     * True when the suggestion was held because this week is marked lighter.
     * The strip names that reason first — an unexplained hold during a deload
     * reads as the app having forgotten how to count.
     */
    val lighterHold: Boolean = false,
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
