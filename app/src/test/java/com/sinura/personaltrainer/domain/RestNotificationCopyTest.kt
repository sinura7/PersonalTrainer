package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestNotificationCopyTest {
    @Test
    fun sentenceNamesTheShadeAndThePocket() {
        assertTrue(RestNotificationCopy.SENTENCE.contains("shade"))
        assertTrue(RestNotificationCopy.SENTENCE.contains("pocket"))
    }

    @Test
    fun sentenceIsNotASettingsDeepLink() {
        val blob = listOf(
            RestNotificationCopy.TITLE,
            RestNotificationCopy.SENTENCE,
            RestNotificationCopy.CONTINUE,
            RestNotificationCopy.NOT_NOW,
        ).joinToString(" ")
        assertFalse(blob.contains("Settings", ignoreCase = true))
        assertFalse(blob.contains("Turn on", ignoreCase = true))
    }
}
