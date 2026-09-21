package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncChildRowTest {
    @Test
    fun remoteBlockedWhileLocallyQueued() {
        assertFalse(SyncChildRow.remoteAppliesWhenNotLocallyQueued(hasLocalPendingUpload = true))
        assertTrue(SyncChildRow.remoteAppliesWhenNotLocallyQueued(hasLocalPendingUpload = false))
    }
}
