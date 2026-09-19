package com.sinura.personaltrainer.update

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Bounded copy of a GitHub APK onto disk. The HTTP client is a seam; this is
 * the part the JVM tests can exercise without a socket.
 */
internal object DebugApkDownload {
    const val MAX_BYTES = 96L * 1024L * 1024L
    private const val BUFFER_BYTES = 64 * 1024

    fun writeTo(
        input: InputStream,
        into: File,
        contentLength: Long,
        onProgress: (bytesRead: Long, contentLength: Long) -> Unit,
    ) {
        if (contentLength > MAX_BYTES) {
            throw IOException("APK was larger than $MAX_BYTES")
        }
        into.parentFile?.mkdirs()
        val tmp = File(into.parentFile, "${into.name}.part")
        tmp.delete()
        into.delete()
        try {
            tmp.outputStream().use { out ->
                val buf = ByteArray(BUFFER_BYTES)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    read += n
                    if (read > MAX_BYTES) {
                        throw IOException("APK was larger than $MAX_BYTES")
                    }
                    out.write(buf, 0, n)
                    onProgress(read, contentLength)
                }
            }
            if (!tmp.renameTo(into)) {
                tmp.copyTo(into, overwrite = true)
                tmp.delete()
            }
        } catch (error: Throwable) {
            tmp.delete()
            into.delete()
            throw error
        }
    }

    fun looksLikeApk(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        file.inputStream().use { input ->
            return input.read() == 'P'.code && input.read() == 'K'.code
        }
    }

    fun percent(bytesRead: Long, contentLength: Long): Int? {
        if (contentLength <= 0L) return null
        return ((bytesRead * 100L) / contentLength).toInt().coerceIn(0, 100)
    }
}
