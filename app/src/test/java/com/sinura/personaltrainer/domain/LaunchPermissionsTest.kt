package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPermissionsTest {
    @Test
    fun theWalkWaitsUntilSettingsIsLeftAfterTheChoice() {
        // Choosing Account or Drive opens Settings in the same moment; the dialogs used to
        // land over the sign-in form.
        assertFalse(
            LaunchPermissions.walkMayShow(
                postureChosen = true,
                onTabAwayFromSettings = true,
                settingsPageOpening = true,
                alreadyShowing = false,
            ),
        )
        assertFalse(
            LaunchPermissions.walkMayShow(
                postureChosen = true,
                onTabAwayFromSettings = false,
                settingsPageOpening = false,
                alreadyShowing = false,
            ),
        )
        assertTrue(
            LaunchPermissions.walkMayShow(
                postureChosen = true,
                onTabAwayFromSettings = true,
                settingsPageOpening = false,
                alreadyShowing = false,
            ),
        )
    }

    @Test
    fun theWalkNeverStartsBeforeTheChoice() {
        assertFalse(
            LaunchPermissions.walkMayShow(
                postureChosen = false,
                onTabAwayFromSettings = true,
                settingsPageOpening = false,
                alreadyShowing = false,
            ),
        )
    }

    @Test
    fun aWalkAlreadyShowingSurvivesNavigatingAway() {
        assertTrue(
            LaunchPermissions.walkMayShow(
                postureChosen = true,
                onTabAwayFromSettings = false,
                settingsPageOpening = true,
                alreadyShowing = true,
            ),
        )
    }

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
