package com.sinura.personaltrainer.domain

/**
 * Settings copy for workout reminders. Quiet hours are stored; this is
 * the readout and the permission recovery sentence. [GONE] is the
 * shade tap whose plan row is already gone — the notification was
 * dismissed and the gym still needs a sentence.
 */
object ReminderCopy {
    const val SWITCH_TITLE = "Reminders"
    const val SWITCH_SUBTITLE = "Workout alarm for the days you pick. Rest alerts are unchanged."
    const val DAYS = "Days"
    const val TIME = "Time"
    const val QUIET_START = "Quiet from"
    const val QUIET_END = "Quiet until"
    const val QUIET_CAPTION = "Quiet hours can still defer a reminder that lands overnight."
    const val PERMISSION_TITLE = "Reminders need a notification"
    const val PERMISSION_BODY =
        "This phone has not allowed notifications. Reminders stay silent until you turn them on."
    const val PERMISSION_ACTION = "Turn on"
    const val GONE = "That session is no longer on the plan."
    const val ALARM_EMPTY = "No days yet. Pick a day, then scroll the time."

    val quietStartHours: List<Int> = listOf(20, 21, 22, 23)
    val quietEndHours: List<Int> = listOf(5, 6, 7, 8)
    val minuteChoices: List<Int> = listOf(0, 15, 30, 45)

    fun hourLabel(hour: Int, format: ClockFormat = ClockFormat.TWELVE): String =
        ClockCopy.hourChip(hour, format)

    fun timeLabel(
        hour: Int,
        minute: Int,
        format: ClockFormat = ClockFormat.TWELVE,
    ): String = ClockCopy.format(hour, minute, format)

    fun quietHoursLine(
        startHour: Int,
        endHour: Int,
        format: ClockFormat = ClockFormat.TWELVE,
    ): String = "Quiet hours ${hourLabel(startHour, format)}–${hourLabel(endHour, format)}"

    fun startChoices(current: Int): List<Int> =
        (quietStartHours + current.coerceIn(0, 23)).distinct().sorted()

    fun endChoices(current: Int): List<Int> =
        (quietEndHours + current.coerceIn(0, 23)).distinct().sorted()

    fun twelveHour(hour24: Int): Int = when (val rem = hour24.coerceIn(0, 23) % 12) {
        0 -> 12
        else -> rem
    }

    fun isPm(hour24: Int): Boolean = hour24.coerceIn(0, 23) >= 12

    fun toHour24(twelveHour: Int, pm: Boolean): Int {
        val hour = twelveHour.coerceIn(1, 12)
        return when {
            hour == 12 && !pm -> 0
            hour == 12 && pm -> 12
            pm -> hour + 12
            else -> hour
        }
    }
}
