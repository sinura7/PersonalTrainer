package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPermissionsTest {
    @Test
    fun firstSessionAsksAndSecondDoesNot() {
        assertTrue(LaunchPermissions.shouldAsk(alreadyAsked = false))
        assertFalse(LaunchPermissions.shouldAsk(alreadyAsked = true))
    }

    @Test
    fun nextStepWalksNotificationsThenExactThenBattery() {
        assertEquals(
            LaunchPermissionStep.NOTIFICATIONS,
            LaunchPermissions.nextStep(
                notificationsGranted = false,
                exactAlarmsGranted = false,
                batteryUnrestricted = false,
                sdkInt = 33,
            ),
        )
        assertEquals(
            LaunchPermissionStep.EXACT_ALARM,
            LaunchPermissions.nextStep(
                notificationsGranted = true,
                exactAlarmsGranted = false,
                batteryUnrestricted = false,
                sdkInt = 33,
            ),
        )
        assertEquals(
            LaunchPermissionStep.BATTERY,
            LaunchPermissions.nextStep(
                notificationsGranted = true,
                exactAlarmsGranted = true,
                batteryUnrestricted = false,
                sdkInt = 33,
            ),
        )
        assertEquals(
            LaunchPermissionStep.DONE,
            LaunchPermissions.nextStep(
                notificationsGranted = true,
                exactAlarmsGranted = true,
                batteryUnrestricted = true,
                sdkInt = 33,
            ),
        )
    }

    @Test
    fun olderSdksSkipNotificationsAndExact() {
        assertEquals(
            LaunchPermissionStep.BATTERY,
            LaunchPermissions.nextStep(
                notificationsGranted = false,
                exactAlarmsGranted = false,
                batteryUnrestricted = false,
                sdkInt = 30,
            ),
        )
    }
}
