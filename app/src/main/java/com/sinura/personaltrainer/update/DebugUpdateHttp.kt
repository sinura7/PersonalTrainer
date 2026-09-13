package com.sinura.personaltrainer.update

import com.sinura.personaltrainer.data.backup.readAtMost
import com.sinura.personaltrainer.logging.AppLog
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "PT/DebugUpdateHttp"
private const val BODY_MAX_BYTES = 512 * 1024
private const val USER_AGENT = "Temper-Debug (com.sinura.personaltrainer.debug)"

/**
 * One GitHub GET. The seam that lets [DebugUpdateChecker] run on a plain JVM.
 */
fun interface DebugUpdateHttp {
    /** Throws on anything but a 2xx, including having no network. */
    fun get(url: String): String
}

class HttpUrlConnectionDebugUpdateHttp : DebugUpdateHttp {
    override fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
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
                AppLog.w(TAG, "GitHub GET $code")
                throw IOException("GitHub GET $code")
            }
            text
        } finally {
            connection.disconnect()
        }
    }
}
