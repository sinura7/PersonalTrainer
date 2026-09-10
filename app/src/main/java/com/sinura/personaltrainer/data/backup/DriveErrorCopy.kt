package com.sinura.personaltrainer.data.backup

import com.google.gson.JsonParser

/**
 * Every sentence the user is shown when Drive refuses a request.
 *
 * These used to be four string literals scattered through
 * [HttpUrlConnectionDriveHttp], where nothing could test them: the transport
 * needs a socket, so its copy was the one part of backup no JVM test reached.
 *
 * The reason it matters is [message]. A non-2xx that was not 401 or 403 became
 * the bare sentence "Google Drive request failed (400)." — true, useless, and
 * unactionable. Drive always says what it refused, in the response body; that
 * body was parsed for `error.message` and otherwise dropped on the floor. The
 * owner reads these on a phone with no adb attached, so the body *is* the
 * diagnosis and it belongs on screen.
 *
 * The body is an API diagnostic — Google's own words about its own refusal —
 * not the user's data, so showing it discloses nothing the user did not send.
 */
object DriveErrorCopy {
    /** Enough of Drive's refusal to name it, short enough to read on a phone. */
    const val REASON_MAX = 300

    const val EXPIRED = "Google sign-in expired. Sign in again."

    const val DENIED = "Drive access was denied."

    /**
     * The sentence for a non-2xx that is neither 401 nor 403.
     *
     * Preference order: Drive's own `error.message`, then whatever else the body
     * says, then the status code alone. [body] may be a JSON error, an HTML error
     * page from a proxy, or empty; all three have to read as one line in a dialog,
     * so runs of whitespace are collapsed and the result is capped at [REASON_MAX].
     */
    fun message(code: Int, body: String): String {
        parsedMessage(body)?.let { return "Google Drive: $it" }
        val reason = shorten(body)
        return if (reason == null) {
            "Google Drive request failed ($code), and sent no explanation."
        } else {
            "Google Drive request failed ($code): $reason"
        }
    }

    private fun parsedMessage(body: String): String? =
        try {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("error")
                ?.get("message")
                ?.asString
                ?.let(::shorten)
        } catch (_: Exception) {
            // Not JSON, not an object, or no string there. The body still gets shown
            // by the caller; it just does not get to claim it is Drive's own message.
            null
        }

    /** One line, capped, or null when there is nothing left to say. */
    private fun shorten(text: String): String? =
        text.replace(WHITESPACE, " ").trim().take(REASON_MAX).ifBlank { null }

    private val WHITESPACE = Regex("\\s+")
}
