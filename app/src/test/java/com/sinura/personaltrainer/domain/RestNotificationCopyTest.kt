package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestNotificationCopyTest {
    @Test
    fun explanationAndRecoveryStayHonestAndCompact() {
        val all = listOf(
            RestNotificationCopy.TITLE,
            RestNotificationCopy.SENTENCE,
            RestNotificationCopy.CONTINUE,
            RestNotificationCopy.NOT_NOW,
            RestNotificationCopy.RECOVERY_TITLE,
            RestNotificationCopy.RECOVERY_ACTION,
        )
        all.forEach { line ->
            assertFalse(line.contains("reliable", ignoreCase = true))
            assertFalse(line.contains("Notifications are blocked", ignoreCase = true))
        }
        assertTrue(RestNotificationCopy.RECOVERY_TITLE.length <= 24)
        assertTrue(RestNotificationCopy.RECOVERY_ACTION.length <= 12)
    }
}
