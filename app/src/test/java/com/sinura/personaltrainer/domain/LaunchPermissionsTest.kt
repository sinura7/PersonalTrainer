package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPermissionsTest {
    @Test
    fun theWalkWaitsUntilSettingsIsLeftAfterTheChoice() {
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
        val missingAll = snapshot(
            sdkInt = 33,
            notificationsEnabled = false,
            restDoneChannelEnabled = true,
            postNotificationsGranted = false,
            canScheduleExactAlarms = false,
            batteryUnrestricted = false,
        )
        assertEquals(LaunchPermissionStep.NOTIFICATIONS, LaunchPermissions.nextStep(missingAll))

        val needExact = snapshot(
            sdkInt = 33,
            notificationsEnabled = true,
            restDoneChannelEnabled = true,
            postNotificationsGranted = true,
            canScheduleExactAlarms = false,
            batteryUnrestricted = false,
        )
        assertEquals(LaunchPermissionStep.EXACT_ALARM, LaunchPermissions.nextStep(needExact))

        val needBattery = snapshot(
            sdkInt = 33,
            notificationsEnabled = true,
            restDoneChannelEnabled = true,
            postNotificationsGranted = true,
            canScheduleExactAlarms = true,
            batteryUnrestricted = false,
        )
        assertEquals(LaunchPermissionStep.BATTERY, LaunchPermissions.nextStep(needBattery))

        val done = snapshot(
            sdkInt = 33,
            notificationsEnabled = true,
            restDoneChannelEnabled = true,
            postNotificationsGranted = true,
            canScheduleExactAlarms = true,
            batteryUnrestricted = true,
        )
        assertEquals(LaunchPermissionStep.DONE, LaunchPermissions.nextStep(done))
    }

    @Test
    fun olderSdksSkipNotificationsAndExact() {
        val needBatteryOnly = snapshot(
            sdkInt = 30,
            notificationsEnabled = false,
            restDoneChannelEnabled = false,
            postNotificationsGranted = false,
            canScheduleExactAlarms = false,
            batteryUnrestricted = false,
        )
        assertEquals(LaunchPermissionStep.BATTERY, LaunchPermissions.nextStep(needBatteryOnly))
    }

    private fun snapshot(
        sdkInt: Int,
        notificationsEnabled: Boolean,
        restDoneChannelEnabled: Boolean,
        postNotificationsGranted: Boolean,
        canScheduleExactAlarms: Boolean,
        batteryUnrestricted: Boolean,
    ): PhoneCapabilitySnapshot = PhoneCapabilitySnapshot(
        sdkInt = sdkInt,
        notificationsEnabled = notificationsEnabled,
        restDoneChannelEnabled = restDoneChannelEnabled,
        postNotificationsGranted = postNotificationsGranted,
        canScheduleExactAlarms = canScheduleExactAlarms,
        canUseFullScreenIntent = true,
        canDrawOverlays = true,
        batteryUnrestricted = batteryUnrestricted,
        hasVibrator = true,
    )
}
