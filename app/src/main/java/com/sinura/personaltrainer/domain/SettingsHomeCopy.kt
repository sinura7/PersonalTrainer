package com.sinura.personaltrainer.domain

/**
 * Settings is an index of doors, not a filing cabinet.
 *
 * Each row names a focused screen. Summaries are what the gym-floor
 * glance needs: the current choice, not the whole form.
 */
object SettingsHomeCopy {
    const val DISPLAY = "Display"
    const val REMINDERS = "Reminders"
    const val GENERATOR = "Week generator"
    const val REST = "Rest timer"
    const val BODYWEIGHT = "Bodyweight"
    const val BACKUP = "Backup"
    const val PLAN = "Your plan"
    const val DIAGNOSTICS = "Diagnostics"
    const val ABOUT = "About"
    const val DEVELOPER = "Developer"
    const val LOG = "Log"
    const val FOUNDATION = "Foundation"
    const val UPDATE = "Update Temper Debug"

    const val OFF = "Off"
    const val NO_DAYS = "No days yet"
    const val BACKUP_SUMMARY = "Export, restore, Drive"
    const val DIAGNOSTICS_SUMMARY = "Share a bundle from this phone"
    const val ABOUT_SUMMARY = "Version and how to update"
    const val LOG_SUMMARY = "Redact log messages"
    const val FOUNDATION_SUMMARY = "Database generation"

    fun displaySummary(unit: WeightUnit, clock: ClockFormat): String =
        "${unit.suffix} · ${clock.displayName}"

    fun remindersSummary(
        preferences: ReminderPreferences,
        clock: ClockFormat,
    ): String {
        if (preferences.optOut) return OFF
        if (preferences.dayAlarms.isEmpty()) return NO_DAYS
        val days = Weekday.entries
            .filter { it in preferences.dayAlarms }
            .joinToString(", ") { it.shortLabel() }
        val times = preferences.dayAlarms.values.map { it.hour to it.minute }.toSet()
        return if (times.size == 1) {
            val time = times.single()
            "$days · ${ReminderCopy.timeLabel(time.first, time.second, clock)}"
        } else {
            days
        }
    }

    fun generatorSummary(
        daysPerWeek: Int,
        preferredDays: Set<Weekday>,
        split: SplitStyle,
        goal: TrainingGoal,
    ): String {
        val days = if (preferredDays.isNotEmpty()) preferredDays.size else daysPerWeek
        return "$days days · ${split.displayName} · ${goal.displayName}"
    }

    fun restSummary(preferences: RestTimerPreferences): String =
        RestTimer.formatClock(preferences.defaultRestSeconds)

    fun bodyweightSummary(
        bodyweightKg: Double?,
        unit: WeightUnit,
        checkIn: Weekday?,
    ): String {
        val weight = bodyweightKg?.toWeightLabel(unit) ?: "Not set"
        return if (checkIn == null) weight else "$weight · ${checkIn.shortLabel()}"
    }
}
