package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestExteriorPermissionsTest {
    @Test
    fun missingListsRequiredCapabilitiesOnly() {
        val snapshot = PhoneCapabilitySnapshot(
            sdkInt = 35,
            notificationsEnabled = false,
            restDoneChannelEnabled = true,
            postNotificationsGranted = false,
            canScheduleExactAlarms = false,
            canUseFullScreenIntent = true,
            canDrawOverlays = false,
            batteryUnrestricted = true,
            hasVibrator = true,
        )
        val missing = RestExteriorPermissions.missing(snapshot)
        assertEquals(
            listOf(
                PhoneCapability.NOTIFICATIONS,
                PhoneCapability.DISPLAY_OVERLAY,
                PhoneCapability.EXACT_REST_ALARM,
            ),
            missing,
        )
    }

    @Test
    fun noneMissingWhenRequiredGranted() {
        val snapshot = PhoneCapabilitySnapshot(
            sdkInt = 35,
            notificationsEnabled = true,
            restDoneChannelEnabled = true,
            postNotificationsGranted = true,
            canScheduleExactAlarms = true,
            canUseFullScreenIntent = false,
            canDrawOverlays = true,
            batteryUnrestricted = false,
            hasVibrator = true,
        )
        assertTrue(RestExteriorPermissions.missing(snapshot).isEmpty())
    }
}
