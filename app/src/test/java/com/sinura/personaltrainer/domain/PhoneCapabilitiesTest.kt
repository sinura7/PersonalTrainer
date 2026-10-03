package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCapabilitiesTest {
    @Test
    fun everyCapabilityStateAcrossRepresentativeInputs() {
        val allGranted = PhoneCapabilities.states(
            sdkInt = 34,
            notificationsEnabled = true,
            restDoneChannelEnabled = true,
            postNotificationsGranted = true,
            canScheduleExactAlarms = true,
            canUseFullScreenIntent = true,
            canDrawOverlays = true,
            batteryUnrestricted = true,
            hasVibrator = true,
        )
        assertEquals(CapabilityState.GRANTED, allGranted.state(PhoneCapability.NOTIFICATIONS))
        assertEquals(CapabilityState.GRANTED, allGranted.state(PhoneCapability.EXACT_REST_ALARM))
        assertEquals(CapabilityState.GRANTED, allGranted.state(PhoneCapability.LOCK_SCREEN_ALERT))
        assertEquals(CapabilityState.GRANTED, allGranted.state(PhoneCapability.DISPLAY_OVERLAY))
        assertEquals(CapabilityState.GRANTED, allGranted.state(PhoneCapability.BATTERY))
        assertEquals(CapabilityState.GRANTED, allGranted.state(PhoneCapability.VIBRATION))

        val notificationsOff = PhoneCapabilities.states(
            sdkInt = 34,
            notificationsEnabled = false,
            restDoneChannelEnabled = true,
            postNotificationsGranted = true,
            canScheduleExactAlarms = true,
            canUseFullScreenIntent = true,
            canDrawOverlays = true,
            batteryUnrestricted = true,
            hasVibrator = true,
        )
        assertEquals(CapabilityState.MISSING, notificationsOff.state(PhoneCapability.NOTIFICATIONS))

        val channelBlocked = PhoneCapabilities.states(
            sdkInt = 34,
            notificationsEnabled = true,
            restDoneChannelEnabled = false,
            postNotificationsGranted = true,
            canScheduleExactAlarms = true,
            canUseFullScreenIntent = true,
            canDrawOverlays = true,
            batteryUnrestricted = true,
            hasVibrator = true,
        )
        assertEquals(CapabilityState.MISSING, channelBlocked.state(PhoneCapability.NOTIFICATIONS))

        val noVibrator = PhoneCapabilities.states(
            sdkInt = 34,
            notificationsEnabled = true,
            restDoneChannelEnabled = true,
            postNotificationsGranted = true,
            canScheduleExactAlarms = true,
            canUseFullScreenIntent = true,
            canDrawOverlays = true,
            batteryUnrestricted = true,
            hasVibrator = false,
        )
        assertEquals(CapabilityState.NOT_ON_THIS_PHONE, noVibrator.state(PhoneCapability.VIBRATION))

        val api30 = PhoneCapabilities.states(
            sdkInt = 30,
            notificationsEnabled = true,
            restDoneChannelEnabled = true,
            postNotificationsGranted = true,
            canScheduleExactAlarms = false,
            canUseFullScreenIntent = false,
            canDrawOverlays = true,
            batteryUnrestricted = false,
            hasVibrator = true,
        )
        assertEquals(CapabilityState.GRANTED, api30.state(PhoneCapability.EXACT_REST_ALARM))
        assertEquals(CapabilityState.GRANTED, api30.state(PhoneCapability.LOCK_SCREEN_ALERT))
        assertEquals(CapabilityState.GRANTED, api30.state(PhoneCapability.DISPLAY_OVERLAY))
        assertEquals(CapabilityState.MISSING, api30.state(PhoneCapability.BATTERY))

        val api33DeniedPost = PhoneCapabilities.states(
            sdkInt = 33,
            notificationsEnabled = true,
            restDoneChannelEnabled = true,
            postNotificationsGranted = false,
            canScheduleExactAlarms = true,
            canUseFullScreenIntent = true,
            canDrawOverlays = true,
            batteryUnrestricted = true,
            hasVibrator = true,
        )
        assertEquals(CapabilityState.MISSING, api33DeniedPost.state(PhoneCapability.NOTIFICATIONS))
    }

    private fun List<PhoneCapabilityStatus>.state(capability: PhoneCapability): CapabilityState {
        for (entry in this) {
            if (entry.capability == capability) return entry.state
        }
        error("missing capability $capability")
    }
}
