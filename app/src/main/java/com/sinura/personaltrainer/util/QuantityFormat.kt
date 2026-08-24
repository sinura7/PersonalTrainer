package com.sinura.personaltrainer.util

import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.round

/**
 * Locale-aware quantity formatting.
 *
 * Lives outside `domain/` (ADR-003). Domain math ([WeightConverter.volumeDisplayWhole])
 * is the single whole-number volume; this is how a platform prints it.
 */
object QuantityFormat {
    fun formatVolumeNumber(
        kg: Double,
        unit: WeightUnit,
        locale: Locale = Locale.getDefault(),
    ): String = NumberFormat.getIntegerInstance(locale)
        .format(WeightConverter.volumeDisplayWhole(kg, unit))

    fun formatVolumeLabel(
        kg: Double,
        unit: WeightUnit,
        locale: Locale = Locale.getDefault(),
    ): String = "${formatVolumeNumber(kg, unit, locale)} ${unit.suffix}"

    fun formatGroupedNumber(value: Double, locale: Locale = Locale.getDefault()): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val whole = round(value).toLong()
        return NumberFormat.getIntegerInstance(locale).format(whole)
    }
}
