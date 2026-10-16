package com.sinura.personaltrainer.domain

import kotlin.math.floor

/**
 * In-session load suggestions after a weight step, from Epley with reps in reserve (RIR).
 *
 * e1RM = w × (1 + (reps + RIR) / 30), RIR = 10 − RPE. Reps at a new weight reserve
 * [targetRir] below capacity. Reps never rise above the last set; a heavier load always
 * lowers reps when the formula would not. A full plate step that would drop more than
 * two reps holds the load instead (light dumbbell jumps).
 */
object RpeLoadModel {
    const val MAX_REP_DROP_FOR_WEIGHT_STEP = 2

    const val MATH_NOTE_FORMULA = "formula"
    const val MATH_NOTE_CAP = "cap"
    const val MATH_NOTE_HOLD_LOAD = "hold_load"

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

    data class WeightIncreaseSuggestion(
        val nextWeightKg: Double,
        val nextReps: Int,
        val mathNote: String,
        val formulaReps: Int?,
    )

    fun suggestAfterWeightIncrease(
        lastWeightKg: Double,
        lastReps: Int,
        lastRpe: Int,
        proposedNextWeightKg: Double,
        targetRir: Int,
    ): WeightIncreaseSuggestion {
        if (proposedNextWeightKg <= lastWeightKg) {
            return WeightIncreaseSuggestion(
                nextWeightKg = lastWeightKg,
                nextReps = lastReps.coerceAtLeast(1),
                mathNote = MATH_NOTE_FORMULA,
                formulaReps = lastReps,
            )
        }
        val e1rm = estimatedMaxKg(lastWeightKg, lastReps, lastRpe)
            ?: return WeightIncreaseSuggestion(
                nextWeightKg = proposedNextWeightKg,
                nextReps = lastReps.coerceAtLeast(1),
                mathNote = MATH_NOTE_FORMULA,
                formulaReps = null,
            )
        val formulaReps = repsAtWeightKg(proposedNextWeightKg, e1rm, targetRir).coerceAtLeast(1)
        var nextReps = formulaReps.coerceAtMost(lastReps)
        var mathNote = MATH_NOTE_FORMULA
        if (nextReps >= lastReps) {
            nextReps = (lastReps - 1).coerceAtLeast(1)
            mathNote = MATH_NOTE_CAP
        }
        val repDrop = lastReps - nextReps
        if (repDrop > MAX_REP_DROP_FOR_WEIGHT_STEP) {
            return WeightIncreaseSuggestion(
                nextWeightKg = lastWeightKg,
                nextReps = lastReps.coerceAtLeast(1),
                mathNote = MATH_NOTE_HOLD_LOAD,
                formulaReps = formulaReps,
            )
        }
        return WeightIncreaseSuggestion(
            nextWeightKg = proposedNextWeightKg,
            nextReps = nextReps,
            mathNote = mathNote,
            formulaReps = formulaReps,
        )
    }

    fun repsAfterWeightIncrease(
        lastWeightKg: Double,
        lastReps: Int,
        lastRpe: Int,
        nextWeightKg: Double,
        targetRir: Int,
    ): Int = suggestAfterWeightIncrease(
        lastWeightKg = lastWeightKg,
        lastReps = lastReps,
        lastRpe = lastRpe,
        proposedNextWeightKg = nextWeightKg,
        targetRir = targetRir,
    ).nextReps

    fun oneLineMath(
        lastWeightKg: Double,
        lastReps: Int,
        lastRpe: Int,
        nextWeightKg: Double,
        nextReps: Int,
        unit: WeightUnit,
        mathNote: String? = null,
        formulaReps: Int? = null,
    ): String {
        val e1rm = estimatedMaxKg(lastWeightKg, lastReps, lastRpe) ?: return ""
        val lastDisplay = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(lastWeightKg, unit))
        val nextDisplay = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(nextWeightKg, unit))
        val e1rmDisplay = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(e1rm, unit))
        val base = "From $lastDisplay×$lastReps @ RPE $lastRpe → e1RM ≈ $e1rmDisplay → $nextDisplay×$nextReps"
        return when (mathNote) {
            MATH_NOTE_CAP ->
                "$base · Reps capped so a heavier load always lowers reps"
            MATH_NOTE_HOLD_LOAD -> buildString {
                append(base)
                append(" · Load held; a full step would drop more than $MAX_REP_DROP_FOR_WEIGHT_STEP reps")
                formulaReps?.let { raw ->
                    if (raw != nextReps) {
                        append(" (formula had $raw)")
                    }
                }
            }
            else -> base
        }
    }
}

fun CoachPreferences.targetRepsInReserve(): Int = when (goal) {
    TrainingGoal.STRENGTH -> 2
    TrainingGoal.HYPERTROPHY -> 1
    else -> 1
}
