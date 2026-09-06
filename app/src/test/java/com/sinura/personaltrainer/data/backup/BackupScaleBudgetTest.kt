package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * R04: every export the app reports as saved must be one its own import accepts.
 *
 * The old contract was one 8 MiB ceiling on both sides, measured in characters on the
 * way out. A protected export is base64 ciphertext — a third larger than the document —
 * so a 7 MiB history produced a file the bounded import read refused as "far larger than
 * any Temper backup".
 */
class BackupScaleBudgetTest {
    @Test
    fun utf8LengthCountsWhatTheEncoderWrites() {
        listOf(
            "",
            "abc",
            "café",
            "€10",
            "😀 squat day",
            "Жим лёжа",
            "lone high \uD800 surrogate",
            "lone low \uDC00 surrogate",
            "😀🏋",
        ).forEach { text ->
            assertEquals(
                text,
                text.toByteArray(Charsets.UTF_8).size.toLong(),
                BackupScaleBudget.utf8Length(text),
            )
        }
    }

    @Test
    fun plaintextIsHeldToTheDocumentBudget() {
        assertNull(BackupScaleBudget.exportRefusal(BackupScaleBudget.DOCUMENT_BYTES_MAX, protected = false))
        assertEquals(
            BackupScaleBudget.TOO_BIG_TO_EXPORT,
            BackupScaleBudget.exportRefusal(BackupScaleBudget.DOCUMENT_BYTES_MAX + 1, protected = false),
        )
    }

    @Test
    fun envelopesAreHeldToTheRawFileBudget() {
        assertNull(BackupScaleBudget.exportRefusal(BackupScaleBudget.RAW_FILE_BYTES_MAX, protected = true))
        assertEquals(
            BackupScaleBudget.TOO_BIG_TO_EXPORT,
            BackupScaleBudget.exportRefusal(BackupScaleBudget.RAW_FILE_BYTES_MAX + 1, protected = true),
        )
        // The bounded reads ask for IMPORT_BYTES_MAX + 1; both names must mean one ceiling.
        assertEquals(BackupScaleBudget.RAW_FILE_BYTES_MAX, BackupScaleBudget.IMPORT_BYTES_MAX.toLong())
        assertEquals(BackupScaleBudget.DOCUMENT_BYTES_MAX, BackupScaleBudget.ENCODED_BYTES_MAX)
    }

    @Test
    fun theLargestAllowedDocumentStillImportsAfterProtection() {
        // Exactly DOCUMENT_BYTES_MAX bytes of two-byte characters: the case String.length
        // undercounts by half, and the largest thing exportJson will hand to wrap().
        val plaintext = buildString(BackupScaleBudget.DOCUMENT_BYTES_MAX.toInt() / 2) {
            repeat(BackupScaleBudget.DOCUMENT_BYTES_MAX.toInt() / 2) { append('é') }
        }
        assertEquals(BackupScaleBudget.DOCUMENT_BYTES_MAX, BackupScaleBudget.utf8Length(plaintext))
        BackupScaleBudget.requireExportable(plaintext, protected = false)
        BackupScaleBudget.requireDocumentFits(plaintext)

        val password = "correct-horse".toCharArray()
        val envelope = BackupEnvelope.wrap(plaintext = plaintext, password = password, iterations = 1)
        val envelopeBytes = BackupScaleBudget.utf8Length(envelope)

        assertTrue(
            "envelope $envelopeBytes bytes exceeds raw budget ${BackupScaleBudget.RAW_FILE_BYTES_MAX}",
            envelopeBytes <= BackupScaleBudget.RAW_FILE_BYTES_MAX,
        )
        assertTrue(
            "upper bound must bound: $envelopeBytes > ${BackupScaleBudget.envelopeBytesUpperBound(BackupScaleBudget.DOCUMENT_BYTES_MAX)}",
            envelopeBytes <= BackupScaleBudget.envelopeBytesUpperBound(BackupScaleBudget.DOCUMENT_BYTES_MAX),
        )
        BackupScaleBudget.requireExportable(envelope, protected = true)
        // And the round trip the contract promises: what import reads is what was written.
        assertEquals(plaintext, BackupEnvelope.open(envelope, password))
    }

    @Test
    fun oneByteOverTheDocumentBudgetIsRefusedBeforeAnythingIsWritten() {
        val overBudget = buildString(BackupScaleBudget.DOCUMENT_BYTES_MAX.toInt() + 1) {
            repeat(BackupScaleBudget.DOCUMENT_BYTES_MAX.toInt() + 1) { append('x') }
        }
        try {
            BackupScaleBudget.requireExportable(overBudget, protected = false)
            fail("a document over the budget must be refused")
        } catch (thrown: BackupException) {
            assertEquals(BackupScaleBudget.TOO_BIG_TO_EXPORT, thrown.message)
        }
        try {
            BackupScaleBudget.requireDocumentFits(overBudget)
            fail("import must refuse the same document")
        } catch (thrown: BackupException) {
            assertEquals(BackupScaleBudget.TOO_BIG_TO_IMPORT, thrown.message)
        }
    }

    @Test
    fun upperBoundHoldsForSmallEnvelopesToo() {
        val password = "correct-horse".toCharArray()
        listOf(0, 1, 2, 3, 100, 65_536, 1_000_003).forEach { size ->
            val plaintext = buildString(size) { repeat(size) { append('a') } }
            val envelope = BackupEnvelope.wrap(plaintext = plaintext, password = password, iterations = 1)
            assertTrue(
                "size $size",
                BackupScaleBudget.utf8Length(envelope) <= BackupScaleBudget.envelopeBytesUpperBound(size.toLong()),
            )
        }
    }
}
