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
     * The step **in the unit the lifter reads**, or null for a lift with no weight to add.
     *
     * Passed in rather than held as a constant: the right increment depends on the lift's load
     * type and on the unit the lifter reads, and neither is knowable from a weight and a rep
     * count. [IncrementTable] is the single source; this takes its answer.
     */
    /**
     * @param weightMeaning what the stored weight IS for this lift. Required, not defaulted,
     * because getting it wrong inverts the whole suggestion — see below.
     * @param unit the unit the lifter reads. The arithmetic happens in it — see below.
     */
    fun suggestWeightKg(
        lastWeightKg: Double,
        lastWorkingReps: Int,
        targetReps: Int,
        displayStep: Double?,
        weightMeaning: WeightMeaning,
        unit: WeightUnit,
    ): Double {
        // No step means nothing to add. Holding the weight is the honest suggestion; the
        // ACTION still reads INCREASE, and the caller renders that as "add a rep".
        if (displayStep == null) return lastWeightKg.coerceAtLeast(0.0)
        val shortfall = targetReps - lastWorkingReps
        // A hold holds EXACTLY. Re-deriving it through the display grid would move a pound
        // user's weight by the rounding alone, which is a suggestion nobody asked for.
        if (shortfall in 1..2) return lastWeightKg.coerceAtLeast(0.0)
        // How much HARDER the next session should be, in display units. A step on a bar and a
        // step on an assistance stack are the same intent expressed by opposite numbers.
        val harder = if (shortfall <= 0) displayStep else -displayStep
        // Assistance is weight taken OFF the lifter, so less of it is the harder set. Without
        // this the machine rewarded hitting your target reps by offering more help — and
        // answered three missed reps by taking help away, making a lift you were already
        // failing harder still. Both directions were exactly backwards.
        val delta = if (weightMeaning == WeightMeaning.ASSISTANCE) -harder else harder
        // The whole point of this function's shape: step in the unit the lifter reads, THEN
        // convert, exactly as WeightConverter.incrementKg does for the +/- plates.
        //
        // Adding the step in kilograms instead — 5 lbs as 2.2679618… kg — and re-quantising
        // to the tenth-of-a-kilogram grid rounds UP every single time, so the real step was
        // 2.3 kg = 5.07 lbs and the error compounded: 100 lbs became 115.5 by the third
        // session and 151 by the tenth. It also split records, because a suggestion the
        // lifter accepted stored a different kilogram from the same weight typed by hand.
        //
        // Floored at zero, which for an assisted lift is the point of the machine: no
        // assistance left is the first unassisted rep.
        val nextDisplay = (WeightConverter.toDisplayValue(lastWeightKg, unit) + delta)
            .coerceAtLeast(0.0)
        return WeightConverter.toKg(nextDisplay, unit)
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
        displayStep: Double?,
        loadType: LoadType?,
        unit: WeightUnit,
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
                displayStep = displayStep,
                weightMeaning = LoadClass.of(loadType).weightMeaning,
                unit = unit,
            ),
            action = action(lastWorkingReps, targetReps),
            loadType = loadType,
        )
    }
}
