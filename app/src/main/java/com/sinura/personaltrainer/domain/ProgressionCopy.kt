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
        val assisted = hint.isAssisted
        return when {
            // Lighter week is why they opened the session this way. Prefer it over RPE.
            hint.lighterHold -> "Lighter week. Keep ${hint.weightLabel(unit)}."
            // A hold with no explanation reads as the app having lost count. If RPE is why, say so.
            hint.rpeHold -> "Top set at RPE 9+. Hold ${hint.weightLabel(unit)}."
            // Nothing to add: the honest instruction is a rep, and it is the SAME sentence the
            // coach uses, so the two surfaces cannot contradict each other on a push-up.
            step == null && hint.action == ProgressionAction.INCREASE ->
                IncrementTable.REP_PROGRESSION_COPY
            step == null -> "Keep ${hint.lastReps} reps."
            // The verbs invert with the meaning of the number. Telling someone who just hit
            // their target on an assisted machine to "add 2.5 kg" is telling them to make the
            // lift easier as a reward for succeeding at it.
            hint.action == ProgressionAction.INCREASE ->
                if (assisted) "Hit target. Drop $step of assist." else "Hit target. Add $step."
            hint.action == ProgressionAction.HOLD -> "Close. Keep ${hint.weightLabel(unit)}."
            else ->
                if (assisted) "Missed target. Add $step of assist." else "Missed target. Drop $step."
        }
    }

    /** The coach card: why this lift is being named, read away from the gym floor. */
    fun coachReason(hint: ProgressionHint, unit: WeightUnit): String {
        val topSet = "Top set ${hint.weightLabel(unit)}×${hint.lastReps} hit target."
        val step = IncrementTable.stepLabel(hint.loadType ?: LoadType.EXTERNAL, unit)
        return when {
            step == null -> "$topSet ${IncrementTable.REP_PROGRESSION_COPY}"
            hint.isAssisted -> "$topSet Next session drop $step of assist."
            else -> "$topSet Next session add $step."
        }
    }

    private val ProgressionHint.isAssisted: Boolean
        get() = LoadClass.of(loadType).weightMeaning == WeightMeaning.ASSISTANCE

    /**
     * The last top set's weight, said in the terms the lift is logged in.
     *
     * "Keep 20 kg" on an assisted machine reads as if twenty kilograms were lifted. They were
     * subtracted.
     */
    private fun ProgressionHint.weightLabel(unit: WeightUnit): String {
        val meaning = LoadClass.of(loadType).weightMeaning
        val weight = lastWeightKg.toWeightLabel(unit)
        return when (meaning) {
            WeightMeaning.ASSISTANCE -> "$weight of assist"
            WeightMeaning.ADDED -> "+$weight"
            WeightMeaning.LIFTED, WeightMeaning.NONE -> weight
        }
    }
}
