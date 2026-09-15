package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftEntryReadinessTest {
    @Test
    fun onlyReadyOrDegradedMayCommit() {
        assertFalse(LiftEntryReadiness.NONE.allowsCommit())
        assertFalse(LiftEntryReadiness.RESOLVING.allowsCommit())
        assertTrue(LiftEntryReadiness.READY.allowsCommit())
        assertTrue(LiftEntryReadiness.DEGRADED.allowsCommit())
    }
}

class LogCommitCopyTest {
    @Test
    fun disabledReasonNamesBusyAndUnready() {
        assertEquals(
            LogCommitCopy.LOGGING_WAIT,
            LogCommitCopy.disabledReason(logging = true, liftReady = false),
        )
        assertEquals(
            LogCommitCopy.WAITING_FOR_LIFT,
            LogCommitCopy.disabledReason(logging = false, liftReady = false),
        )
        assertEquals(null, LogCommitCopy.disabledReason(logging = false, liftReady = true))
        assertEquals(
            "Could not save. Your set is still here. Try again.",
            LogCommitCopy.WRITE_FAILED,
        )
    }
}
