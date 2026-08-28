package com.sinura.personaltrainer.domain

/**
 * What the rest floor page says about the lift you just logged.
 *
 * Packet 1 is last set plus the session-grain [ProgressionHint], not in-set micro-rec.
 * Copy lives in the domain so the ViewModel and the tests quote the same sentences.
 */
data class RestFloorContext(
    val exerciseName: String?,
    val lastSetLine: String?,
    val sessionTargetLine: String?,
)

object RestFloorCopy {
    fun context(
        session: WorkoutSession?,
        selectedExerciseId: String?,
        hint: ProgressionHint?,
        unit: WeightUnit,
    ): RestFloorContext {
        if (session == null) {
            return RestFloorContext(
                exerciseName = null,
                lastSetLine = null,
                sessionTargetLine = null,
            )
        }
        val exerciseId = session.resolveSelectedExerciseId(selectedExerciseId)
        val exercise = session.exercises.firstOrNull { it.exercise.id == exerciseId }?.exercise
        val last = exerciseId?.let { id ->
            session.setsFor(id).maxByOrNull { it.completedAt }
        } ?: session.sets.maxByOrNull { it.completedAt }
        val loadClass = last?.let { session.loadClassOf(it.exerciseId) } ?: LoadClass.LOADED
        return RestFloorContext(
            exerciseName = exercise?.name ?: last?.exerciseName,
            lastSetLine = last?.let { lastSetLine(it.weightKg, it.reps, loadClass, unit) },
            sessionTargetLine = hint?.let { sessionTargetLine(it, unit) },
        )
    }

    fun lastSetLine(
        weightKg: Double,
        reps: Int,
        loadClass: LoadClass,
        unit: WeightUnit,
    ): String = "Last set · ${SetCopy.setLine(weightKg, reps, loadClass, unit)}"

    fun sessionTargetLine(hint: ProgressionHint, unit: WeightUnit): String =
        ProgressionCopy.stripReason(hint, unit)
}
