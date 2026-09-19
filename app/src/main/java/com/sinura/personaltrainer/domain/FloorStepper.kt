package com.sinura.personaltrainer.domain

import kotlin.math.abs

/**
 * Gym-floor weight, reps, and hold-draft plates: one equipment-aware
 * step, not a live wheel. Extra / paste / Home keep their own wells.
 *
 * Hold-repeat timing lives here so the Compose plate and the tests
 * cannot drift: 450 ms before the first extra change, then at most
 * five a second. The 60 ms fast repeat is gone.
 */
object StepperRepeat {
    const val HOLD_BEFORE_REPEAT_MS = 450L
    const val REPEAT_MS = 200L
    const val MAX_CHANGES_PER_SECOND = 5
}

object FloorStepper {
    const val REPS_STEP = 1

    fun nextWeightKg(
        currentKg: Double,
        unit: WeightUnit,
        direction: Int,
        loadType: LoadType,
        equipment: EquipmentType? = null,
    ): Double = IncrementTable.nextKg(currentKg, unit, direction, loadType, equipment)

    fun nextReps(current: Int, direction: Int): Int =
        (current + direction * REPS_STEP).coerceIn(1, NumericEntry.MAX_REPS)

    fun nextHoldSeconds(current: Int, direction: Int): Int =
        HoldWork.nextSeconds(current, direction)
}

enum class WeightDraftSource(val label: String, val chipName: String) {
    PLAN("Plan", "Plan"),
    LAST_TIME("Last time", "Last"),
    SUGGESTED("Suggested", "Suggested"),
}

data class WeightContextAction(
    val source: WeightDraftSource,
    val weightKg: Double,
) {
    fun chipLabel(unit: WeightUnit): String {
        val shown = WeightConverter.formatDisplayNumber(
            WeightConverter.toDisplayValue(weightKg, unit),
        )
        return "${source.chipName} $shown"
    }
}

/**
 * Plan / Last / Suggested on the weight field. Information, not an
 * arbitrary chip row. Suggested is a source label in this packet;
 * Use on the recommendation strip stays Packet F.
 */
object FloorWeightPresets {
    fun source(
        currentKg: Double,
        plannedKg: Double?,
        lastKg: Double?,
        suggestedKg: Double?,
    ): WeightDraftSource? {
        if (sameKg(currentKg, suggestedKg)) return WeightDraftSource.SUGGESTED
        if (sameKg(currentKg, plannedKg)) return WeightDraftSource.PLAN
        if (sameKg(currentKg, lastKg)) return WeightDraftSource.LAST_TIME
        return null
    }

    fun contextActions(
        plannedKg: Double?,
        lastKg: Double?,
        suggestedKg: Double? = null,
        includeSuggested: Boolean = false,
    ): List<WeightContextAction> {
        val out = ArrayList<WeightContextAction>(3)
        fun add(source: WeightDraftSource, kg: Double?) {
            val value = kg?.takeIf { it.isFinite() && it > 0.0 } ?: return
            if (out.any { sameKg(it.weightKg, value) }) return
            out += WeightContextAction(source, value)
        }
        add(WeightDraftSource.PLAN, plannedKg)
        add(WeightDraftSource.LAST_TIME, lastKg)
        if (includeSuggested) add(WeightDraftSource.SUGGESTED, suggestedKg)
        return out
    }

    /**
     * The one-tap fills worth offering under the weight numeral: the plan and last time,
     * minus whichever the entry already holds. Empty on the common path, where the draft
     * is the plan, so the floor stays quiet.
     */
    fun quickFills(currentKg: Double, plannedKg: Double?, lastKg: Double?): List<WeightContextAction> =
        contextActions(plannedKg = plannedKg, lastKg = lastKg).filterNot { sameKg(currentKg, it.weightKg) }

    private fun sameKg(currentKg: Double, otherKg: Double?): Boolean {
        if (otherKg == null || !currentKg.isFinite() || !otherKg.isFinite()) return false
        return abs(currentKg - otherKg) < 0.05
    }
}
