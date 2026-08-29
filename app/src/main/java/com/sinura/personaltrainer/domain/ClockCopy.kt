package com.sinura.personaltrainer.domain

/**
 * How wall times are shown. Stored hours stay 0–23.
 *
 * Regular is the default, matching pounds as the default weight unit.
 * Military is a Settings choice.
 */
enum class ClockFormat(
    val storageKey: String,
    val displayName: String,
    val shortLabel: String,
) {
    TWELVE(
        storageKey = "12h",
        displayName = "Regular",
        shortLabel = "12h",
    ),
    TWENTY_FOUR(
        storageKey = "24h",
        displayName = "Military",
        shortLabel = "24h",
    ),
    ;

    companion object {
        fun fromStorage(value: String?): ClockFormat =
            entries.firstOrNull { it.storageKey.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: TWELVE
    }
}

object ClockCopy {
    /** Compact hour picks for an existing Plan-day block. */
    val SESSION_HOURS: List<Int> = listOf(6, 7, 8, 9, 12, 17, 18, 19, 20, 21)

    fun format(hour: Int, minute: Int = 0, format: ClockFormat): String {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        return when (format) {
            ClockFormat.TWENTY_FOUR -> "%02d:%02d".format(h, m)
            ClockFormat.TWELVE -> {
                val period = if (h < 12) "AM" else "PM"
                val twelve = when (val rem = h % 12) {
                    0 -> 12
                    else -> rem
                }
                if (m == 0) "$twelve $period" else "$twelve:${"%02d".format(m)} $period"
            }
        }
    }

    fun hourChip(hour: Int, format: ClockFormat): String = format(hour, 0, format)

    fun hourChoices(current: Int): List<Int> =
        (SESSION_HOURS + current.coerceIn(0, 23)).distinct().sorted()
}
