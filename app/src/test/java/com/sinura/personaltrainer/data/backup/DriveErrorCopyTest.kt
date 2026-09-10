package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Drive refusal the owner cannot act on is a bug they have to report twice.
 *
 * Before this, any non-2xx that was not 401 or 403 and did not carry a well-shaped
 * `error.message` produced "Google Drive request failed (400)." and nothing else —
 * the body holding Google's actual reason was parsed, missed, and dropped. These are
 * the shapes Drive really answers with: its own JSON error, a proxy's HTML page, an
 * empty body, and a body too long to put on a phone screen.
 */
class DriveErrorCopyTest {
    @Test
    fun drivesOwnMessageIsQuoted() {
        val body = """{"error":{"code":400,"message":"The file identifier is invalid."}}"""

        assertEquals(
            "Google Drive: The file identifier is invalid.",
            DriveErrorCopy.message(400, body),
        )
    }

    @Test
    fun aBodyWithNoParseableMessageIsShownRatherThanDropped() {
        val body = """{"reason":"storageQuotaExceeded","detail":"out of room"}"""

        val message = DriveErrorCopy.message(400, body)

        assertTrue(message, message.startsWith("Google Drive request failed (400): "))
        assertTrue(message, message.contains("storageQuotaExceeded"))
    }

    @Test
    fun anHtmlErrorPageIsFlattenedToOneLine() {
        val body = "<html>\n  <title>502 Server Error</title>\n  <p>That's an error.</p>\n</html>"

        val message = DriveErrorCopy.message(502, body)

        assertEquals(
            "Google Drive request failed (502): " +
                "<html> <title>502 Server Error</title> <p>That's an error.</p> </html>",
            message,
        )
    }

    @Test
    fun anEmptyBodySaysSoInsteadOfShowingNothing() {
        assertEquals(
            "Google Drive request failed (404), and sent no explanation.",
            DriveErrorCopy.message(404, ""),
        )
    }

    @Test
    fun aWhitespaceOnlyBodyCountsAsEmpty() {
        assertEquals(
            "Google Drive request failed (500), and sent no explanation.",
            DriveErrorCopy.message(500, "  \n\t  "),
        )
    }

    @Test
    fun aLongBodyIsCappedSoTheDialogStaysReadable() {
        val body = "x".repeat(5_000)

        val message = DriveErrorCopy.message(400, body)

        assertEquals("Google Drive request failed (400): " + "x".repeat(DriveErrorCopy.REASON_MAX), message)
    }

    @Test
    fun aLongDriveMessageIsCappedTheSameWay() {
        val body = """{"error":{"message":"${"y".repeat(5_000)}"}}"""

        val message = DriveErrorCopy.message(400, body)

        assertEquals("Google Drive: " + "y".repeat(DriveErrorCopy.REASON_MAX), message)
    }

    @Test
    fun aBlankDriveMessageFallsThroughToTheBody() {
        val body = """{"error":{"message":"   ","status":"FAILED_PRECONDITION"}}"""

        val message = DriveErrorCopy.message(400, body)

        assertTrue(message, message.contains("FAILED_PRECONDITION"))
    }
}
