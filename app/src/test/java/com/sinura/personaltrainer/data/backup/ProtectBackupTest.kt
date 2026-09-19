package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectBackupTest {
    @Test
    fun protectThenOpenRecoversTheDocument() {
        val envelope = ProtectBackup()(PLAINTEXT, PASSWORD, iterations = TEST_ITERATIONS)
        assertTrue(BackupEnvelope.looksLike(envelope))
        assertFalse(envelope.contains(PLAINTEXT))
        assertEquals(PLAINTEXT, OpenBackup()(envelope, PASSWORD))
    }

    @Test
    fun openLeavesLegacyPlaintextUnchanged() {
        assertEquals(PLAINTEXT, OpenBackup()(PLAINTEXT, password = null))
        assertEquals(PLAINTEXT, OpenBackup()(PLAINTEXT, PASSWORD))
    }

    private companion object {
        const val PLAINTEXT = """{"version":3,"app":"temper"}"""
        val PASSWORD = "correct-horse".toCharArray()
        const val TEST_ITERATIONS = 1_000
    }
}
