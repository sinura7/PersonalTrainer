package com.sinura.personaltrainer.domain

/**
 * Turns one reference set into a suggestion for next time.
 *
 * The reference set is always the TOP set of the last finished session, chosen by
 * [ProgressionBasis] — never the last set logged. Everything here assumes that: feed it a
 * back-off set and it will happily suggest progressing from the lighter weight.
 */
object ProgressionCalculator {
    /**
     * The step in kilograms, or null for a lift with no weight to add.
     *
     * Passed in rather than held as a constant: the right increment depends on the lift's load
     * type and on the unit the lifter reads, and neither is knowable from a weight and a rep
     * count. [IncrementTable] is the single source; this takes its answer.
     */
    /**
     * @param weightMeaning what the stored weight IS for this lift. Required, not defaulted,
     * because getting it wrong inverts the whole suggestion — see below.
     */
    fun suggestWeightKg(
        lastWeightKg: Double,
        lastWorkingReps: Int,
        targetReps: Int,
        stepKg: Double?,
        weightMeaning: WeightMeaning,
    ): Double {
        // No step means nothing to add. Holding the weight is the honest suggestion; the
        // ACTION still reads INCREASE, and the caller renders that as "add a rep".
        if (stepKg == null) return lastWeightKg.coerceAtLeast(0.0)
        val shortfall = targetReps - lastWorkingReps
        // How much HARDER the next session should be. A step on a bar and a step on an
        // assistance stack are the same intent expressed by opposite numbers.
        val harder = when {
            shortfall <= 0 -> stepKg
            shortfall <= 2 -> 0.0
            else -> -stepKg
        }
        // Assistance is weight taken OFF the lifter, so less of it is the harder set. Without
        // this the machine rewarded hitting your target reps by offering more help — and
        // answered three missed reps by taking help away, making a lift you were already
        // failing harder still. Both directions were exactly backwards.
        val delta = if (weightMeaning == WeightMeaning.ASSISTANCE) -harder else harder
        // Floored at zero, which for an assisted lift is the point of the machine: no
        // assistance left is the first unassisted rep.
        //
        // Quantised to the same tenth-of-a-kilogram grid typed and stepped input lands
        // on (WeightConverter.toKg): the raw lbs step (5 lbs = 2.2679618… kg) otherwise
        // stores a kg no typed "110 lbs" can ever equal again — the same displayed
        // weight splits into distinct stored values, announcing a false "Heaviest ever"
        // for repeating a weight and never accumulating a reps-at-weight record.
        return WeightConverter.toKg((lastWeightKg + delta).coerceAtLeast(0.0), WeightUnit.KG)
    }

    fun action(
        lastWorkingReps: Int,
        targetReps: Int,
    ): ProgressionAction {
        val shortfall = targetReps - lastWorkingReps
        return when {
            shortfall <= 0 -> ProgressionAction.INCREASE
            shortfall <= 2 -> ProgressionAction.HOLD
            else -> ProgressionAction.DECREASE
        }
    }

    fun hint(
        exerciseId: String,
        exerciseName: String,
        lastWeightKg: Double,
        lastWorkingReps: Int,
        targetReps: Int,
        stepKg: Double?,
        loadType: LoadType?,
    ): ProgressionHint {
        return ProgressionHint(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            lastWeightKg = lastWeightKg,
            lastReps = lastWorkingReps,
            targetReps = targetReps,
            suggestedWeightKg = suggestWeightKg(
                lastWeightKg = lastWeightKg,
                lastWorkingReps = lastWorkingReps,
                targetReps = targetReps,
                stepKg = stepKg,
                weightMeaning = LoadClass.of(loadType).weightMeaning,
            ),
            action = action(lastWorkingReps, targetReps),
            loadType = loadType,
        )
    }
}
