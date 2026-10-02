package com.sinura.personaltrainer.domain

/**
 * Live phone facts for the permission page and the first-open walk.
 */
interface PhoneCapabilityPort {
    fun read(): PhoneCapabilitySnapshot
}

data class PhoneCapabilitySnapshot(
    val sdkInt: Int,
    val notificationsEnabled: Boolean,
    val restDoneChannelEnabled: Boolean,
    val postNotificationsGranted: Boolean,
    val canScheduleExactAlarms: Boolean,
    val canUseFullScreenIntent: Boolean,
    val batteryUnrestricted: Boolean,
    val hasVibrator: Boolean,
) {
    val states: List<PhoneCapabilityStatus> = PhoneCapabilities.states(
        sdkInt = sdkInt,
        notificationsEnabled = notificationsEnabled,
        restDoneChannelEnabled = restDoneChannelEnabled,
        postNotificationsGranted = postNotificationsGranted,
        canScheduleExactAlarms = canScheduleExactAlarms,
        canUseFullScreenIntent = canUseFullScreenIntent,
        batteryUnrestricted = batteryUnrestricted,
        hasVibrator = hasVibrator,
    )

    fun state(capability: PhoneCapability): CapabilityState {
        for (entry in states) {
            if (entry.capability == capability) return entry.state
        }
        error("missing capability $capability")
    }
}
