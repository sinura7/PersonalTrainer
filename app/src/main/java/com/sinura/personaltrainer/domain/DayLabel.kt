package com.sinura.personaltrainer.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

    fun relative(thenMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (thenMs <= 0L) return null
        val then = Instant.ofEpochMilli(thenMs).atZone(zone).toLocalDate()
        val now = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(then, now)
        return when {
            days < 0L -> null
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days <= RELATIVE_DAYS -> "$days days ago"
            else -> null
        }
    }
}
