package com.sinura.personaltrainer.domain

/**
 * One lift on a finished session, in the same shape as a program card: identity,
 * the prescription it was started with, and the sets that filled it in.
 */
data class FilledSessionLift(
    val number: Int,
    val exercise: Exercise,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val restSeconds: Int,
    val sets: List<SetLog>,
) {
    val workingLogged: Int get() = sets.count { !it.isWarmup }

    /**
     * Whether this card should print the Work / Rest / Load row the program
     * card uses. Orphaned set-only history has nothing to print there.
     */
    val hasPrescription: Boolean
        get() = targetSets > 0 ||
            restSeconds > 0 ||
            (targetWeightKg != null && targetWeightKg > 0.0)
}
