package com.sinura.personaltrainer.domain

import kotlin.math.floor

/**
 * In-session load suggestions after a weight step, from Epley with reps in reserve (RIR).
 *
 * e1RM = w × (1 + (reps + RIR) / 30), RIR = 10 − RPE. Reps at a new weight reserve
 * [targetRir] below capacity. Reps never rise above the last set; a heavier load always
 * lowers reps when the formula would not.
 */
object RpeLoadModel {
    fun repsInReserve(rpe: Int): Int = (10 - rpe).coerceAtLeast(0)

    fun estimatedMaxKg(weightKg: Double, reps: Int, rpe: Int): Double? {
        if (weightKg <= 0.0 || reps <= 0 || rpe !in 1..10) return null
        val rir = repsInReserve(rpe)
        return weightKg * (1.0 + (reps + rir) / 30.0)
    }

    fun repsAtWeightKg(
        weightKg: Double,
        estimatedMaxKg: Double,
        targetRir: Int,
    ): Int {
        if (weightKg <= 0.0) return 1
        val capacity = 30.0 * (estimatedMaxKg / weightKg - 1.0)
        return floor(capacity).toInt().coerceAtLeast(0) - targetRir.coerceAtLeast(0)
    }

    /**
     * Reps for [nextWeightKg] after a load increase from the last working set.
     */
    fun repsAfterWeightIncrease(
        lastWeightKg: Double,
        lastReps: Int,
        lastRpe: Int,
        nextWeightKg: Double,
        targetRir: Int,
    ): Int {
        if (nextWeightKg <= lastWeightKg) return lastReps.coerceAtLeast(1)
        val e1rm = estimatedMaxKg(lastWeightKg, lastReps, lastRpe) ?: return lastReps.coerceAtLeast(1)
        val computed = repsAtWeightKg(nextWeightKg, e1rm, targetRir)
        val capped = computed.coerceAtMost(lastReps)
        return if (capped >= lastReps) {
            (lastReps - 1).coerceAtLeast(1)
        } else {
            capped.coerceAtLeast(1)
        }
    }

    fun oneLineMath(
        lastWeightKg: Double,
        lastReps: Int,
        lastRpe: Int,
        nextWeightKg: Double,
        nextReps: Int,
        unit: WeightUnit,
    ): String {
        val e1rm = estimatedMaxKg(lastWeightKg, lastReps, lastRpe) ?: return ""
        val lastDisplay = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(lastWeightKg, unit))
        val nextDisplay = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(nextWeightKg, unit))
        val e1rmDisplay = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(e1rm, unit))
        return "From $lastDisplay×$lastReps @ RPE $lastRpe → e1RM ≈ $e1rmDisplay → $nextDisplay×$nextReps"
    }
}

fun CoachPreferences.targetRepsInReserve(): Int = when (goal) {
    TrainingGoal.STRENGTH -> 2
    TrainingGoal.HYPERTROPHY -> 1
    else -> 1
}
