package com.sinura.personaltrainer.update

import android.content.Intent
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for Android's install sheet, an Intent. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DebugUpdateInstallTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun missingPermissionOpensThisAppSettingsAndDoesNotDownload() = runTest {
        val fetcher = RecordingFetcher()
        val installer = FakeInstaller(dir = tmp.root, canInstall = false)
        val monitor = monitor(fetcher, installer)
        monitor.refresh(minIntervalMs = 0)
        monitor.installNow()
        assertEquals(DebugUpdateInstall.NeedsPermission, monitor.ui.value.install)
        assertEquals(1, installer.settingsOpened)
        assertEquals(0, fetcher.starts)
        assertNull(installer.installed)
        assertFalse(fetcher.urls.any { it.contains("/releases/tag/") })
    }

    @Test
    fun downloadThenHandsTheFileToTheInstaller() = runTest {
        val fetcher = RecordingFetcher()
        val installer = FakeInstaller(dir = tmp.root, canInstall = true)
        val monitor = monitor(fetcher, installer)
        monitor.refresh(minIntervalMs = 0)
        monitor.installNow()
        assertEquals(1, fetcher.starts)
        assertEquals(
            "https://github.com/sinura7/PersonalTrainer/releases/download/debug-live-2026-09-13-2/PersonalTrainer-1.0.0+debug.51-debug.apk",
            fetcher.urls.single(),
        )
        assertNotNull(installer.installed)
        assertTrue(DebugApkDownload.looksLikeApk(installer.installed!!))
        assertEquals(FAKE_APK.toList(), installer.installed!!.readBytes().toList())
        assertEquals(DebugUpdateInstall.Idle, monitor.ui.value.install)
        assertEquals(0, installer.settingsOpened)
    }

    @Test
    fun downloadFailureIsFailOpenAndDoesNotInstall() = runTest {
        val fetcher = RecordingFetcher(error = IOException("offline"))
        val installer = FakeInstaller(dir = tmp.root, canInstall = true)
        val monitor = monitor(fetcher, installer)
        monitor.refresh(minIntervalMs = 0)
        monitor.installNow()
        assertEquals(DebugUpdateInstall.Failed, monitor.ui.value.install)
        assertNull(installer.installed)
        assertEquals(1, fetcher.starts)
    }

    @Test
    fun secondTapWhileDownloadingDoesNotHitTheFetcherTwice() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fetcher = RecordingFetcher(block = gate)
        val installer = FakeInstaller(dir = tmp.root, canInstall = true)
        val monitor = monitor(fetcher, installer)
        monitor.refresh(minIntervalMs = 0)
        launch { monitor.installNow() }
        advanceUntilIdle()
        launch { monitor.installNow() }
        advanceUntilIdle()
        assertEquals(1, fetcher.starts)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, fetcher.starts)
        assertNotNull(installer.installed)
    }

    @Test
    fun foregroundRetriesAfterPermissionIsGranted() = runTest {
        val fetcher = RecordingFetcher()
        val installer = FakeInstaller(dir = tmp.root, canInstall = false)
        val monitor = monitor(fetcher, installer)
        monitor.refresh(minIntervalMs = 0)
        monitor.installNow()
        assertEquals(1, installer.settingsOpened)
        installer.canInstall = true
        monitor.retryInstallIfPermissionGranted()
        assertNotNull(installer.installed)
        assertEquals(1, installer.settingsOpened)
    }

    @Test
    fun foregroundDoesNotReopenSettingsWhenStillDenied() = runTest {
        val fetcher = RecordingFetcher()
        val installer = FakeInstaller(dir = tmp.root, canInstall = false)
        val monitor = monitor(fetcher, installer)
        monitor.refresh(minIntervalMs = 0)
        monitor.installNow()
        monitor.retryInstallIfPermissionGranted()
        assertEquals(1, installer.settingsOpened)
        assertEquals(0, fetcher.starts)
        assertEquals(DebugUpdateInstall.NeedsPermission, monitor.ui.value.install)
    }

    /** Audit RM-1: Android refused the build. The banner says the update did not finish. */
    @Test
    fun anInstallAndroidRefusesShowsFailed() = runTest {
        val installer = FakeInstaller(dir = tmp.root, canInstall = true)
        val monitor = monitor(RecordingFetcher(), installer)
        monitor.refresh(minIntervalMs = 0)
        monitor.installNow()
        assertEquals(DebugUpdateInstall.Idle, monitor.ui.value.install)

        monitor.onInstallAnswer(DebugInstallAnswer.Failed(status = 7, message = "INSTALL_FAILED_UPDATE_INCOMPATIBLE"))
        runCurrent()
        assertEquals(DebugUpdateInstall.Failed, monitor.ui.value.install)
        assertEquals("Try again downloads afresh, so nothing is kept", 1, installer.cleared)
    }

    /** The owner cancelled Android's sheet: the update is still there to tap. */
    @Test
    fun aCancelledSheetLeavesTheUpdateToTap() = runTest {
        val installer = FakeInstaller(dir = tmp.root, canInstall = true)
        val monitor = monitor(RecordingFetcher(), installer)
        monitor.refresh(minIntervalMs = 0)
        monitor.installNow()

        monitor.onInstallAnswer(DebugInstallAnswer.Cancelled)
        runCurrent()
        assertEquals(DebugUpdateInstall.Idle, monitor.ui.value.install)
        assertNotNull(monitor.ui.value.offer)
        assertEquals(1, installer.cleared)
    }

    /**
     * Audit RM-1: Android answers in the background, where the app may not open a screen. Its
     * sheet waits for the main screen, which opens it once.
     */
    @Test
    fun androidsSheetWaitsForTheMainScreen() = runTest {
        val monitor = monitor(RecordingFetcher(), FakeInstaller(dir = tmp.root, canInstall = true))
        val sheet = Intent("android.content.pm.action.CONFIRM_INSTALL")

        monitor.onInstallAnswer(DebugInstallAnswer.Confirm(sheet))
        assertTrue(monitor.installSheet.value === sheet)

        monitor.onInstallSheetShown(opened = true)
        assertNull(monitor.installSheet.value)
        assertEquals(DebugUpdateInstall.Idle, monitor.ui.value.install)
    }

    @Test
    fun aSheetTheMainScreenCannotOpenShowsFailedAndLeavesNoDownload() = runTest {
        val installer = FakeInstaller(dir = tmp.root, canInstall = true)
        val monitor = monitor(RecordingFetcher(), installer)
        monitor.onInstallAnswer(DebugInstallAnswer.Confirm(Intent("android.content.pm.action.CONFIRM_INSTALL")))

        monitor.onInstallSheetShown(opened = false)
        assertNull(monitor.installSheet.value)
        assertEquals(DebugUpdateInstall.Failed, monitor.ui.value.install)
        // Try again downloads afresh: the kept file would serve nothing.
        runCurrent()
        assertEquals(1, installer.cleared)
    }

    /**
     * An answer for an earlier session, arriving during a new download, must not delete the file
     * still being downloaded: the clear waits until Android has its copy.
     */
    @Test
    fun aClearWaitsForTheDownloadRunningNow() = runTest {
        val gate = CompletableDeferred<Unit>()
        val installer = FakeInstaller(dir = tmp.root, canInstall = true)
        val monitor = monitor(RecordingFetcher(block = gate), installer)
        monitor.refresh(minIntervalMs = 0)
        launch { monitor.installNow() }
        advanceUntilIdle()

        monitor.onInstallAnswer(DebugInstallAnswer.Installed)
        runCurrent()
        assertEquals("cleared during a download", 0, installer.cleared)

        gate.complete(Unit)
        advanceUntilIdle()
        runCurrent()
        assertEquals(listOf("install", "clear"), installer.events)
    }

    /** Audit RM-6: an install Android reports done leaves no download behind. */
    @Test
    fun anInstallThatWentThroughClearsTheDownloads() = runTest {
        val installer = FakeInstaller(dir = tmp.root, canInstall = true)
        val monitor = monitor(RecordingFetcher(), installer)

        monitor.onInstallAnswer(DebugInstallAnswer.Installed)
        // The clear runs on the monitor's scope (the test's background scope).
        runCurrent()
        assertEquals(1, installer.cleared)
    }

    /**
     * Audit RM-6: a self-update replaces the app before Android can answer it, so the new build
     * finds no newer one and clears what the old one downloaded.
     */
    @Test
    fun noNewerBuildClearsTheDownloads() = runTest {
        val installer = FakeInstaller(dir = tmp.root, canInstall = true)
        val monitor = monitor(RecordingFetcher(), installer, installedVersionCode = 51)

        monitor.refresh(minIntervalMs = 0)
        assertNull(monitor.ui.value.offer)
        assertEquals(1, installer.cleared)
    }

    @Test
    fun gymFloorInstallIsANoOp() = runTest {
        DisabledDebugUpdate.install()
        assertNull(DisabledDebugUpdate.ui.value.offer)
        assertEquals(DebugUpdateInstall.Idle, DisabledDebugUpdate.ui.value.install)
    }

    private fun TestScope.monitor(
        fetcher: RecordingFetcher,
        installer: FakeInstaller,
        installedVersionCode: Int = 50,
    ): DebugUpdateMonitor {
        val cache = MemoryDebugUpdateCache()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return DebugUpdateMonitor(
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
                installedVersionCode = installedVersionCode,
                nowMillis = { 0L },
                isOnline = { true },
            ),
            cache = cache,
            fetcher = fetcher,
            installer = installer,
            scope = backgroundScope,
            ioDispatcher = dispatcher,
        )
    }

    private class RecordingFetcher(
        private val error: Throwable? = null,
        private val block: CompletableDeferred<Unit>? = null,
    ) : DebugApkFetcher {
        val urls = mutableListOf<String>()
        var starts = 0

        override suspend fun fetch(
            url: String,
            into: File,
            onProgress: (Long, Long) -> Unit,
        ) {
            starts++
            urls += url
            block?.await()
            error?.let { throw it }
            into.parentFile?.mkdirs()
            into.writeBytes(FAKE_APK)
            onProgress(FAKE_APK.size.toLong(), FAKE_APK.size.toLong())
        }
    }

    private class FakeInstaller(
        private val dir: File,
        var canInstall: Boolean,
    ) : DebugApkInstaller {
        var settingsOpened = 0
        var installed: File? = null

        override fun canInstall(): Boolean = canInstall

        override fun openInstallPermissionSettings() {
            settingsOpened++
        }

        val events = mutableListOf<String>()

        override fun install(apk: File) {
            installed = apk
            events += "install"
        }

        override fun stagingFile(versionCode: Int): File =
            File(dir, "PersonalTrainer-$versionCode-debug.apk")

        var cleared = 0

        override fun clearStaging() {
            cleared++
            events += "clear"
        }
    }

    companion object {
        private val FAKE_APK = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(32) { 7 }
    }
}
