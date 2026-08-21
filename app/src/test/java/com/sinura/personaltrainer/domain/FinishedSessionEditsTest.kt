package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishedSessionEditsTest {
    private val started = 1_700_000_000_000L
    private val finished = started + 3_600_000

    @Test
    fun addedSetLandsAfterLastSet() {
        val last = started + 600_000
        assertEquals(
            last + 1,
            FinishedSessionEdits.timestampForAddedSet(started, finished, last),
        )
    }

    @Test
    fun addedSetNeverExceedsFinishedAt() {
        val stamp = FinishedSessionEdits.timestampForAddedSet(started, finished, finished)
        assertEquals(finished, stamp)
        assertTrue(stamp <= finished)
    }

    @Test
    fun addedSetFallsBackToStartedAtWhenNoSets() {
        assertEquals(
            started + 1,
            FinishedSessionEdits.timestampForAddedSet(started, finished, null),
        )
    }

    @Test
    fun addedSetTotalOnDegenerateWindow() {
        // finishedAt before startedAt should not throw, and must not land before the start.
        val stamp = FinishedSessionEdits.timestampForAddedSet(started, started - 5_000, null)
        assertEquals(started, stamp)
    }
}
