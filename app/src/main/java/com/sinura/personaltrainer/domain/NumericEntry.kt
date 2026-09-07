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
 *
 * ## The contract every typed number keeps (UX06)
 *
 * The text the owner sees is the text they typed. Nothing between the keyboard and the commit
 * rewrites it: no stripping of a minus sign, no collapsing of two decimal points into one, no
 * dropping of a stray letter. Then, at the moment the value is used — Add set, a card losing
 * focus, Finish — it is parsed, and it either parses as exactly what was written or the
 * field is refused with a message that names the rule.
 *
 * There used to be a live filter here (`filterDecimal`) that kept "digits and one separator".
 * It read as a convenience and was a meaning change: a pasted `-50` became `50`, `1.2.3`
 * became `1.23`, `8e2` became `82`, and the integer fields' `filter(Char::isDigit)` turned
 * `8.5` reps into `85`. Each of those is a number the owner never typed, accepted without a
 * word. A field that refuses to salvage digits from text it cannot read is the only version
 * that never logs the wrong set.
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
     *
     * The optional leading minus is matched so that `-50` is *read* as negative and refused by
     * the weight rule for being negative, rather than being unreadable. Either way it is never
     * stored as 50.
     */
    private val DECIMAL = Regex("""^-?\d+([.,]\d{1,2})?$""")

    fun parseDecimal(input: String): Double? {
        val trimmed = input.trim()
        if (!DECIMAL.matches(trimmed)) return null
        return trimmed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /**
     * A whole, non-negative count with no separator at all: `\d+` and nothing else. `8.5`
     * is refused here rather than truncated to 8 or read as 85.
     */
    private val WHOLE = Regex("""^\d+$""")

    /** A typed field, read at its commit boundary. Blank is its own answer, not an error. */
    sealed interface Typed<out T> {
        data object Blank : Typed<Nothing>

        data class Valid<T>(val value: T) : Typed<T>

        /** [message] names the rule that was broken and what would satisfy it. */
        data class Invalid(val message: String) : Typed<Nothing>

        val valueOrNull: T?
            get() = (this as? Valid<T>)?.value

        val messageOrNull: String?
            get() = (this as? Invalid)?.message
    }

    /** A weight in the display unit: zero or more, up to two decimals, point or comma. */
    fun typedWeightKg(input: String, unit: WeightUnit): Typed<Double> {
        if (input.isBlank()) return Typed.Blank
        val value = parseDecimal(input) ?: return Typed.Invalid(WEIGHT_RULE)
        if (value < 0.0) return Typed.Invalid(WEIGHT_NEGATIVE)
        return Typed.Valid(WeightConverter.toKg(value, unit))
    }

    /** A rep count: a whole number from 1 to [MAX_REPS]. `8.5` is refused, never `85`. */
    fun typedReps(input: String): Typed<Int> {
        if (input.isBlank()) return Typed.Blank
        val trimmed = input.trim()
        if (!WHOLE.matches(trimmed)) return Typed.Invalid(REPS_RULE)
        val whole = trimmed.toIntOrNull() ?: return Typed.Invalid(REPS_RULE)
        if (whole < 1 || whole > MAX_REPS) return Typed.Invalid(REPS_RULE)
        return Typed.Valid(whole)
    }

    /**
     * A whole number of at least [min]. Used for sets, rest seconds and minutes; the caller
     * passes the rule text so the message names the field rather than "the value".
     */
    fun typedWhole(input: String, min: Int, rule: String): Typed<Int> {
        if (input.isBlank()) return Typed.Blank
        val trimmed = input.trim()
        if (!WHOLE.matches(trimmed)) return Typed.Invalid(rule)
        val whole = trimmed.toIntOrNull() ?: return Typed.Invalid(rule)
        if (whole < min) return Typed.Invalid(rule)
        return Typed.Valid(whole)
    }

    /**
     * An optional distance in the display unit, returned in kilometres. Blank means "no
     * distance", and so does a typed zero — there is no such thing as a 0 km run to record,
     * and that is the one salvage this file allows because it drops a value rather than
     * inventing one. Anything else that does not read as a number is refused.
     */
    fun typedDistanceKm(input: String, unit: DistanceUnit): Typed<Double> {
        if (input.isBlank()) return Typed.Blank
        val amount = parseDecimal(input) ?: return Typed.Invalid(DISTANCE_RULE)
        if (amount < 0.0) return Typed.Invalid(DISTANCE_RULE)
        if (amount == 0.0) return Typed.Blank
        return Typed.Valid(
            when (unit) {
                DistanceUnit.KM -> amount
                DistanceUnit.MI -> amount * DistanceUnit.METERS_PER_MILE / 1_000.0
            },
        )
    }

    // Field rules, worded as the fix: what to type, not what went wrong.
    const val WEIGHT_RULE = "Enter a weight as a number, like 60 or 62.5."
    const val WEIGHT_NEGATIVE = "A weight cannot be negative. Enter 0 or more."
    const val REPS_RULE = "Enter a whole number of reps, 1 to $MAX_REPS."
    const val SETS_RULE = "Enter a whole number of sets, at least 1."
    const val REST_RULE = "Enter rest as whole seconds."
    const val MINUTES_RULE = "Enter whole minutes, at least 1."
    const val DISTANCE_RULE = "Enter a distance as a number, like 5 or 5.5, or leave it blank."
}
