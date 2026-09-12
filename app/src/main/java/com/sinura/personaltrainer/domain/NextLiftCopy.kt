package com.sinura.personaltrainer.domain

/**
 * The pinned next-lift box on the live log: picture, name, planned work,
 * and rest. Selected identity used to scroll under the dock (phone check
 * 04). This copy is what that box speaks.
 */
object NextLiftCopy {
    const val KICKER = "This lift"

    fun plannedWork(
        workingLogged: Int,
        targetSets: Int,
        targetReps: Int,
        targetWeightLabel: String?,
    ): String = WorkoutCopy.setProgress(
        workingLogged = workingLogged,
        targetSets = targetSets,
        targetReps = targetReps,
        targetWeightLabel = targetWeightLabel,
    )

    fun spoken(
        name: String,
        plannedWork: String,
        restClock: String?,
        restLive: Boolean,
    ): String = buildString {
        append(name)
        append(". ")
        append(plannedWork)
        if (!restClock.isNullOrBlank()) {
            append(". ")
            append(if (restLive) LiftChipCopy.REST_REMAINING else LiftChipCopy.REST)
            append(" ")
            append(restClock)
        }
    }
}
