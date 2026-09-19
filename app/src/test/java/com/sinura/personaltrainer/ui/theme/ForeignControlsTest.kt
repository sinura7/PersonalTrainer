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
    fun twoSwitchesSevenMenusZeroSnackbarHosts() {
        val switchSites = listOf(
            "ui/settings/RestTimerPrefsSection.kt",
            "ui/reminders/ReminderPrefsSection.kt",
        )
        switchSites.forEach { path ->
            val src = readOwned(path)
            assertTrue(path, src.contains("InstrumentSwitch("))
            assertFalse(path, src.contains("material3.Switch"))
        }
        // The workout floor's three menus — the header ⋮, a saved-set chip, and the
        // full saved-sets sheet — all go through InstrumentMenu, never Material's own.
        val menuSites = listOf(
            "ui/history/SessionDetailScreen.kt",
            "ui/workout/WorkoutOverflowMenu.kt",
            "ui/workout/SetHistoryStrip.kt",
            "ui/workout/WorkoutSavedSets.kt",
            "ui/settings/BackupRestoreSection.kt",
            "ui/components/GymSurfaces.kt",
            "ui/navigation/LiveSessionBar.kt",
        )
        val materialMenu = Regex("material3\\.DropdownMenu\\b")
        menuSites.forEach { path ->
            val src = readOwned(path)
            assertTrue(path, src.contains("InstrumentMenu("))
            assertFalse(path, materialMenu.containsMatchIn(src))
        }
        // Undo on the floor lives in the dock's companion slot while the dock is up, and in
        // the screen's own host when it is not; neither is a Snackbar.
        val snackbarHosts = listOf(
            "ui/workout/ActiveWorkoutScreen.kt",
            "ui/workout/WorkoutDock.kt",
            "ui/history/SessionDetailScreen.kt",
            "ui/history/HistoryScreen.kt",
        )
        snackbarHosts.forEach { path ->
            val src = readOwned(path)
            assertFalse(path, src.contains("SnackbarHost("))
            assertTrue(
                path,
                src.contains("GymStatusBanner(") ||
                    src.contains("GymErrorBanner(") ||
                    src.contains("GymUndoHost("),
            )
        }
        assertTrue(readOwned("ui/workout/WorkoutDock.kt").contains("GymUndoHost("))
        assertEquals(2, switchSites.size)
        assertEquals(7, menuSites.size)
        assertEquals(4, snackbarHosts.size)
        assertTrue(readOwned("ui/components/InstrumentSwitch.kt").contains("fun InstrumentSwitch("))
        val menu = readOwned("ui/components/InstrumentMenu.kt")
        assertTrue(menu.contains("fun InstrumentMenu("))
        assertTrue(menu.contains("tonalElevation = 0.dp"))
        assertTrue(menu.contains("shadowElevation = 0.dp"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
