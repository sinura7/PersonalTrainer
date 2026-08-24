package com.sinura.personaltrainer.domain

import kotlin.math.abs
import kotlin.math.round

enum class WeightUnit(
    val storageKey: String,
    val suffix: String,
    val displayName: String,
) {
    KG(
        storageKey = "kg",
        suffix = "kg",
        displayName = "Kilograms (kg)",
    ),
    LBS(
        storageKey = "lbs",
        suffix = "lbs",
        displayName = "Pounds (lbs)",
    );

    /**
     * The general-purpose stepper increment for any weight field.
     *
     * Read from [IncrementTable] rather than declared here, because it used to be declared in
     * both places and the two drifted: the coach quoted the calculator's 2.5 kg through the
     * unit formatter and told pound users to add "+5.5 lbs" while the stepper next to it moved
     * in fives. One table, and the disagreement has nowhere to live.
     *
     * [LoadType.EXTERNAL] because this is the *field's* step, not a particular lift's: a
     * bodyweight lift has no weight to progress but can still carry added load, and its weight
     * field must keep stepping. What a bodyweight lift is ADVISED to do is the calculator's
     * question, and that one honours the null.
     *
     * Computed rather than a constructor argument to avoid initialising the enum from an object
     * that reads the enum back.
     */
    val step: Double
        get() = IncrementTable.displayStep(LoadType.EXTERNAL, this)!!

    val stepLabel: String
        get() = WeightConverter.formatDisplayNumber(step)

    companion object {
        /** Missing or unknown storage is pounds. Kilograms is a Settings choice. */
        fun fromStorage(value: String?): WeightUnit =
            entries.firstOrNull { it.storageKey.equals(value, ignoreCase = true) } ?: LBS
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

    /**
     * The single whole-number volume every session surface must show.
     *
     * Ties-to-even (`kotlin.math.round`), not half-away-from-zero (`roundToInt`).
     * 100 lb × 5 stores 45.4 kg × 5 = 227 kg, which displays as 500.5 lbs and
     * must read **500**, not 501. Summary, History, Session Detail, Exercise
     * Detail, the count-up target, and TalkBack all read this Long.
     */
    fun volumeDisplayWhole(kg: Double, unit: WeightUnit): Long {
        val display = toDisplayValue(kg, unit)
        if (display.isNaN() || display.isInfinite()) return 0L
        return round(display).toLong()
    }

    /** Compose count-up target. Same integer [formatVolumeNumber] will print. */
    fun volumeAnimationTarget(kg: Double, unit: WeightUnit): Int =
        volumeDisplayWhole(kg, unit).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /**
     * Locale-free digits for the whole-number volume.
     *
     * Grouped output ("12,450") is a platform concern — see
     * `util/QuantityFormat`. Domain copy and TalkBack that must stay
     * shared-target read this string.
     */
    fun formatVolumeNumber(kg: Double, unit: WeightUnit): String =
        volumeDisplayWhole(kg, unit).toString()

    fun formatVolumeLabel(kg: Double, unit: WeightUnit): String =
        "${formatVolumeNumber(kg, unit)} ${unit.suffix}"

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

/** Grouped, whole-number label for volume-scale numbers. See [WeightConverter.formatVolumeLabel]. */
fun Double.toVolumeLabel(unit: WeightUnit): String = WeightConverter.formatVolumeLabel(this, unit)

fun Double.toKgLabel(): String = toWeightLabel(WeightUnit.KG)

fun Double.toKgNumber(): String = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(this, WeightUnit.KG))
