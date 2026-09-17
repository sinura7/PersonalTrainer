package com.sinura.personaltrainer.domain

/** Entry precision is distinct from rounded history/volume presentation. */
object WorkoutWeightCopy {
    fun number(weightKg: Double, unit: WeightUnit): String = when (unit) {
        // Imported or restored drafts may have finer precision than the keypad's
        // normalization. Display it without silently describing a different load.
        WeightUnit.KG -> WeightConverter.sanitizeKg(weightKg).toString().removeSuffix(".0")
        WeightUnit.LBS -> WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(weightKg, unit))
    }

    fun label(weightKg: Double, unit: WeightUnit): String = "${number(weightKg, unit)} ${unit.suffix}"
}
