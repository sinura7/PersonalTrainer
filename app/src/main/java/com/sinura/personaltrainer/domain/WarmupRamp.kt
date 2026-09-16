package com.sinura.personaltrainer.domain

import kotlin.math.floor

/**
 * Working-set warm-ups, as a percentage ladder off the working weight.
 *
 * Additive only: these sets are never written onto the plan and they never
 * enter [targetSets] accounting. ADR-020 warm-up *packs* are whole-session
 * mobility blocks and are a different thing.
 *
 * Bodyweight and assisted lifts have no honest bar to ramp from — a push-up
 * does not have a 40% set, and assistance inverted would invent a harder
 * warm-up than the work. Both return empty.
 */
data class WarmupSet(
    val weightKg: Double,
    val percent: Int,
)

object WarmupRamp {
    val PERCENTS: List<Int> = listOf(40, 60, 80)

    fun sets(
        workingWeightKg: Double,
        loadType: LoadType?,
        unit: WeightUnit,
        equipment: EquipmentType? = null,
    ): List<WarmupSet> {
        if (workingWeightKg <= 0.0) {
            // 40/60/80 of 0 is 0. Empty, not a 2.5/5 plate, and never an auto-log.
            return emptyList()
        }
        val resolved = loadType ?: LoadType.EXTERNAL
        if (resolved == LoadType.BODYWEIGHT || resolved == LoadType.ASSISTED) return emptyList()
        val step = IncrementTable.displayStep(resolved, unit, equipment) ?: return emptyList()
        return PERCENTS.mapNotNull { percent ->
            val rawDisplay = WeightConverter.toDisplayValue(
                workingWeightKg * percent / 100.0,
                unit,
            )
            val snapped = floor((rawDisplay / step) + 1e-9) * step
            if (snapped <= 0.0) return@mapNotNull null
            val kg = WeightConverter.toKg(snapped, unit)
            if (kg >= workingWeightKg - 1e-6) return@mapNotNull null
            WarmupSet(weightKg = kg, percent = percent)
        }.distinctBy { it.weightKg }
    }

    /**
     * The bar the percentage chips are measured from.
     *
     * After a warm-up log the wells keep the lighter number. The ramp must
     * still read off the planned working weight, not that leftover 40 kg.
     * Once the lifter has typed a working load at or above the plan, that
     * typed number wins.
     */
    fun workingWeightKg(
        draftKg: Double,
        draftIsWarmup: Boolean,
        workingLogged: Int,
        targetKg: Double?,
        suggestedKg: Double?,
        lastKg: Double?,
        loadType: LoadType? = null,
        equipment: EquipmentType? = null,
        movementKey: String? = null,
    ): Double {
        val planned = sequenceOf(targetKg, suggestedKg, lastKg)
            .mapNotNull { it }
            .firstOrNull { it.isFinite() && it > 0.0 }
            ?: 0.0
        val draft = if (draftKg.isFinite()) draftKg.coerceAtLeast(0.0) else 0.0
        // An empty-hands lunge at 0 is a chosen working weight, not an unfilled well.
        // Falling back to a leftover 5 lb plan would invent 40/60/80 chips of that 5.
        if (
            !draftIsWarmup &&
            draft <= 0.0 &&
            UnloadedLoad.allowsZeroWorkingWeight(loadType, equipment, movementKey)
        ) {
            return 0.0
        }
        if (workingLogged <= 0 && planned > 0.0) {
            if (draftIsWarmup || draft + 1e-6 < planned) return planned
        }
        if (!draftIsWarmup && draft > 0.0) return draft
        return planned
    }

    fun chipLabel(set: WarmupSet, unit: WeightUnit): String =
        "${set.percent}% · ${set.weightKg.toWeightLabel(unit)}"

    /**
     * Index of the next ramp weight that has not been logged as a warm-up.
     * -1 when every rung is used or the ramp is empty.
     */
    fun nextUnusedIndex(ramp: List<WarmupSet>, loggedWarmupKg: List<Double>): Int {
        if (ramp.isEmpty()) return -1
        return ramp.indexOfFirst { step ->
            loggedWarmupKg.none { logged ->
                logged.isFinite() && kotlin.math.abs(logged - step.weightKg) < 1e-6
            }
        }
    }
}
