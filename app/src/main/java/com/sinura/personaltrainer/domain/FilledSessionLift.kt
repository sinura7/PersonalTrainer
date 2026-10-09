package com.sinura.personaltrainer.domain

/**
 * One exact exercise on a finished session. Each original prescription remains
 * separate; saved sets belong to the exercise, not to any one prescription.
 */
data class FilledSessionLift(
    val number: Int,
    val exercise: Exercise,
    val prescriptions: List<FilledSessionPrescription>,
    val sets: List<SetLog>,
) {
    val workingLogged: Int get() = sets.count { !it.isWarmup }

    /**
     * Whether this card should print the Work / Rest / Load row the program
     * card uses. Orphaned set-only history has nothing to print there.
     */
    val hasPrescription: Boolean
        get() = prescriptions.any { it.hasTargets }
}

/** The original row and its position before exercise grouping, including repeated rows. */
data class FilledSessionPrescription(
    val originalPosition: Int,
    val row: SessionExercise,
) {
    val hasTargets: Boolean
        get() = row.targetSets > 0 ||
            row.restSeconds > 0 ||
            row.targetSeconds != null ||
            (row.targetWeightKg != null && row.targetWeightKg > 0.0)
}
