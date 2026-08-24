package com.sinura.personaltrainer.domain

/**
 * ISO weekday, Monday-first.
 *
 * Same names and declaration order as the platform `DayOfWeek` this replaces, so
 * stored ordinals (0 = Monday) and `name` tokens keep meaning across the reset.
 * The type itself does not import `java.time`.
 */
enum class Weekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
    ;

    /** ISO-8601 value: Monday = 1 … Sunday = 7. */
    val isoValue: Int get() = ordinal + 1

    fun plus(days: Long): Weekday {
        val shift = ((days % DAYS_IN_WEEK) + DAYS_IN_WEEK) % DAYS_IN_WEEK
        return entries[(ordinal + shift.toInt()) % DAYS_IN_WEEK]
    }

    fun minus(days: Long): Weekday = plus(-days)

    /** Three-letter title case: Mon, Tue. Locale-free; UI may format via the platform. */
    fun shortLabel(): String = name.take(3).lowercase().replaceFirstChar { it.titlecase() }

    companion object {
        const val DAYS_IN_WEEK = 7

        fun fromIso(value: Int): Weekday {
            require(value in 1..7) { "ISO weekday must be 1..7, was $value" }
            return entries[value - 1]
        }

        fun fromOrdinal(ordinal: Int): Weekday? = entries.getOrNull(ordinal)

        fun fromStorage(value: String?): Weekday? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

        /**
         * Unix epoch day 0 is Thursday, 1970-01-01.
         * `floorMod(epochDay + 3, 7) + 1` is the ISO weekday.
         */
        fun fromEpochDay(epochDay: Long): Weekday =
            fromIso((((epochDay + 3) % 7 + 7) % 7).toInt() + 1)
    }
}
