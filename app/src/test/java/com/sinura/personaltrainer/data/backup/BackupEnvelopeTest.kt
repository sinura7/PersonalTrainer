package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEnvelopeTest {
    @Test
    fun wrapThenUnwrapRecoversTheDocument() {
        val envelope = BackupEnvelope.wrap(PLAINTEXT, PASSWORD, iterations = TEST_ITERATIONS)
        assertTrue(BackupEnvelope.looksLike(envelope))
        assertTrue(envelope.contains("\"format\": \"temper-backup-envelope\""))
        assertTrue(envelope.contains("\"envelopeVersion\": 1"))
        assertFalse(envelope.contains("\"version\":"))
        assertFalse(envelope.contains(PLAINTEXT))
        assertEquals(PLAINTEXT, BackupEnvelope.unwrap(envelope, PASSWORD))
        assertEquals(PLAINTEXT, BackupEnvelope.open(envelope, PASSWORD))
    }

    @Test
    fun openLeavesLegacyPlaintextUnchanged() {
        assertFalse(BackupEnvelope.looksLike(PLAINTEXT))
        assertEquals(PLAINTEXT, BackupEnvelope.open(PLAINTEXT, password = null))
        assertEquals(PLAINTEXT, BackupEnvelope.open(PLAINTEXT, PASSWORD))
    }

    @Test
    fun eachWrapUsesAFreshSaltAndNonce() {
        val first = BackupEnvelope.wrap(PLAINTEXT, PASSWORD, iterations = TEST_ITERATIONS)
        val second = BackupEnvelope.wrap(PLAINTEXT, PASSWORD, iterations = TEST_ITERATIONS)
        assertNotEquals(first, second)
        assertEquals(PLAINTEXT, BackupEnvelope.unwrap(first, PASSWORD))
        assertEquals(PLAINTEXT, BackupEnvelope.unwrap(second, PASSWORD))
    }

    @Test
    fun wrongPasswordDoesNotDistinguishTamperFromGuess() {
        val envelope = BackupEnvelope.wrap(PLAINTEXT, PASSWORD, iterations = TEST_ITERATIONS)
        expectWrong(BackupEnvelope.WRONG_PASSWORD) {
            BackupEnvelope.unwrap(envelope, "wrong-password".toCharArray())
        }
        val tampered = envelope.replace("ciphertext", "ciphertext")
            .let { body ->
                val marker = "\"ciphertext\": \""
                val start = body.indexOf(marker) + marker.length
                val end = body.indexOf('"', start)
                body.replaceRange(start, end, "AAAAAAAA")
            }
        expectWrong(BackupEnvelope.WRONG_PASSWORD) { BackupEnvelope.unwrap(tampered, PASSWORD) }
    }

    @Test
    fun changingBoundParametersFailsClosed() {
        val envelope = BackupEnvelope.wrap(PLAINTEXT, PASSWORD, iterations = TEST_ITERATIONS)
        val retargeted = envelope.replace("\"iterations\": $TEST_ITERATIONS", "\"iterations\": 2000")
        expectWrong(BackupEnvelope.WRONG_PASSWORD) { BackupEnvelope.unwrap(retargeted, PASSWORD) }
    }

    @Test
    fun unknownMethodAndNewerEnvelopeAreNamed() {
        val envelope = BackupEnvelope.wrap(PLAINTEXT, PASSWORD, iterations = TEST_ITERATIONS)
        expectWrong(BackupEnvelope.UNKNOWN_METHOD) {
            BackupEnvelope.unwrap(
                envelope.replace("PBKDF2WithHmacSHA256", "scrypt"),
                PASSWORD,
            )
        }
        expectWrong("newer app version") {
            BackupEnvelope.unwrap(
                envelope.replace("\"envelopeVersion\": 1", "\"envelopeVersion\": 2"),
                PASSWORD,
            )
        }
    }

    @Test
    fun envelopeWithoutPasswordAsksRatherThanDecodingAsACatalog() {
        val envelope = BackupEnvelope.wrap(PLAINTEXT, PASSWORD, iterations = TEST_ITERATIONS)
        expectWrong(BackupEnvelope.NEED_PASSWORD) { BackupEnvelope.open(envelope, password = null) }
        try {
            BackupJson.decode(envelope)
            throw AssertionError("envelope must not decode as a backup document")
        } catch (error: BackupException) {
            assertTrue(error.message.orEmpty().contains("not a", ignoreCase = true))
        }
    }

    @Test
    fun newPasswordRules() {
        assertEquals(
            BackupEnvelope.PASSWORD_TOO_SHORT,
            BackupEnvelope.validateNewPassword("short", "short"),
        )
        assertEquals(
            BackupEnvelope.PASSWORD_MISMATCH,
            BackupEnvelope.validateNewPassword("long-enough", "long-enough-2"),
        )
        assertEquals(null, BackupEnvelope.validateNewPassword("long-enough", "long-enough"))
        expectWrong(BackupEnvelope.PASSWORD_TOO_SHORT) {
            BackupEnvelope.wrap(PLAINTEXT, "short".toCharArray(), iterations = TEST_ITERATIONS)
        }
    }

    private fun expectWrong(fragment: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected BackupException containing $fragment")
        } catch (error: BackupException) {
            assertTrue(
                "message was: ${error.message}",
                error.message.orEmpty().contains(fragment, ignoreCase = true),
            )
        }
    }

    private companion object {
        const val TEST_ITERATIONS = 1_000
        const val PLAINTEXT = """{"version":2,"app":"personal-trainer","sessions":[]}"""
        val PASSWORD = "correct-horse".toCharArray()
    }
}
