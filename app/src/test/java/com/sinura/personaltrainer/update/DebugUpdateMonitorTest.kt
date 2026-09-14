package com.sinura.personaltrainer.update

import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DebugUpdateMonitorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun dismissHidesTheBannerAndKeepsTheSettingsRow() = runTest {
        val cache = MemoryDebugUpdateCache()
        val monitor = DebugUpdateMonitor(
            checker = DebugUpdateChecker(
                http = DebugUpdateHttp { url ->
                    if (url == GitHubDebugReleases.RELEASES_URL) {
                        return@DebugUpdateHttp """
                            [{
                              "tag_name": "debug-live-2026-09-13-2",
                              "prerelease": true,
                              "draft": false,
                              "published_at": "2026-09-13T02:55:11Z",
                              "html_url": "https://github.com/sinura7/PersonalTrainer/releases/tag/debug-live-2026-09-13-2",
                              "assets": [{
                                "name": "PersonalTrainer-1.0.0+debug.51-debug.apk",
                                "browser_download_url": "https://github.com/sinura7/PersonalTrainer/releases/download/debug-live-2026-09-13-2/PersonalTrainer-1.0.0+debug.51-debug.apk"
                              }]
                            }]
                        """.trimIndent()
                    }
                    throw IOException("unexpected $url")
                },
                cache = cache,
                enabled = true,
                installedVersionCode = 50,
                nowMillis = { 0L },
                isOnline = { true },
            ),
            cache = cache,
            fetcher = DebugApkFetcher { _, _, _ -> error("dismiss must not download") },
            installer = object : DebugApkInstaller {
                override fun canInstall() = true
                override fun openInstallPermissionSettings() {}
                override fun install(apk: File) {}
                override fun stagingFile(versionCode: Int) = File(tmp.root, "x.apk")
            },
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
