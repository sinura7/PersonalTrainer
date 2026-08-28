package com.sinura.personaltrainer.domain

/**
 * What the rest floor page says about the lift you just logged.
 *
 * Last set plus the in-set next line ([SetMicroRecCopy.line]). Session-grain
 * [ProgressionHint] stays on the log strip, not here.
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
        unit: WeightUnit,
        nextLine: String? = null,
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
            sessionTargetLine = nextLine,
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
