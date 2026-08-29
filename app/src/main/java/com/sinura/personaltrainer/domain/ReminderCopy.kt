package com.sinura.personaltrainer.domain

/**
 * Settings copy for workout reminders. Quiet hours are stored; this is
 * the readout and the permission recovery sentence.
 */
object ReminderCopy {
    const val SWITCH_TITLE = "Reminders"
    const val SWITCH_SUBTITLE = "Best-effort. Rest alerts are unchanged."
    const val QUIET_START = "Quiet from"
    const val QUIET_END = "Quiet until"
    const val PERMISSION_TITLE = "Reminders need a notification"
    const val PERMISSION_BODY =
        "This phone has not allowed notifications. Reminders stay silent until you turn them on."
    const val PERMISSION_ACTION = "Turn on"

    val quietStartHours: List<Int> = listOf(20, 21, 22, 23)
    val quietEndHours: List<Int> = listOf(5, 6, 7, 8)

    fun hourLabel(hour: Int, format: ClockFormat = ClockFormat.TWENTY_FOUR): String =
        ClockCopy.hourChip(hour, format)

    fun quietHoursLine(
        startHour: Int,
        endHour: Int,
        format: ClockFormat = ClockFormat.TWENTY_FOUR,
    ): String = "Quiet hours ${hourLabel(startHour, format)}–${hourLabel(endHour, format)}"

    fun startChoices(current: Int): List<Int> =
        (quietStartHours + current.coerceIn(0, 23)).distinct().sorted()

    fun endChoices(current: Int): List<Int> =
        (quietEndHours + current.coerceIn(0, 23)).distinct().sorted()
}
