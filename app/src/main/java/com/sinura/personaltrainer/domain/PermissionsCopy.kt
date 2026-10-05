package com.sinura.personaltrainer.domain

object PermissionsCopy {
    const val SETTINGS_ROW_TITLE = "Permissions"
    const val SETTINGS_ROW_SUBTITLE = "What Temper may do on this phone"

    const val INSTALL_TIME_LINE =
        "Internet, network state, foreground service, wake lock, and boot: granted at install, nothing to do."

    const val MANAGE_IN_SETTINGS = "Manage in Settings → Permissions"

    const val LOCK_WIDGET_SETUP =
        "Lock screen widget (Samsung One UI): wake the phone, tap and hold the lock screen, " +
            "choose Widgets, then pick Rest countdown or Rest countdown (compact) under Temper. " +
            "The widget updates while a rest runs. Some One UI builds hide third-party lock " +
            "widgets until both compact and wide sizes are listed."

    fun homeSummary(missingCount: Int): String = when (missingCount) {
        0 -> "All set"
        1 -> "1 to fix"
        else -> "$missingCount to fix"
    }

    fun stateLabel(state: CapabilityState): String = when (state) {
        CapabilityState.GRANTED -> "Granted"
        CapabilityState.MISSING -> "Missing"
        CapabilityState.NOT_ON_THIS_PHONE -> "Not on this phone"
    }

    fun title(capability: PhoneCapability): String = when (capability) {
        PhoneCapability.NOTIFICATIONS -> "Notifications"
        PhoneCapability.EXACT_REST_ALARM -> "Precise rest alerts"
        PhoneCapability.LOCK_SCREEN_ALERT -> "Rest alert over the lock screen"
        PhoneCapability.DISPLAY_OVERLAY -> "Rest timer over other apps"
        PhoneCapability.BATTERY -> "Battery"
        PhoneCapability.VIBRATION -> "Vibration"
    }

    fun why(capability: PhoneCapability): String = when (capability) {
        PhoneCapability.NOTIFICATIONS ->
            "Required for the rest timer while Temper is in the background: the ongoing " +
                "countdown notification and rest-done alerts."
        PhoneCapability.EXACT_REST_ALARM ->
            "Rest and reminders fire on time when the screen is off. " +
                "Android 14 phones start with this off for new installs."
        PhoneCapability.LOCK_SCREEN_ALERT ->
            "While you rest with the phone locked, Temper can show the live countdown over the " +
                "lock screen; rest-done uses the same permission. Sideloaded builds usually start " +
                "with this granted."
        PhoneCapability.DISPLAY_OVERLAY ->
            "Required for the draggable rest countdown when you leave Temper during a rest. " +
                "Turn on Display over other apps (Appear on top on some phones)."
        PhoneCapability.BATTERY ->
            "Unrestricted battery lifts the once-per-nine-minutes throttle on the rest wakeup. " +
                "Exact alarms already fire in Doze."
        PhoneCapability.VIBRATION ->
            "Rest cues can shake the phone when you turn vibration on in Rest timer. " +
                "Your phone's Alarm vibration setting also applies on Android 13+."
    }

    fun fixLabel(capability: PhoneCapability): String = when (capability) {
        PhoneCapability.NOTIFICATIONS -> "Fix notifications"
        PhoneCapability.EXACT_REST_ALARM -> "Allow precise alarms"
        PhoneCapability.LOCK_SCREEN_ALERT -> "Allow lock-screen alert"
        PhoneCapability.DISPLAY_OVERLAY -> "Allow display over other apps"
        PhoneCapability.BATTERY -> "Allow unrestricted battery"
        PhoneCapability.VIBRATION -> ""
    }
}
