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
        if (workingWeightKg <= 0.0) return emptyList()
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
}
