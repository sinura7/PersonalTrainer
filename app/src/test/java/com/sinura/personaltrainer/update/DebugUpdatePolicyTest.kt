package com.sinura.personaltrainer.update

import com.sinura.personaltrainer.domain.DebugUpdateCopy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugUpdatePolicyTest {
    @Test
    fun gymFloorWiresADisabledPortAndDebugChecksGitHub() {
        val container = read("app/src/main/java/com/sinura/personaltrainer/AppContainer.kt")
        assertTrue(container.contains("if (BuildConfig.DEBUG)"))
        assertTrue(container.contains("DisabledDebugUpdate"))
        assertTrue(container.contains("DebugUpdateMonitor"))
        assertTrue(container.contains("HttpUrlConnectionDebugApkFetcher"))
        assertTrue(container.contains("AndroidDebugApkInstaller"))
        val activity = read("app/src/main/java/com/sinura/personaltrainer/MainActivity.kt")
        assertTrue(activity.contains("debugUpdate?.onForeground()"))
        val settings = read("app/src/main/java/com/sinura/personaltrainer/ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("BuildConfig.DEBUG && notice.showBanner"))
        assertTrue(settings.contains("debugUpdate::install"))
        assertFalse(settings.contains("openOffer"))
        val home = read("app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt")
        assertTrue(home.contains("DebugUpdateBanner"))
        assertTrue(home.contains("debugUpdate::install"))
        assertFalse(home.contains("Get started"))
        assertFalse(home.contains("openOffer"))
    }

    @Test
    fun installPermissionIsDebugOnlyAndCopyDoesNotAskToBypassSafety() {
        val mainManifest = read("app/src/main/AndroidManifest.xml")
        assertFalse(mainManifest.contains("REQUEST_INSTALL_PACKAGES"))
        val debugManifest = read("app/src/debug/AndroidManifest.xml")
        assertTrue(debugManifest.contains("REQUEST_INSTALL_PACKAGES"))
        assertTrue(debugManifest.contains("FileProvider"))
        assertTrue(debugManifest.contains("\${applicationId}.debugupdate"))
        val monitor = read("app/src/main/java/com/sinura/personaltrainer/update/DebugUpdateMonitor.kt")
        assertFalse(monitor.contains("ACTION_VIEW"))
        assertFalse(monitor.contains("releaseUrl"))
        assertFalse(DebugUpdateCopy.TITLE.contains("Play Protect"))
        assertFalse(DebugUpdateCopy.BANNER_BODY.contains("Play Protect"))
        assertFalse(DebugUpdateCopy.BANNER_BODY.contains("unknown apps"))
        assertFalse(DebugUpdateCopy.NEEDS_PERMISSION.contains("unknown apps"))
        assertFalse(DebugUpdateCopy.SETTINGS_SUMMARY.contains("unknown apps"))
        assertFalse(DebugUpdateCopy.BANNER_BODY.contains("Obtainium"))
        assertFalse(DebugUpdateCopy.TITLE.contains("Obtainium"))
        assertTrue(DebugUpdateCopy.BANNER_BODY.contains("Android will ask you to install"))
        assertEquals("Update Temper Debug", DebugUpdateCopy.TITLE)
        assertEquals("Update", DebugUpdateCopy.ACTION)
        assertEquals("Live 51 is ready", DebugUpdateCopy.settingsSummary(51))
        assertEquals("Downloading the update… 40%", DebugUpdateCopy.downloading(40))
    }

    private fun read(relative: String): String = file(relative).readText()

    private fun file(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File(relative.removePrefix("app/")),
        )
        return candidates.first { it.exists() }
    }
}
