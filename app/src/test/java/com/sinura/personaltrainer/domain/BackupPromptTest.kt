package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPromptTest {
    @Test
    fun missingStampIsStale() {
        assertTrue(BackupPrompt.isStale(null, NOW))
    }

    @Test
    fun aBackupFromTodayIsFresh() {
        assertFalse(BackupPrompt.isStale(NOW, NOW))
        assertFalse(BackupPrompt.isStale(NOW - THIRTEEN_DAYS, NOW))
    }

    @Test
    fun fourteenDaysOrOlderIsStale() {
        assertTrue(BackupPrompt.isStale(NOW - BackupPrompt.STALE_AFTER_MS, NOW))
        assertTrue(BackupPrompt.isStale(NOW - FIFTEEN_DAYS, NOW))
    }

    @Test
    fun staleCaptionLeadsWithTheNag() {
        assertTrue(BackupPrompt.caption(true).startsWith("No backup in the last 14 days."))
        assertEquals(BackupPrompt.FRESH_CAPTION, BackupPrompt.caption(false))
    }

    private companion object {
        const val NOW = 1_777_000_000_000L
        const val THIRTEEN_DAYS = 13L * 24 * 60 * 60 * 1000
        const val FIFTEEN_DAYS = 15L * 24 * 60 * 60 * 1000
    }
}
