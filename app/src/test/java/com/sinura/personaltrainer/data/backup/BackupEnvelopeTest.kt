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
    fun aForgedIterationCountAboveTheCeilingIsRefusedBeforeDeriving() {
        // The count is read before anything can be authenticated; without the
        // ceiling a forged file demanding two billion iterations pinned a core
        // for hours on the first password attempt.
        val envelope = BackupEnvelope.wrap(PLAINTEXT, PASSWORD, iterations = TEST_ITERATIONS)
        val forged = envelope.replace(
            "\"iterations\": $TEST_ITERATIONS",
            "\"iterations\": 2000000000",
        )
        expectWrong(BackupEnvelope.UNKNOWN_METHOD) { BackupEnvelope.unwrap(forged, PASSWORD) }
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

    @Test
    fun aCommittedV1FixtureStillOpens() {
        // The regression guard the format did not have. Every other test here wraps and
        // unwraps with the same build, so the whole suite would stay green while a change to
        // the header, the AAD string or the KDF parameters quietly orphaned every backup file
        // already on the owner's phone and in their Drive.
        //
        // This envelope was generated OUTSIDE this class, from the documented format, with a
        // fixed salt and nonce so it is a constant rather than a fresh random file. It must
        // decrypt byte for byte, forever, on every future build that still claims to read v1.
        assertEquals(PLAINTEXT, BackupEnvelope.unwrap(V1_FIXTURE, PASSWORD))
        assertEquals(PLAINTEXT, BackupEnvelope.open(V1_FIXTURE, PASSWORD))
    }

    @Test
    fun theTagIsBoundToTheEnvelopeVersionItIsGiven() {
        // The mechanism behind the fixture, asserted where it cannot drift. The tag covers the
        // envelope version, so unwrap has to compute it from the version IN THE FILE. Reading
        // it from the ENVELOPE_VERSION constant works only until that constant moves — and the
        // day it moves to 2, every v1 file fails its AEAD check and the owner is told "that
        // password doesn't open this file" about a backup that is perfectly intact.
        assertNotEquals(
            String(BackupEnvelope.aad(1, TEST_ITERATIONS)),
            String(BackupEnvelope.aad(2, TEST_ITERATIONS)),
        )
        assertTrue(String(BackupEnvelope.aad(1, TEST_ITERATIONS)).contains("|1|"))
        assertTrue(String(BackupEnvelope.aad(2, TEST_ITERATIONS)).contains("|2|"))
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

        /**
         * A real v1 envelope over [PLAINTEXT] under [PASSWORD], at [TEST_ITERATIONS].
         *
         * Salt and nonce are fixed (01..10 and 40..4b) so this is a committed artefact rather
         * than something regenerated on each run. Do not "refresh" it: the whole value of the
         * fixture is that it predates whatever change is being made to the format.
         */
        const val V1_FIXTURE = """{
  "format": "temper-backup-envelope",
  "envelopeVersion": 1,
  "app": "personal-trainer",
  "kdf": "PBKDF2WithHmacSHA256",
  "iterations": 1000,
  "keyBytes": 32,
  "cipher": "AES/GCM/NoPadding",
  "salt": "AQIDBAUGBwgJCgsMDQ4PEA",
  "nonce": "QEFCQ0RFRkdISUpL",
  "ciphertext": "+gM8qcUAA0q1DOvb5sfBu2NKi5V1So2TzqMP9ts1p2b/x0F8VyTc/OkikSuhffM1LS9jS88UM+CwRATnEGJaKJawZ8w"
}
"""
    }
}
