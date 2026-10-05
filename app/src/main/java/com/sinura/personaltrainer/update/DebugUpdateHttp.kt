package com.sinura.personaltrainer.update

import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.data.backup.readAtMost
import com.sinura.personaltrainer.logging.AppLog
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.URL
import javax.net.ssl.SSLException

private const val TAG = "PT/DebugUpdateHttp"
private const val BODY_MAX_BYTES = 512 * 1024
private const val MAX_ATTEMPTS = 3

/**
 * One GitHub GET with retries on transient network failures (common on mobile
 * when talking to api.github.com). The seam that lets [DebugUpdateChecker] run
 * on a plain JVM.
 */
fun interface DebugUpdateHttp {
    /** Throws on anything but a 2xx, including having no network. */
    fun get(url: String): String
}

class HttpUrlConnectionDebugUpdateHttp : DebugUpdateHttp {
    override fun get(url: String): String {
        var last: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return getOnce(url)
            } catch (error: IOException) {
                last = error
                if (!error.shouldRetry() || attempt == MAX_ATTEMPTS - 1) throw error
                val backoffMs = 750L * (attempt + 1)
                AppLog.w(TAG, "GitHub GET retry ${attempt + 2}/$MAX_ATTEMPTS after ${error.message}")
                Thread.sleep(backoffMs)
            }
        }
        throw last ?: IOException("GitHub GET failed")
    }

    private fun getOnce(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", DEBUG_UPDATE_USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            val token = BuildConfig.GITHUB_API_TOKEN.trim()
            if (token.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { input ->
                val bytes = input.readAtMost(BODY_MAX_BYTES + 1)
                if (bytes.size > BODY_MAX_BYTES) {
                    throw IOException("GitHub reply was larger than $BODY_MAX_BYTES")
                }
                bytes.toString(Charsets.UTF_8)
            }.orEmpty()
            if (code !in 200..299) {
                AppLog.w(TAG, "GitHub GET $code for $url")
                throw IOException("GitHub GET $code")
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun IOException.shouldRetry(): Boolean = when (this) {
        is SocketException -> true
        is SSLException -> true
        else -> message?.contains("ECONNRESET", ignoreCase = true) == true ||
            message?.contains("connection abort", ignoreCase = true) == true ||
            message?.contains("Software caused connection abort", ignoreCase = true) == true
    }
}
