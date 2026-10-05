package com.sinura.personaltrainer.domain

/**
 * Permissions Temper needs for rest timer behavior outside the in-app UI
 * (foreground service shade card, floating pill, lock widget updates).
 */
object RestExteriorPermissions {
    fun missing(snapshot: PhoneCapabilitySnapshot): List<PhoneCapability> {
        val out = mutableListOf<PhoneCapability>()
        for (capability in REQUIRED) {
            if (snapshot.state(capability) == CapabilityState.MISSING) {
                out.add(capability)
            }
        }
        return out
    }

    val REQUIRED: List<PhoneCapability> = listOf(
        PhoneCapability.NOTIFICATIONS,
        PhoneCapability.DISPLAY_OVERLAY,
        PhoneCapability.EXACT_REST_ALARM,
    )
}

object RestExteriorPermissionCopy {
    const val BLOCKING_TITLE = "Permissions required"
    const val BLOCKING_LEAD =
        "Temper needs these settings for rest timer alerts outside the app " +
            "(floating countdown, notifications, on-time rest end)."
    const val OPEN_SETTINGS = "Open settings"
    const val CHECK_AGAIN = "I turned it on — check again"
}
