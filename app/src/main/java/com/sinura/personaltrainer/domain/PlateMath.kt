package com.sinura.personaltrainer.domain

/**
 * How a barbell is loaded, in the unit the lifter thinks in.
 *
 * The stepper already moves in plates ([IncrementTable]). This is the readout:
 * a 225 lb squat is a 45 lb bar and two 45s a side, not an abstract kilogram.
 * Dumbbells, stacks and bodyweight have no bar. They stay quiet.
 */
data class PlateCount(
    val size: Double,
    val count: Int,
)

data class PlateLoad(
    val barDisplay: Double,
    val sides: List<PlateCount>,
    val leftoverDisplay: Double,
    val unit: WeightUnit,
) {
    fun caption(): String {
        val bar = "${WeightConverter.formatDisplayNumber(barDisplay)} ${unit.suffix} bar"
        val plates = sides.joinToString(" + ") { count ->
            "${count.count}×${WeightConverter.formatDisplayNumber(count.size)}"
        }
        val loaded = if (plates.isEmpty()) bar else "$bar + $plates / side"
        return if (leftoverDisplay > LEFTOVER_EPS) {
            "$loaded, and ${WeightConverter.formatDisplayNumber(leftoverDisplay)} leftover"
        } else {
            loaded
        }
    }

    companion object {
        internal const val LEFTOVER_EPS = 0.05
    }
}

object PlateMath {
    const val BAR_KG = 20.0
    const val BAR_LBS = 45.0

    val PLATES_KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    val PLATES_LBS = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

    fun defaultBarDisplay(unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> BAR_KG
        WeightUnit.LBS -> BAR_LBS
    }

    fun load(targetKg: Double, unit: WeightUnit): PlateLoad? {
        val target = WeightConverter.toDisplayValue(targetKg, unit)
        val bar = defaultBarDisplay(unit)
        if (target < bar - PlateLoad.LEFTOVER_EPS) return null

        var remainingPerSide = ((target - bar) / 2.0).coerceAtLeast(0.0)
        val plates = when (unit) {
            WeightUnit.KG -> PLATES_KG
            WeightUnit.LBS -> PLATES_LBS
        }
        val sides = ArrayList<PlateCount>(plates.size)
        for (size in plates) {
            val count = ((remainingPerSide + 1e-9) / size).toInt()
            if (count > 0) {
                sides += PlateCount(size, count)
                remainingPerSide -= count * size
            }
        }
        val leftover = if (remainingPerSide * 2.0 <= PlateLoad.LEFTOVER_EPS) {
            0.0
        } else {
            WeightConverter.toDisplayValue(
                WeightConverter.toKg(remainingPerSide * 2.0, unit),
                unit,
            )
        }
        return PlateLoad(
            barDisplay = bar,
            sides = sides,
            leftoverDisplay = leftover,
            unit = unit,
        )
    }
}
