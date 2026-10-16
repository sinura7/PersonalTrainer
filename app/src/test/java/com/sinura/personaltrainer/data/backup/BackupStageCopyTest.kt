package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupStageCopyTest {
    @Test
    fun eachStopHasItsOwnHeading() {
        val titles = listOf(
            BackupStageCopy.titleFor(BackupEnvelope.WRONG_PASSWORD),
            BackupStageCopy.titleFor(
                "This backup was made with a newer app version and can’t be opened here.",
            ),
            BackupStageCopy.titleFor(BackupScaleBudget.TOO_BIG_TO_IMPORT),
            BackupStageCopy.titleFor(BackupScaleBudget.TOO_BIG_TO_EXPORT),
            BackupStageCopy.titleFor("Connect to the internet to use Google Drive."),
            BackupStageCopy.titleFor(RestoreJournal.SETTINGS_PENDING),
        )
        assertEquals(BackupStageCopy.WRONG_PASSWORD, titles[0])
        assertEquals(BackupStageCopy.NEWER_BACKUP, titles[1])
        assertEquals(BackupStageCopy.TOO_BIG, titles[2])
        assertEquals(BackupStageCopy.TOO_BIG, titles[3])
        assertEquals(BackupStageCopy.OFFLINE, titles[4])
        assertEquals(BackupStageCopy.SETTINGS_STILL_OWED, titles[5])
        assertEquals(
            setOf(
                BackupStageCopy.WRONG_PASSWORD,
                BackupStageCopy.NEWER_BACKUP,
                BackupStageCopy.TOO_BIG,
                BackupStageCopy.OFFLINE,
                BackupStageCopy.SETTINGS_STILL_OWED,
            ),
            titles.toSet(),
        )
        assertEquals(BackupStageCopy.GENERIC, BackupStageCopy.titleFor("Something else broke."))
    }

    @Test
    fun aCompletedRestoreIsTheLineAfterTheCommit() {
        assertTrue(BackupStageCopy.isCompletedRestore("Restored Monday — 2 sessions."))
        assertFalse(BackupStageCopy.isCompletedRestore(RestoreJournal.SETTINGS_PENDING))
        assertFalse(BackupStageCopy.isCompletedRestore(BackupEnvelope.WRONG_PASSWORD))
    }

    @Test
    fun aRestoreThatStillOwesSettingsIsNotHeadedAsFinished() {
        val done = BackupStageCopy.restoreStatus(
            sourceName = "Monday",
            summary = "2 sessions",
            preferencesRestored = true,
            settingsPending = false,
        )
        val owed = BackupStageCopy.restoreStatus(
            sourceName = "Monday",
            summary = "2 sessions",
            preferencesRestored = false,
            settingsPending = true,
        )
        assertTrue(BackupStageCopy.isCompletedRestore(done))
        assertFalse(owed.startsWith("Restored "))
        assertFalse(BackupStageCopy.isCompletedRestore(owed))
        assertEquals(BackupStageCopy.SETTINGS_STILL_OWED, BackupStageCopy.titleFor(owed))
    }

    @Test
    fun aSecondClaimHasNothingLeftToCommit() {
        val first = RestoreCommitGate.claim("plan-1")
        assertEquals(null, first.slot)
        assertEquals("plan-1", first.taken)
        val second = RestoreCommitGate.claim(first.slot)
        assertEquals(null, second.taken)
    }

    @Test
    fun liveSessionAndPartialSettingsAreTheirOwnLines() {
        assertTrue(BackupStageCopy.LIVE_SESSION.contains("live session"))
        assertTrue(BackupStageCopy.PARTIAL_SETTINGS.contains("Settings still need"))
        assertFalse(BackupStageCopy.LIVE_SESSION == BackupStageCopy.PARTIAL_SETTINGS)
    }
}
