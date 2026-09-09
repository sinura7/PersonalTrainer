package com.sinura.personaltrainer.data.backup

import com.google.gson.JsonParser
import com.sinura.personaltrainer.logging.AppLog
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * One authenticated Drive request, answered with its 2xx body.
 *
 * The seam that lets [DriveRestClient] run on a plain JVM. Its paging logic is
 * a sequence of requests whose URLs carry the state, so a fake that serves
 * canned pages and records every URL exercises all of it without a socket.
 * Production wires [HttpUrlConnectionDriveHttp].
 */
private const val TAG = "PT/DriveHttp"

/** Enough of Drive's refusal to name it, short enough to read on a phone. */
private const val REASON_MAX = 300

fun interface DriveHttp {
    /** Throws [BackupException] for anything but a 2xx, including having no network. */
    fun call(
        accessToken: String,
        url: String,
        method: String,
        contentType: String?,
        body: String?,
    ): String
}

/**
 * The shipping transport. Everything Settings relies on lives here: the bounded
 * read that refuses a planted multi-hundred-MB file, and the 401 / 403 / other
 * error copy the user sees.
 */
class HttpUrlConnectionDriveHttp : DriveHttp {
    override fun call(
        accessToken: String,
        url: String,
        method: String,
        contentType: String?,
        body: String?,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (contentType != null) {
                setRequestProperty("Content-Type", contentType)
            }
            if (body != null) {
                doOutput = true
                outputStream.use { stream ->
                    stream.write(body.toByteArray(Charsets.UTF_8))
                }
            }
        }
        return try {
            val code = connection.responseCode
            // Bounded: the Drive folder is writable by anything holding the account,
            // and an unbounded readText of a planted multi-hundred-MB file OOM-kills
            // the app. No genuine backup or API reply approaches the budget.
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.use { stream ->
                    val bytes = stream.readAtMost(BackupScaleBudget.IMPORT_BYTES_MAX + 1)
                    if (bytes.size > BackupScaleBudget.IMPORT_BYTES_MAX) {
                        throw BackupException(BackupScaleBudget.TOO_BIG_TO_IMPORT)
                    }
                    bytes.toString(Charsets.UTF_8)
                }
                .orEmpty()
            if (code == 401) {
                throw BackupException("Google sign-in expired. Sign in again.")
            }
            if (code == 403) {
                throw BackupException("Drive access was denied.")
            }
            if (code !in 200..299) {
                // The body is Google's own explanation of what it refused. It used to be
                // parsed for error.message and otherwise dropped, which left a 400 with an
                // unexpected body shape as the bare sentence "Google Drive request failed
                // (400)" — true, useless, and unactionable. It is an API diagnostic, not
                // user data, so it is safe to show and to log.
                AppLog.e(TAG, "Drive $method $url -> $code: $text")
                throw BackupException(driveErrorMessage(code, text))
            }
            text
        } catch (error: BackupException) {
            throw error
        } catch (error: IOException) {
            throw BackupException("Connect to the internet to use Google Drive.")
        } finally {
            connection.disconnect()
        }
    }

    private fun driveErrorMessage(code: Int, body: String): String {
        val message = try {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("error")
                ?.get("message")
                ?.asString
        } catch (_: Exception) {
            null
        }
        message?.ifBlank { null }?.let { return "Google Drive: $it" }
        // No parseable message. Show what Drive actually sent rather than only the number:
        // on a phone with no adb to hand, the response body IS the diagnosis.
        val reason = body.trim().take(REASON_MAX).ifBlank { null }
        return if (reason == null) {
            "Google Drive request failed ($code), and sent no explanation."
        } else {
            "Google Drive request failed ($code): $reason"
        }
    }
}
