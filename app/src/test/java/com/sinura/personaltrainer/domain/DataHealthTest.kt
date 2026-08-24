package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataHealthTest {
    @Test
    fun emptyListIsAvailableNotUnavailable() {
        val health = DataHealthFold.onValue(emptyList<String>())
        assertEquals(emptyList<String>(), health.presentValue())
        assertFalse(health.isUnavailable)
    }

    @Test
    fun firstFailureIsUnavailable() {
        val health = DataHealthFold.onFailure<List<String>>(last = null, what = "workout history")
        assertTrue(health is DataHealth.Unavailable)
        assertEquals("workout history", (health as DataHealth.Unavailable).what)
        assertNull(health.presentValue())
    }

    @Test
    fun laterFailureKeepsTheLastSuccessfulValue() {
        val last = listOf("squat")
        val health = DataHealthFold.onFailure(last = last, what = "workout history")
        assertTrue(health is DataHealth.Degraded)
        assertEquals(last, health.presentValue())
        assertFalse(health.isUnavailable)
    }

    @Test
    fun successfulNullThenFailureIsDegradedNotUnavailable() {
        val health = DataHealthFold.onFailure<String?>(last = null, what = "the in-progress session", seen = true)
        assertTrue(health is DataHealth.Degraded)
        assertNull(health.presentValue())
        assertFalse(health.isUnavailable)
    }

    @Test
    fun faultCopyNeverOffersContinueOrReset() {
        val lines = listOf(
            DataHealthCopy.HISTORY_TITLE,
            DataHealthCopy.HISTORY_BODY,
            DataHealthCopy.RETRY,
            DataHealthCopy.SETTINGS_TITLE,
            DataHealthCopy.SETTINGS_BODY,
            DataHealthCopy.START_UNAVAILABLE,
            DataHealthCopy.RESTORE_UNAVAILABLE,
        )
        lines.forEach { line ->
            assertFalse(line.contains("continue anyway", ignoreCase = true))
            assertFalse(line.contains("reset", ignoreCase = true))
        }
    }
}
