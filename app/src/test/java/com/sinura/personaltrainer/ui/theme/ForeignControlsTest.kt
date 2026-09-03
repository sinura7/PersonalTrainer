package com.sinura.personaltrainer.ui.theme

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignControlsTest {
    @Test
    fun outlineSolidClearsNonTextContrastOnReadingSurfaces() {
        for (surface in ContrastPolicy.readingSurfaces) {
            val ratio = ContrastPolicy.ratio(OutlineSolid, surface)
            assertTrue(
                "OutlineSolid on $surface is $ratio, want ≥ ${ContrastPolicy.NON_TEXT}",
                ContrastPolicy.meetsNonText(OutlineSolid, surface),
            )
        }
        val retired = Color(0xFF39434A)
        assertFalse(
            "old 1.75:1 border must not sneak back",
            ContrastPolicy.meetsNonText(retired, Surface2),
        )
    }

    @Test
    fun surfaceContainerIsTheSheetNotTheWindow() {
        val theme = readOwned("ui/theme/Theme.kt")
        assertTrue(theme.contains("surfaceContainer = Surface3"))
        assertFalse(theme.contains("surfaceContainer = Pit"))
        assertTrue(theme.contains("DropdownMenu"))
        assertFalse(theme.contains("navigation bar sits on it"))
    }

    @Test
    fun threeSwitchesFiveMenusZeroSnackbarHosts() {
        val switchSites = listOf(
            "ui/settings/SettingsScreen.kt",
            "ui/reminders/ReminderPrefsSection.kt",
        )
        switchSites.forEach { path ->
            val src = readOwned(path)
            assertTrue(path, src.contains("InstrumentSwitch("))
            assertFalse(path, src.contains("material3.Switch"))
        }
        val menuSites = listOf(
            "ui/history/SessionDetailScreen.kt",
            "ui/workout/ActiveWorkoutScreen.kt",
            "ui/settings/SettingsScreen.kt",
            "ui/components/GymSurfaces.kt",
            "ui/navigation/LiveSessionBar.kt",
        )
        menuSites.forEach { path ->
            assertTrue(path, readOwned(path).contains("InstrumentMenu("))
        }
        val snackbarHosts = listOf(
            "ui/workout/ActiveWorkoutScreen.kt",
            "ui/history/SessionDetailScreen.kt",
            "ui/history/HistoryScreen.kt",
        )
        snackbarHosts.forEach { path ->
            val src = readOwned(path)
            assertFalse(path, src.contains("SnackbarHost("))
            assertTrue(
                path,
                src.contains("GymStatusBanner(") || src.contains("GymErrorBanner("),
            )
        }
        assertEquals(2, switchSites.size)
        assertEquals(5, menuSites.size)
        assertEquals(3, snackbarHosts.size)
        assertTrue(readOwned("ui/components/InstrumentSwitch.kt").contains("fun InstrumentSwitch("))
        assertTrue(readOwned("ui/components/InstrumentMenu.kt").contains("fun InstrumentMenu("))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
