package com.sinura.personaltrainer.domain

enum class PhoneCapability {
    NOTIFICATIONS,
    EXACT_REST_ALARM,
    LOCK_SCREEN_ALERT,
    DISPLAY_OVERLAY,
    BATTERY,
    VIBRATION,
}

enum class CapabilityState {
    GRANTED,
    MISSING,
    NOT_ON_THIS_PHONE,
}

data class PhoneCapabilityStatus(
    val capability: PhoneCapability,
    val state: CapabilityState,
)

/**
 * Pure capability table shared by the first-open walk and Settings → Permissions.
 */
object PhoneCapabilities {
    fun states(
        sdkInt: Int,
        notificationsEnabled: Boolean,
        restDoneChannelEnabled: Boolean,
        postNotificationsGranted: Boolean,
        canScheduleExactAlarms: Boolean,
        canUseFullScreenIntent: Boolean,
        canDrawOverlays: Boolean,
        batteryUnrestricted: Boolean,
        hasVibrator: Boolean,
    ): List<PhoneCapabilityStatus> = PhoneCapability.entries.map { capability ->
        PhoneCapabilityStatus(
            capability = capability,
            state = stateFor(
                capability = capability,
                sdkInt = sdkInt,
                notificationsEnabled = notificationsEnabled,
                restDoneChannelEnabled = restDoneChannelEnabled,
                postNotificationsGranted = postNotificationsGranted,
                canScheduleExactAlarms = canScheduleExactAlarms,
                canUseFullScreenIntent = canUseFullScreenIntent,
                canDrawOverlays = canDrawOverlays,
                batteryUnrestricted = batteryUnrestricted,
                hasVibrator = hasVibrator,
            ),
        )
    }

    fun stateFor(
        capability: PhoneCapability,
        sdkInt: Int,
        notificationsEnabled: Boolean,
        restDoneChannelEnabled: Boolean,
        postNotificationsGranted: Boolean,
        canScheduleExactAlarms: Boolean,
        canUseFullScreenIntent: Boolean,
        canDrawOverlays: Boolean,
        batteryUnrestricted: Boolean,
        hasVibrator: Boolean,
    ): CapabilityState = when (capability) {
        PhoneCapability.NOTIFICATIONS -> when {
            sdkInt >= 33 && !postNotificationsGranted -> CapabilityState.MISSING
            !notificationsEnabled || !restDoneChannelEnabled -> CapabilityState.MISSING
            else -> CapabilityState.GRANTED
        }
        PhoneCapability.EXACT_REST_ALARM -> when {
            sdkInt < 31 -> CapabilityState.GRANTED
            canScheduleExactAlarms -> CapabilityState.GRANTED
            else -> CapabilityState.MISSING
        }
        PhoneCapability.LOCK_SCREEN_ALERT -> when {
            sdkInt < 34 -> CapabilityState.GRANTED
            canUseFullScreenIntent -> CapabilityState.GRANTED
            else -> CapabilityState.MISSING
        }
        PhoneCapability.DISPLAY_OVERLAY -> when {
            sdkInt < 23 -> CapabilityState.GRANTED
            canDrawOverlays -> CapabilityState.GRANTED
            else -> CapabilityState.MISSING
        }
        PhoneCapability.BATTERY -> if (batteryUnrestricted) {
            CapabilityState.GRANTED
        } else {
            CapabilityState.MISSING
        }
        PhoneCapability.VIBRATION -> if (hasVibrator) {
            CapabilityState.GRANTED
        } else {
            CapabilityState.NOT_ON_THIS_PHONE
        }
    }

    fun missingCount(states: List<PhoneCapabilityStatus>): Int =
        states.count { it.state == CapabilityState.MISSING }
}
