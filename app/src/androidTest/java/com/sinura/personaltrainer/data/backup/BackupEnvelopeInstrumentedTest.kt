package com.sinura.personaltrainer.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device proof that the portable envelope uses the platform cipher, not a
 * host-only construction. Same wrap/unwrap contract as the JVM suite.
 */
@RunWith(AndroidJUnit4::class)
class BackupEnvelopeInstrumentedTest {
    @Test
    fun deviceAesGcmRoundTrip() {
        val password = "correct-horse".toCharArray()
        val envelope = BackupEnvelope.wrap(PLAINTEXT, password, iterations = 1_000)
        assertTrue(BackupEnvelope.looksLike(envelope))
        assertEquals(PLAINTEXT, BackupEnvelope.unwrap(envelope, password))
    }

    private companion object {
        const val PLAINTEXT = """{"version":2,"app":"personal-trainer","sessions":[]}"""
    }
}
