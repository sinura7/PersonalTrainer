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
        "Rest alerts and workout reminders need a notification.\n\n${PermissionsCopy.MANAGE_IN_SETTINGS}"
    const val EXACT_TITLE = "Allow exact alarms"
    const val EXACT_BODY =
        "Workout reminders and rest alerts stay on time when this is on.\n\n${PermissionsCopy.MANAGE_IN_SETTINGS}"
    const val BATTERY_TITLE = "Unrestricted battery"
    const val BATTERY_BODY =
        "Rest alerts can die in the background until battery is unrestricted.\n\n${PermissionsCopy.MANAGE_IN_SETTINGS}"
    const val CONTINUE = "Continue"
    const val NOT_NOW = "Not now"
}

object LaunchPermissions {
    fun shouldAsk(alreadyAsked: Boolean): Boolean = !alreadyAsked

    /**
     * Whether the walk may put its first dialog up now.
     *
     * Choosing Account or Drive on the first-launch chooser saves the choice and opens Settings
     * together, and the walk used to start on the save — over the sign-in form. It now waits
     * until the user is on a tab other than Settings with no Settings page on its way. Once
     * showing it stays, so leaving mid-walk does not drop the steps still to come.
     */
    fun walkMayShow(
        postureChosen: Boolean,
        onTabAwayFromSettings: Boolean,
        settingsPageOpening: Boolean,
        alreadyShowing: Boolean,
    ): Boolean = alreadyShowing || (postureChosen && onTabAwayFromSettings && !settingsPageOpening)

    fun nextStep(snapshot: PhoneCapabilitySnapshot): LaunchPermissionStep {
        val states = snapshot.states
        if (snapshot.sdkInt >= 33 &&
            states.state(PhoneCapability.NOTIFICATIONS) == CapabilityState.MISSING
        ) {
            return LaunchPermissionStep.NOTIFICATIONS
        }
        if (snapshot.sdkInt >= 31 &&
            states.state(PhoneCapability.EXACT_REST_ALARM) == CapabilityState.MISSING
        ) {
            return LaunchPermissionStep.EXACT_ALARM
        }
        if (states.state(PhoneCapability.BATTERY) == CapabilityState.MISSING) {
            return LaunchPermissionStep.BATTERY
        }
        return LaunchPermissionStep.DONE
    }

    /** Skipped steps count as handled for the walk only. */
    fun nextStepWithSkips(
        snapshot: PhoneCapabilitySnapshot,
        skippedNotifications: Boolean,
        skippedExact: Boolean,
        skippedBattery: Boolean,
    ): LaunchPermissionStep {
        val adjusted = snapshot.copy(
            notificationsEnabled = snapshot.notificationsEnabled || skippedNotifications,
            restDoneChannelEnabled = snapshot.restDoneChannelEnabled || skippedNotifications,
            postNotificationsGranted = snapshot.postNotificationsGranted || skippedNotifications,
            canScheduleExactAlarms = snapshot.canScheduleExactAlarms || skippedExact,
            batteryUnrestricted = snapshot.batteryUnrestricted || skippedBattery,
        )
        return nextStep(adjusted)
    }
}

private fun List<PhoneCapabilityStatus>.state(capability: PhoneCapability): CapabilityState {
    for (entry in this) {
        if (entry.capability == capability) return entry.state
    }
    error("missing capability $capability")
}
