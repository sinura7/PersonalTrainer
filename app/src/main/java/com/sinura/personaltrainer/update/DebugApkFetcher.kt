package com.sinura.personaltrainer.update

import com.sinura.personaltrainer.logging.AppLog
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "PT/DebugUpdateApk"

/**
 * Downloads a Temper Debug APK. The seam that lets the install path run on a
 * plain JVM with a fake.
 */
fun interface DebugApkFetcher {
    suspend fun fetch(
        url: String,
        into: File,
        onProgress: (bytesRead: Long, contentLength: Long) -> Unit,
    )
}

internal class HttpUrlConnectionDebugApkFetcher : DebugApkFetcher {
    override suspend fun fetch(
        url: String,
        into: File,
        onProgress: (bytesRead: Long, contentLength: Long) -> Unit,
    ) {
        if (!GitHubDebugReleases.isAllowedApkUrl(url)) {
            throw IOException("APK URL was not a Temper Debug GitHub asset")
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", DEBUG_UPDATE_USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                AppLog.w(TAG, "GitHub APK GET $code")
                throw IOException("GitHub APK GET $code")
            }
            val length = connection.contentLengthLong
            connection.inputStream.use { input ->
                DebugApkDownload.writeTo(input, into, length, onProgress)
            }
            if (!DebugApkDownload.looksLikeApk(into)) {
                into.delete()
                throw IOException("Download was not an APK")
            }
        } finally {
            connection.disconnect()
        }
    }
}
