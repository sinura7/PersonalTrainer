package com.sinura.personaltrainer.domain

/**
 * Per-weekday workout reminder times. Stored in DataStore, not Room.
 *
 * These are the Settings alarms Allen sets: pick the day, scroll the
 * time, AM/PM. Rest-timer notifications stay on their own path.
 */
data class DayReminder(
    val hour: Int = 7,
    val minute: Int = 0,
) {
    fun sanitized(): DayReminder = copy(
        hour = hour.coerceIn(0, 23),
        minute = minute.coerceIn(0, 59),
    )

    val minutesOfDay: Int get() = hour * 60 + minute
}

object WorkoutAlarms {
    fun encode(alarms: Map<Weekday, DayReminder>): Set<String> =
        alarms.map { (day, reminder) ->
            val clean = reminder.sanitized()
            "${day.name}=${clean.hour}:${"%02d".format(clean.minute)}"
        }.toSet()

    fun decode(raw: Set<String>?): Map<Weekday, DayReminder> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.mapNotNull { token ->
            val parts = token.split('=', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val day = Weekday.fromStorage(parts[0]) ?: return@mapNotNull null
            val clock = parts[1].split(':')
            val hour = clock.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val minute = clock.getOrNull(1)?.toIntOrNull() ?: 0
            day to DayReminder(hour = hour, minute = minute).sanitized()
        }.toMap()
    }

    /**
     * Next wall-clock fire for [weekday] at [hour]:[minute] on or after [nowMs].
     * If that clock today is already behind, the next week's same weekday.
     */
    fun nextTriggerEpochDay(
        weekday: Weekday,
        hour: Int,
        minute: Int,
        todayEpochDay: Long,
        nowMinutes: Int,
    ): Long {
        val today = CivilDate.fromEpochDay(todayEpochDay)
        val candidate = today.nextOrSame(weekday)
        val minutes = hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)
        return if (candidate.epochDay == todayEpochDay && minutes <= nowMinutes) {
            candidate.plusDays(Weekday.DAYS_IN_WEEK.toLong()).epochDay
        } else {
            candidate.epochDay
        }
    }
}
