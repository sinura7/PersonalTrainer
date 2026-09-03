package com.sinura.personaltrainer.domain

/**
 * Parsing for typed weight and rep entry.
 *
 * Steppers are fine for nudging a number; they are not fine for setting one. Going from 20 kg
 * to 140 kg is 48 taps at the 2.5 kg step, which is why typing exists at all — and why this
 * has to be forgiving about how the number arrives.
 *
 * Deliberately stricter than [WeightConverter.parseDisplayToKg], which is built for a live
 * text field and falls back to the previous value on a bad keystroke. Entry is confirmed
 * explicitly, so an unparseable string should refuse rather than quietly keep the old number.
 */
object NumericEntry {
    /**
     * A guard against a fumbled tap, not a judgement about anyone's training. Someone typing
     * three digits meant to type two far more often than they meant to log a 500-rep set.
     */
    const val MAX_REPS = 100

    enum class Ime { NEXT, DONE }

    /** Sets → Reps → Rest → Weight. */
    val ROUTINE_EDITOR_CHAIN = listOf(Ime.NEXT, Ime.NEXT, Ime.NEXT, Ime.DONE)

    /** Weight → Reps. */
    val COMPOSER_STRENGTH_CHAIN = listOf(Ime.NEXT, Ime.DONE)

    /** Minutes → Distance. */
    val COMPOSER_CARDIO_CHAIN = listOf(Ime.NEXT, Ime.DONE)

    /** Password → Confirm. */
    val PASSWORD_CHAIN = listOf(Ime.NEXT, Ime.DONE)

    val CUSTOM_REST = Ime.DONE
    val LIVE_CARDIO_DISTANCE = Ime.DONE
    val UNLOCK_PASSWORD = Ime.DONE

    /** Rejects anything a set could not actually be logged at, so the caller can refuse it. */
    fun parseWeightKg(input: String, unit: WeightUnit): Double? {
        val value = parseDecimal(input) ?: return null
        if (value < 0.0) return null
        return WeightConverter.toKg(value, unit)
    }

    fun parseReps(input: String): Int? {
        val value = parseDecimal(input) ?: return null
        val whole = value.toInt()
        if (whole.toDouble() != value) return null
        if (whole < 1 || whole > MAX_REPS) return null
        return whole
    }

    /**
     * Digits, with an optional decimal separator followed by one or two more.
     *
     * A comma counts as that separator: on a phone set to most of Europe the numeric keyboard's
     * decimal key emits `,`, so rejecting it would make typed entry silently unusable for
     * anyone in that half of the world.
     *
     * Capping the fraction at two digits is what makes that safe. `1,000` reads as one
     * thousand to an English speaker and as one to everyone else, and there is no way to tell
     * which was meant — so it is refused rather than guessed at. Nothing entered here is ever
     * four digits or needs three decimal places, so the rule costs nothing real and removes
     * the one input that could silently log 1 kg for an intended 1000.
     */
    private val DECIMAL = Regex("""^-?\d+([.,]\d{1,2})?$""")

    fun parseDecimal(input: String): Double? {
        val trimmed = input.trim()
        if (!DECIMAL.matches(trimmed)) return null
        return trimmed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /**
     * Live-field filter: digits and at most one decimal separator, comma or point.
     *
     * Stripping the comma used to turn `102,5` into `1025` in the routine editor.
     */
    fun filterDecimal(raw: String): String {
        val filtered = raw.filter { it.isDigit() || it == '.' || it == ',' }
        val sepIndex = filtered.indexOfFirst { it == '.' || it == ',' }
        if (sepIndex < 0) return filtered
        val sep = filtered[sepIndex]
        val intPart = filtered.take(sepIndex).filter { it.isDigit() }
        val frac = filtered.substring(sepIndex + 1).filter { it.isDigit() }
        return intPart + sep + frac
    }
}
