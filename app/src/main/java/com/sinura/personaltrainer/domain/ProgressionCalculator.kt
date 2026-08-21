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
    fun suggestWeightKg(
        lastWeightKg: Double,
        lastWorkingReps: Int,
        targetReps: Int,
        stepKg: Double?,
    ): Double {
        // No step means nothing to add. Holding the weight is the honest suggestion; the
        // ACTION still reads INCREASE, and the caller renders that as "add a rep".
        if (stepKg == null) return lastWeightKg.coerceAtLeast(0.0)
        val shortfall = targetReps - lastWorkingReps
        val delta = when {
            shortfall <= 0 -> stepKg
            shortfall <= 2 -> 0.0
            else -> -stepKg
        }
        return ((lastWeightKg + delta).coerceAtLeast(0.0))
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
            suggestedWeightKg = suggestWeightKg(lastWeightKg, lastWorkingReps, targetReps, stepKg),
            action = action(lastWorkingReps, targetReps),
            loadType = loadType,
        )
    }
}
