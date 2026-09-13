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
        val activity = read("app/src/main/java/com/sinura/personaltrainer/MainActivity.kt")
        assertTrue(activity.contains("debugUpdate?.onForeground()"))
        val settings = read("app/src/main/java/com/sinura/personaltrainer/ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("BuildConfig.DEBUG && notice.showBanner"))
        val home = read("app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt")
        assertTrue(home.contains("DebugUpdateBanner"))
        assertFalse(home.contains("Get started"))
    }

    @Test
    fun thePromptDoesNotAskToInstallPackagesOrDisableSafety() {
        val manifest = read("app/src/main/AndroidManifest.xml")
        assertFalse(manifest.contains("REQUEST_INSTALL_PACKAGES"))
        val sources = listOf(
            "app/src/main/java/com/sinura/personaltrainer/update",
            "app/src/main/java/com/sinura/personaltrainer/ui/update",
        ).flatMap { dir ->
            file(dir).walkTopDown().filter { it.isFile }.map { it.readText() }
        }
        assertTrue(sources.none { it.contains("REQUEST_INSTALL_PACKAGES") })
        assertFalse(DebugUpdateCopy.TITLE.contains("Play Protect"))
        assertFalse(DebugUpdateCopy.BANNER_BODY.contains("Play Protect"))
        assertFalse(DebugUpdateCopy.BANNER_BODY.contains("unknown apps"))
        assertFalse(DebugUpdateCopy.SETTINGS_SUMMARY.contains("unknown apps"))
        assertTrue(DebugUpdateCopy.BANNER_BODY.contains("Android will still ask you to allow the install"))
        assertEquals("Update available", DebugUpdateCopy.TITLE)
        assertEquals("Live 51 is ready", DebugUpdateCopy.settingsSummary(51))
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
