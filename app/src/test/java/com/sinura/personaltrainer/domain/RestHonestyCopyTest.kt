package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestHonestyCopyTest {
    @Test
    fun persistenceBeatsNotificationAndFirstRest() {
        val row = RestHonestyCopy.pick(
            persistenceHealthy = false,
            restRunning = true,
            notificationsEnabled = false,
            batteryHint = true,
            exactBestEffort = true,
            onRestPage = true,
        )
        assertEquals(RestHonestyCopy.Kind.PERSISTENCE, row?.kind)
        assertEquals(RestHonestyCopy.PERSISTENCE, row?.sentence)
        assertFalse(row!!.sentence.contains("precise", ignoreCase = true))
    }

    @Test
    fun notificationRecoveryWhenAlertsAreOff() {
        val row = RestHonestyCopy.pick(
            persistenceHealthy = true,
            restRunning = false,
            notificationsEnabled = false,
            batteryHint = false,
            exactBestEffort = false,
            onRestPage = false,
        )
        assertEquals(RestHonestyCopy.Kind.NOTIFICATION, row?.kind)
        assertEquals(RestNotificationCopy.RECOVERY_TITLE, row?.sentence)
        assertEquals(RestNotificationCopy.RECOVERY_ACTION, row?.action)
    }

    @Test
    fun exactDeniedCopyNeverSaysPrecise() {
        val row = RestHonestyCopy.pick(
            persistenceHealthy = true,
            restRunning = false,
            notificationsEnabled = true,
            batteryHint = false,
            exactBestEffort = true,
            onRestPage = true,
        )
        assertEquals(RestHonestyCopy.Kind.EXACT, row?.kind)
        assertEquals(RestHonestyCopy.EXACT_DENIED, row?.sentence)
        assertFalse(RestHonestyCopy.EXACT_DENIED.contains("precise", ignoreCase = true))
        assertNull(
            RestHonestyCopy.pick(
                persistenceHealthy = true,
                restRunning = true,
                notificationsEnabled = true,
                batteryHint = false,
                exactBestEffort = true,
                onRestPage = false,
            ),
        )
    }

    @Test
    fun firstRestNamesBatteryAndSettings() {
        val row = RestHonestyCopy.pick(
            persistenceHealthy = true,
            restRunning = true,
            notificationsEnabled = true,
            batteryHint = true,
            exactBestEffort = false,
            onRestPage = false,
        )
        assertEquals(RestHonestyCopy.Kind.FIRST_REST, row?.kind)
        assertTrue(row!!.sentence.contains("unrestricted battery", ignoreCase = true))
        assertTrue(row.sentence.contains("Settings"))
        assertEquals(RestBatteryCopy.GOT_IT, row.action)
    }
}
