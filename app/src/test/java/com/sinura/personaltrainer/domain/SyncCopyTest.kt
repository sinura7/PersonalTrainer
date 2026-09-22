package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCopyTest {
    @Test
    fun syncStatusLineShowsErrorAndPendingTogether() {
        val line = SyncCopy.syncStatusLine(
            pending = 2,
            lastSuccessAtMs = null,
            lastError = "Supabase upsert failed (503): busy",
        )
        assertTrue(line.contains("Could not reach Temper Account"))
        assertTrue(line.contains("2 changes waiting"))
    }

    @Test
    fun ownerFacingErrorSanitizesLongServerPayload() {
        val raw = "x".repeat(200)
        assertEquals(
            "Sync could not finish. Try again when you are online.",
            SyncCopy.ownerFacingError(raw),
        )
    }

    @Test
    fun scopeSaysLiveWorkoutsStayOnThePhoneExactlyWhileTheyDoNotSync() {
        // The day live-logged workouts join sync, this copy has to change with them.
        val tables = SyncEntityType.entries.map { it.remoteTable }.toSet()
        val liveWorkoutsSync = "workout_sessions" in tables || "set_logs" in tables
        assertEquals(!liveWorkoutsSync, SyncCopy.SCOPE.contains("stay on this phone"))
    }
}
