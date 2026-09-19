package com.sinura.personaltrainer.domain

/**
 * First-open permission walk for Temper Debug.
 *
 * Asks once per install for the permissions the app already uses:
 * notifications, exact alarms, and unrestricted battery. Grant or deny
 * both count as asked — a second session must not re-spam.
 */
enum class LaunchPermissionStep {
    NOTIFICATIONS,
    EXACT_ALARM,
    BATTERY,
    DONE,
}

object LaunchPermissionCopy {
    const val NOTIFICATIONS_TITLE = "Allow notifications"
    const val NOTIFICATIONS_BODY =
        "Rest alerts and workout reminders need a notification."
    const val EXACT_TITLE = "Allow exact alarms"
    const val EXACT_BODY =
        "Workout reminders and rest alerts stay on time when this is on."
    const val BATTERY_TITLE = "Unrestricted battery"
    const val BATTERY_BODY =
        "Rest alerts can die in the background until battery is unrestricted."
    const val CONTINUE = "Continue"
    const val NOT_NOW = "Not now"
}

object LaunchPermissions {
    fun shouldAsk(alreadyAsked: Boolean): Boolean = !alreadyAsked

    fun nextStep(
        notificationsGranted: Boolean,
        exactAlarmsGranted: Boolean,
        batteryUnrestricted: Boolean,
        sdkInt: Int,
    ): LaunchPermissionStep = when {
        sdkInt >= 33 && !notificationsGranted -> LaunchPermissionStep.NOTIFICATIONS
        sdkInt >= 31 && !exactAlarmsGranted -> LaunchPermissionStep.EXACT_ALARM
        !batteryUnrestricted -> LaunchPermissionStep.BATTERY
        else -> LaunchPermissionStep.DONE
    }
}
