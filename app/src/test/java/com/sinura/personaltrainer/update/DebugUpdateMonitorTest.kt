package com.sinura.personaltrainer.update

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugUpdateMonitorTest {
    @Test
    fun dismissHidesTheBannerAndKeepsTheSettingsRow() = runTest {
        val http = object : DebugUpdateHttp {
            override fun get(url: String): String {
                if (url == GitHubDebugReleases.RELEASES_URL) {
                    return """
                        [{
                          "tag_name": "debug-live-2026-09-13-2",
                          "prerelease": true,
                          "draft": false,
                          "published_at": "2026-09-13T02:55:11Z",
                          "html_url": "https://example.test/rel",
                          "assets": [{
                            "name": "PersonalTrainer-1.0.0+debug.51-debug.apk",
                            "browser_download_url": "https://example.test/apk"
                          }]
                        }]
                    """.trimIndent()
                }
                throw IOException("unexpected $url")
            }
        }
        val cache = MemoryDebugUpdateCache()
        val monitor = DebugUpdateMonitor(
            checker = DebugUpdateChecker(
                http = http,
                cache = cache,
                enabled = true,
                installedVersionCode = 50,
                nowMillis = { 0L },
                isOnline = { true },
            ),
            cache = cache,
            scope = backgroundScope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        monitor.refresh(minIntervalMs = 0)
        assertEquals(51, monitor.ui.value.offer!!.versionCode)
        assertTrue(monitor.ui.value.showBanner)
        monitor.dismissBanner()
        assertNotNull(monitor.ui.value.offer)
        assertFalse(monitor.ui.value.showBanner)
    }
}
