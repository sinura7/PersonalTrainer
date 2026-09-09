package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The table behind an unattended copy. Every refusal here is one the owner would rather
 * have than the alternative: no plaintext upload, no second copy of one workout.
 */
class AutoBackupPolicyTest {

    @Test
    fun aFinishedSessionOnAnArmedPhoneIsBackedUp() {
        assertTrue(
            AutoBackupPolicy.shouldBackUp(
                enabled = true,
                hasStoredPassphrase = true,
                lastBackedUpSessionId = "earlier",
                sessionId = "today",
            ),
        )
    }

    @Test
    fun theToggleIsTheSwitch() {
        assertFalse(
            AutoBackupPolicy.shouldBackUp(
                enabled = false,
                hasStoredPassphrase = true,
                lastBackedUpSessionId = null,
                sessionId = "today",
            ),
        )
    }

    @Test
    fun noPassphraseMeansNoCopyRatherThanAPlaintextOne() {
        // ADR-009 §9: plaintext is a warned advanced choice. An unattended path must never
        // reach for it, so a missing secret refuses instead of falling back.
        assertFalse(
            AutoBackupPolicy.shouldBackUp(
                enabled = true,
                hasStoredPassphrase = false,
                lastBackedUpSessionId = null,
                sessionId = "today",
            ),
        )
    }

    @Test
    fun theSameSessionIsNeverBackedUpTwice() {
        // The summary screen is rebuilt with the same session after process death; without
        // this the restore would upload a second copy of one workout.
        assertFalse(
            AutoBackupPolicy.shouldBackUp(
                enabled = true,
                hasStoredPassphrase = true,
                lastBackedUpSessionId = "today",
                sessionId = "today",
            ),
        )
    }

    @Test
    fun theFirstEverAutomaticCopyHasNothingToCompareAgainst() {
        assertTrue(
            AutoBackupPolicy.shouldBackUp(
                enabled = true,
                hasStoredPassphrase = true,
                lastBackedUpSessionId = null,
                sessionId = "today",
            ),
        )
    }

    @Test
    fun aMissingSessionIdIsNotASession() {
        // savedStateHandle.get<String>("sessionId").orEmpty() is the source; an empty id
        // means the route arrived without one, and there is nothing to be idempotent about.
        assertFalse(
            AutoBackupPolicy.shouldBackUp(
                enabled = true,
                hasStoredPassphrase = true,
                lastBackedUpSessionId = null,
                sessionId = "",
            ),
        )
        assertFalse(
            AutoBackupPolicy.shouldBackUp(
                enabled = true,
                hasStoredPassphrase = true,
                lastBackedUpSessionId = null,
                sessionId = "   ",
            ),
        )
    }

    @Test
    fun theCaptionsSayWhereTheWorkoutIs() {
        // A failed copy must never read as a lost workout.
        assertTrue(AutoBackupPolicy.FAILED.contains("saved on this phone"))
        assertTrue(AutoBackupPolicy.NEEDS_SIGN_IN.contains("Settings"))
        assertEquals("Backed up to Drive.", AutoBackupPolicy.DONE)
    }
}
