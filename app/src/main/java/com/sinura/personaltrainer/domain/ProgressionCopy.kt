package com.sinura.personaltrainer.domain

/**
 * What the app says about a progression hint.
 *
 * Pure, and in the domain, because the same sentence has to hold in two places that cannot see
 * each other: the strip above the set entry during a workout, and the coach card on Home. They
 * used to compute it separately from the same constant, which is how the coach ended up saying
 * "+5.5 lbs" while the stepper an inch below it moved in fives.
 *
 * Every branch reads the step from [IncrementTable], so a lift with nothing to add says so
 * rather than naming a weight that cannot be loaded.
 */
object ProgressionCopy {
    /** The in-workout strip: what to do with this lift, right now. */
    fun stripReason(hint: ProgressionHint, unit: WeightUnit): String {
        val step = IncrementTable.stepLabel(hint.loadType ?: LoadType.EXTERNAL, unit)
        return when {
            // A hold with no explanation reads as the app having lost count. If RPE is why, say so.
            hint.rpeHold -> "Top set at RPE 9+. Hold ${hint.lastWeightKg.toWeightLabel(unit)}."
            // Nothing to add: the honest instruction is a rep, and it is the SAME sentence the
            // coach uses, so the two surfaces cannot contradict each other on a push-up.
            step == null && hint.action == ProgressionAction.INCREASE ->
                IncrementTable.REP_PROGRESSION_COPY
            step == null -> "Keep ${hint.lastReps} reps."
            hint.action == ProgressionAction.INCREASE -> "Hit target. Add $step."
            hint.action == ProgressionAction.HOLD ->
                "Close. Keep ${hint.lastWeightKg.toWeightLabel(unit)}."
            else -> "Missed target. Drop $step."
        }
    }

    /** The coach card: why this lift is being named, read away from the gym floor. */
    fun coachReason(hint: ProgressionHint, unit: WeightUnit): String {
        val topSet = "Top set ${hint.lastWeightKg.toWeightLabel(unit)}×${hint.lastReps} hit target."
        val step = IncrementTable.stepLabel(hint.loadType ?: LoadType.EXTERNAL, unit)
        return if (step == null) {
            "$topSet ${IncrementTable.REP_PROGRESSION_COPY}"
        } else {
            "$topSet Next session add $step."
        }
    }
}
