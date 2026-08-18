package com.sinura.personaltrainer.domain

import kotlin.math.abs
import kotlin.math.round

enum class WeightUnit(
    val storageKey: String,
    val suffix: String,
    val displayName: String,
    val step: Double,
) {
    KG(
        storageKey = "kg",
        suffix = "kg",
        displayName = "Kilograms (kg)",
        step = 2.5,
    ),
    LBS(
        storageKey = "lbs",
        suffix = "lbs",
        displayName = "Pounds (lbs)",
        step = 5.0,
    );

    val stepLabel: String
        get() = WeightConverter.formatDisplayNumber(step)

    companion object {
        fun fromStorage(value: String?): WeightUnit =
            entries.firstOrNull { it.storageKey.equals(value, ignoreCase = true) } ?: KG
    }
}

object WeightConverter {
    const val LBS_PER_KG = 2.20462

    fun sanitizeKg(kg: Double): Double = if (kg.isFinite()) kg.coerceAtLeast(0.0) else 0.0

    fun kgToLbs(kg: Double): Double = sanitizeKg(kg) * LBS_PER_KG

    fun lbsToKg(lbs: Double): Double = sanitizeKg(lbs) / LBS_PER_KG

    fun toDisplayValue(kg: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> roundToTenth(sanitizeKg(kg))
        WeightUnit.LBS -> roundToHalf(kgToLbs(kg))
    }

    fun toKg(displayValue: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> roundToTenth(sanitizeKg(displayValue))
        WeightUnit.LBS -> roundToTenth(lbsToKg(displayValue))
    }

    fun incrementKg(currentKg: Double, unit: WeightUnit, direction: Int): Double {
        val nextDisplay = (toDisplayValue(currentKg, unit) + direction * unit.step).coerceAtLeast(0.0)
        return toKg(nextDisplay, unit)
    }

    fun formatLabel(kg: Double, unit: WeightUnit): String =
        "${formatDisplayNumber(toDisplayValue(kg, unit))} ${unit.suffix}"

    fun formatDisplayNumber(value: Double): String {
        val rounded = roundToTenth(value)
        return if (abs(rounded % 1.0) < 0.001) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
    }

    fun parseDisplayToKg(input: String, unit: WeightUnit, originalKg: Double?): Double? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val parsed = trimmed.toDoubleOrNull() ?: return originalKg
        if (originalKg != null) {
            val originalDisplay = formatDisplayNumber(toDisplayValue(originalKg, unit))
            val typedDisplay = formatDisplayNumber(toDisplayValue(toKg(parsed, unit), unit))
            if (originalDisplay == typedDisplay) return originalKg
        }
        return toKg(parsed, unit)
    }

    private fun roundToTenth(value: Double): Double = round(value * 10.0) / 10.0

    private fun roundToHalf(value: Double): Double = round(value * 2.0) / 2.0
}

fun Double.toWeightLabel(unit: WeightUnit): String = WeightConverter.formatLabel(this, unit)

fun Double.toKgLabel(): String = toWeightLabel(WeightUnit.KG)

fun Double.toKgNumber(): String = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(this, WeightUnit.KG))
