package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRevisionTest {
    @Test
    fun higherRevisionWins() {
        val local = SyncEntityVersion(revision = 2, updatedAtMs = 100)
        val remote = SyncEntityVersion(revision = 3, updatedAtMs = 50)
        assertTrue(SyncRevision.remoteWins(local, remote))
    }

    @Test
    fun equalRevisionUsesUpdatedAtMs() {
        val local = SyncEntityVersion(revision = 4, updatedAtMs = 200)
        val remote = SyncEntityVersion(revision = 4, updatedAtMs = 201)
        assertTrue(SyncRevision.remoteWins(local, remote))
        assertFalse(SyncRevision.remoteWins(remote, local))
    }
}
