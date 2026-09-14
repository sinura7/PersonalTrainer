package com.sinura.personaltrainer.update

import com.sinura.personaltrainer.update.DebugApkDownload.writeTo
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DebugApkDownloadTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun copyReportsProgressAndLooksLikeAnApk() {
        val dest = tmp.newFile("out.apk")
        dest.delete()
        val payload = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(100) { 1 }
        var lastRead = 0L
        var lastTotal = -1L
        writeTo(
            input = ByteArrayInputStream(payload),
            into = dest,
            contentLength = payload.size.toLong(),
            onProgress = { read, total ->
                lastRead = read
                lastTotal = total
            },
        )
        assertEquals(payload.size.toLong(), lastRead)
        assertEquals(payload.size.toLong(), lastTotal)
        assertEquals(payload.size.toLong(), dest.length())
        assertTrue(DebugApkDownload.looksLikeApk(dest))
        assertEquals(100, DebugApkDownload.percent(payload.size.toLong(), payload.size.toLong()))
        assertEquals(null, DebugApkDownload.percent(10, 0))
    }

    @Test
    fun oversizeAndNonApkFailOpen() {
        val dest = tmp.newFile("big.apk")
        dest.delete()
        try {
            writeTo(
                input = ByteArrayInputStream(ByteArray(16)),
                into = dest,
                contentLength = DebugApkDownload.MAX_BYTES + 1,
                onProgress = { _, _ -> },
            )
            error("expected oversize to throw")
        } catch (_: IOException) {
        }
        assertFalse(dest.exists())

        val html = tmp.newFile("page.apk")
        html.writeText("<html>not an apk</html>")
        assertFalse(DebugApkDownload.looksLikeApk(html))
        assertFalse(DebugApkDownload.looksLikeApk(tmp.newFile("empty.apk")))
    }
}
