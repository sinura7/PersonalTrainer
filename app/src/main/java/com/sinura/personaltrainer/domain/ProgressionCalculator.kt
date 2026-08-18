package com.sinura.personaltrainer.domain

object ProgressionCalculator {
    const val INCREMENT_KG = 2.5

    fun suggestWeightKg(
        lastWeightKg: Double,
        lastWorkingReps: Int,
        targetReps: Int,
    ): Double {
        val shortfall = targetReps - lastWorkingReps
        val delta = when {
            shortfall <= 0 -> INCREMENT_KG
            shortfall <= 2 -> 0.0
            else -> -INCREMENT_KG
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
    ): ProgressionHint {
        return ProgressionHint(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            lastWeightKg = lastWeightKg,
            lastReps = lastWorkingReps,
            targetReps = targetReps,
            suggestedWeightKg = suggestWeightKg(lastWeightKg, lastWorkingReps, targetReps),
            action = action(lastWorkingReps, targetReps),
        )
    }
}
