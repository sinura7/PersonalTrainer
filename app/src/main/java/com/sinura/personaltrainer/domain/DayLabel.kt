package com.sinura.personaltrainer.domain


/**
 * How long ago something happened, in the words a lifter would use.
 *
 * Counted in calendar days rather than elapsed hours: a set logged at 11pm last night is
 * "Yesterday" at 1am, not "2 hours ago", because that is how the person remembers it.
 *
 * Returns null past a week, where "9 days ago" stops being easier to read than the date
 * itself — the caller formats that, since only it knows the device's locale.
 */
object DayLabel {
    const val RELATIVE_DAYS = 7

    fun relative(
        thenMs: Long,
        nowMs: Long,
        time: TimePort,
        zoneId: String = time.defaultZoneId(),
    ): String? {
        if (thenMs <= 0L) return null
        val then = time.civilDate(thenMs, zoneId)
        val now = time.civilDate(nowMs, zoneId)
        val days = now.epochDay - then.epochDay
        return when {
            days < 0L -> null
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days <= RELATIVE_DAYS -> "$days days ago"
            else -> null
        }
    }
}

/**
 * Today, as a day number rather than a moment.
 *
 * Every "is this day today", "has this day gone" and "was anything logged here" comparison in
 * the app is a day comparison, and doing them in milliseconds gets the answer wrong twice a day
 * — once either side of midnight. Moved out of the old Schedule screen so the week strip, the
 * calendar and the derivation all ask the same question the same way.
 */
fun todayEpochDay(
    nowMs: Long,
    time: TimePort,
    zoneId: String = time.defaultZoneId(),
): Long = time.civilDate(nowMs, zoneId).epochDay
