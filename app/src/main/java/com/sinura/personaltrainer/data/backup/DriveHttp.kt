package com.sinura.personaltrainer.data.backup

import com.google.gson.JsonParser
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
        return message?.ifBlank { null } ?: "Google Drive request failed ($code)."
    }
}
