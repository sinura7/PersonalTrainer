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

    @Test
    fun anUploadNobodyReadBackIsNotABackupYet() {
        // The whole point of the verified stamp: a file that went up but would not come back
        // must nag, and must not claim there was no backup.
        val caption = BackupPrompt.caption(
            lastVerifiedAt = null,
            lastBackupAt = NOW,
            nowMs = NOW,
        )
        assertEquals(BackupPrompt.UNVERIFIED_CAPTION, caption)
        assertTrue(caption.contains("could not be read back"))
    }

    @Test
    fun averifiedBackupTodayReadsAsFresh() {
        assertEquals(
            BackupPrompt.FRESH_CAPTION,
            BackupPrompt.caption(lastVerifiedAt = NOW, lastBackupAt = NOW, nowMs = NOW),
        )
    }

    @Test
    fun nothingRecentAtAllStillSaysNoBackup() {
        assertEquals(
            BackupPrompt.STALE_CAPTION,
            BackupPrompt.caption(lastVerifiedAt = null, lastBackupAt = null, nowMs = NOW),
        )
        // An old written copy and an old verified copy are the same story: overdue.
        assertEquals(
            BackupPrompt.STALE_CAPTION,
            BackupPrompt.caption(
                lastVerifiedAt = NOW - FIFTEEN_DAYS,
                lastBackupAt = NOW - FIFTEEN_DAYS,
                nowMs = NOW,
            ),
        )
    }

    @Test
    fun staleIsMeasuredFromTheVerifiedStamp() {
        // Written five minutes ago, last proven readable three weeks ago: still overdue.
        assertTrue(BackupPrompt.isStale(NOW - FIFTEEN_DAYS, NOW))
    }
}
