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
    val afterWarmup: Boolean = false,
    val prescribedRestLine: String? = null,
)

object RestFloorCopy {
    fun context(
        session: WorkoutSession?,
        selectedExerciseId: String?,
        unit: WeightUnit,
        nextLine: String? = null,
        prescribedSeconds: Int? = null,
    ): RestFloorContext {
        if (session == null) {
            return RestFloorContext(
                exerciseName = null,
                lastSetLine = null,
                sessionTargetLine = null,
                afterWarmup = false,
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
            lastSetLine = last?.let {
                lastSetLine(
                    weightKg = it.weightKg,
                    reps = it.reps,
                    loadClass = loadClass,
                    unit = unit,
                    durationSeconds = it.durationSeconds,
                )
            },
            sessionTargetLine = nextLine,
            afterWarmup = last?.isWarmup == true,
            prescribedRestLine = prescribedSeconds?.let { prescribedLine(it) },
        )
    }

    fun prescribedLine(seconds: Int): String = RestIdleCopy.planned(RestTimer.formatClock(seconds))

    /**
     * A hold reads as its time, "Last set · 30s". Without the duration a plank read "Last set ·
     * 0 reps", since a hold is logged with none (audit DM-1).
     */
    fun lastSetLine(
        weightKg: Double,
        reps: Int,
        loadClass: LoadClass,
        unit: WeightUnit,
        durationSeconds: Int? = null,
    ): String = "Last set · ${SetCopy.setLine(
        weightKg = weightKg,
        reps = reps,
        loadClass = loadClass,
        unit = unit,
        durationSeconds = durationSeconds,
    )}"

    fun sessionTargetLine(hint: ProgressionHint, unit: WeightUnit): String =
        ProgressionCopy.stripReason(hint, unit)
}
